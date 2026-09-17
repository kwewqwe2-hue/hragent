package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.CertificateLanguage;
import com.hragent.hragentv1.domain.EmploymentCertificateTemplate;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.dto.WebChatDtos;
import com.hragent.hragentv1.web.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class WebChatGatewayService {
    private static final Logger log = LoggerFactory.getLogger(WebChatGatewayService.class);
    private static final long MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024;
    private static final String AGENT_UNAVAILABLE_REPLY = "本次处理没有得到有效结果";
    private static final List<String> ALLOWED_ATTACHMENT_EXTENSIONS =
            List.of("jpg", "jpeg", "png", "pdf", "docx", "txt");

    private final WebChatIdentityService identityService;
    private final AssistantService assistantService;
    private final EmployeeSelfServiceAssistant selfService;
    private final EmployeeRelationsService relations;
    private final EmployeeAgentRouter employeeRouter;
    private final HandbookKnowledgeService handbook;
    private final DeepSeekClient directModel;
    private final boolean workflowEnabled;
    private final EmploymentCertificateTemplateService templateService;
    private final URI n8nWebhookUri;
    private final URI n8nAttachmentParseUri;
    private final String attachmentInternalKey;
    private final String callbackBaseUrl;
    private final int timeoutSeconds;
    private final RestClient restClient;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PendingReply> pendingReplies = new ConcurrentHashMap<>();
    private final PolicyConversationGuide conversationGuide = new PolicyConversationGuide();
    @org.springframework.beans.factory.annotation.Autowired
    private PolicyCopilotService policyCopilot;

    public WebChatGatewayService(
            WebChatIdentityService identityService,
            AssistantService assistantService,
            EmployeeSelfServiceAssistant selfService,
            EmployeeRelationsService relations,
            EmployeeAgentRouter employeeRouter,
            HandbookKnowledgeService handbook,
            DeepSeekClient directModel,
            @Value("${app.web-chat.workflow-enabled:true}") boolean workflowEnabled,
            EmploymentCertificateTemplateService templateService,
            @Value("${app.web-chat.n8n-webhook-url}") String n8nWebhookUrl,
            @Value("${app.web-chat.n8n-attachment-parse-url}") String n8nAttachmentParseUrl,
            @Value("${app.web-chat.attachment-internal-key}") String attachmentInternalKey,
            @Value("${app.web-chat.callback-base-url}") String callbackBaseUrl,
            @Value("${app.web-chat.timeout-seconds:120}") int timeoutSeconds
    ) {
        this.identityService = identityService;
        this.assistantService = assistantService;
        this.selfService = selfService;
        this.relations = relations;
        this.employeeRouter = employeeRouter;
        this.handbook = handbook;
        this.directModel = directModel;
        this.workflowEnabled = workflowEnabled;
        this.templateService = templateService;
        this.n8nWebhookUri = URI.create(n8nWebhookUrl);
        this.n8nAttachmentParseUri = URI.create(n8nAttachmentParseUrl);
        this.attachmentInternalKey = attachmentInternalKey;
        this.callbackBaseUrl = callbackBaseUrl.replaceAll("/+$", "");
        this.timeoutSeconds = Math.max(15, timeoutSeconds);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public WebChatDtos.MessageResponse chat(UserAccount user, String rawMessage) {
        return chat(user, rawMessage, null);
    }
    private final ServiceConversationGuide serviceConversations = new ServiceConversationGuide();
    public void clearPolicyConversation(UserAccount user,String cid){conversationGuide.clear(user,cid);}
    public void clearServiceConversation(UserAccount user,String cid){serviceConversations.clear(user,cid);}
    public String resolveServiceFollowup(UserAccount user,String message,String cid){return serviceConversations.command(user,message,cid).orElse(message);}
    public Optional<WebChatDtos.MessageResponse> serviceGuidance(UserAccount user,String message,String cid){return serviceConversations.guidance(user,message,cid);}
    public void rememberService(UserAccount user,String message,String cid,WebChatDtos.MessageResponse response){serviceConversations.observe(user,message,cid,response);}
    public Optional<WebChatDtos.MessageResponse> policyConversation(UserAccount user,String message,String cid){
        if(policyCopilot!=null && message!=null && message.contains("年金")) {
            var benefit=policyCopilot.benefitAnswer(user,message);
            if(benefit.isPresent()) {
                var safety=relations.triage(user,message);
                if(safety.isPresent())return Optional.of(new WebChatDtos.MessageResponse(safety.get(),"er-human-support",UUID.randomUUID().toString()));
                conversationGuide.clear(user,cid);
                return Optional.of(new WebChatDtos.MessageResponse(benefit.get().answer(),"company-benefit-guidance",UUID.randomUUID().toString()));
            }
        }
        if(!conversationGuide.interviewRelevant(user,message,cid))return Optional.empty();
        var safety=relations.triage(user,message);
        if(safety.isPresent())return Optional.of(new WebChatDtos.MessageResponse(safety.get(),"er-human-support",UUID.randomUUID().toString()));
        return conversationGuide.opening(user,message,cid);
    }

    public Map<String, Object> status(UserAccount user) {
        return Map.of("businessServicesAvailable", true, "directModelConfigured", directModel.isConfigured(user.getTenantId()),
                "workflowEnabled", workflowEnabled);
    }

    public WebChatDtos.MessageResponse chat(UserAccount user, String rawMessage, String conversationId) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        String requestId = UUID.randomUUID().toString();
        var opening = conversationGuide.opening(user, message, conversationId);
        if(opening.isPresent())return opening.get();
        message = conversationGuide.resolve(user, message, conversationId);
        var routed = employeeRouter.reply(user, message, conversationId);
        if (routed.handled()) return conversationGuide.present(user, message, conversationId, routed, requestId);
        if (!workflowEnabled) {
            if (directModel.isConfigured(user.getTenantId())) return fallbackReply(user, message, requestId, "direct model mode");
            return EmployeeIntentUnderstanding.nextStep(message);
        }
        String callbackToken = randomToken();
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingReplies.put(requestId, new PendingReply(callbackToken, future));

        try {
            String callbackUrl = callbackBaseUrl + "/internal/web-chat/callback/" + requestId
                    + "?token=" + callbackToken;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("msgtype", "text");
            payload.put("text", Map.of("content", message));
            payload.put("senderId", identityService.issue(user));
            payload.put("senderStaffId", "web-" + user.getEmployeeNo());
            payload.put("conversationType", "1");
            payload.put("conversationId", "web-" + user.getTenantId() + "-" + user.getId()
                    + (conversationId == null || !conversationId.matches("[A-Za-z0-9_-]{1,80}") ? "" : "-" + conversationId));
            payload.put("sessionWebhook", callbackUrl);
            payload.put("channel", "web");

            restClient.post()
                    .uri(n8nWebhookUri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            String answer = future.get(timeoutSeconds, TimeUnit.SECONDS);
            if (isAgentUnavailableReply(answer)) {
                return fallbackReply(user, message, requestId, "n8n returned no usable result");
            }
            return new WebChatDtos.MessageResponse(answer, "n8n / DeepSeek", requestId);
        } catch (TimeoutException exception) {
            return fallbackReply(user, message, requestId, "n8n response timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return fallbackReply(user, message, requestId, "n8n request interrupted");
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Web chat gateway failed requestId={}", requestId, exception);
            return fallbackReply(user, message, requestId, "n8n gateway request failed");
        } finally {
            pendingReplies.remove(requestId);
        }
    }

    private WebChatDtos.MessageResponse fallbackReply(
            UserAccount user,
            String message,
            String requestId,
            String reason
    ) {
        log.warn("Web chat is using the local fallback requestId={} reason={}", requestId, reason);
        try {
            var fallback = assistantService.chat(user, message);
            return new WebChatDtos.MessageResponse(fallback.answer(), fallback.provider(), requestId);
        } catch (Exception exception) {
            log.error("Web chat fallback failed requestId={}", requestId, exception);
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "智能体服务暂时不可用，请稍后重试。");
        }
    }

    private boolean isAgentUnavailableReply(String answer) {
        return answer != null && answer.contains(AGENT_UNAVAILABLE_REPLY);
    }

    public WebChatDtos.MessageResponse chatWithAttachment(
            UserAccount user,
            MultipartFile file,
            String rawInstruction
    ) {
        var safety = relations.triage(user, rawInstruction == null ? "附件咨询" : rawInstruction);
        if (safety.isPresent()) return new WebChatDtos.MessageResponse(safety.get() + "\n本次附件尚未解析；请通过工单说明必要的证据线索。", "er-human-support", UUID.randomUUID().toString());
        ValidatedAttachment attachment = validateAttachment(file);
        Map<String, Object> parsed = parseAttachment(file, attachment);
        String extractedText = stringValue(parsed.get("extractedText"));
        String parseError = stringValue(parsed.get("error"));

        if (!parseError.isBlank()) {
            throw AppException.badRequest(parseError);
        }
        if (extractedText.isBlank()) {
            throw AppException.badRequest("文件中没有提取到可读文字，请检查文件内容或清晰度。");
        }

        String instruction = rawInstruction == null ? "" : rawInstruction.trim();
        if (instruction.length() > 1000) {
            throw AppException.badRequest("附件说明不能超过 1000 个字符");
        }
        if (instruction.isBlank()) {
            instruction = "请提取并整理这个文件中的关键信息。";
        }

        TemplateContext templateContext = createTemplateContext(user, file, attachment, instruction);
        String templateContextText = templateContext == null
                ? ""
                : """
                        【系统上下文：该 DOCX 已作为员工提交的证明模板保存】
                        模板 ID：%d
                        模板文件名：%s
                        模板语言初步识别：%s
                        如果用户要按此模板申请签证或出境在职证明，收集完整的目的国家、领事馆或受理机构、语言、用途和薪资选项；用户明确确认后，调用证明申请工具，并把 requestedTemplateId 设为 %d。不要把它导入知识库，也不要声称文件已经生成。
                        """.formatted(
                        templateContext.id(),
                        templateContext.fileName(),
                        templateContext.language().name(),
                        templateContext.id()
                );

        String prompt = """
                【系统已完成网页附件解析，请勿再次调用文件解析工具】
                文件名：%s
                用户要求：%s
                %s
                解析内容：
                %s

                请仅依据以上文件内容和用户要求，用简体中文回答；不得编造文件中没有的信息。
                """.formatted(attachment.fileName(), instruction, templateContextText, extractedText);
        return chat(user, prompt);
    }

    private TemplateContext createTemplateContext(
            UserAccount user,
            MultipartFile file,
            ValidatedAttachment attachment,
            String instruction
    ) {
        if (!"docx".equals(attachment.extension()) || !looksLikeCertificateTemplate(instruction, attachment.fileName())) {
            return null;
        }
        CertificateLanguage language = inferTemplateLanguage(instruction);
        EmploymentCertificateTemplate template = templateService.uploadProposal(
                user,
                file,
                attachment.fileName(),
                "待员工补充",
                "待员工补充",
                language
        );
        return new TemplateContext(template.getId(), template.getSourceFileName(), language);
    }

    private boolean looksLikeCertificateTemplate(String instruction, String fileName) {
        String value = (instruction + " " + fileName).toLowerCase(Locale.ROOT);
        return value.contains("模板")
                || value.contains("template")
                || value.contains("签证")
                || value.contains("在职证明")
                || value.contains("按此格式")
                || value.contains("按照这个格式");
    }

    private CertificateLanguage inferTemplateLanguage(String instruction) {
        String value = instruction.toLowerCase(Locale.ROOT);
        if (value.contains("中英") || value.contains("双语") || value.contains("bilingual")) {
            return CertificateLanguage.BILINGUAL;
        }
        if (value.contains("英文") || value.contains("english")) {
            return CertificateLanguage.ENGLISH;
        }
        return CertificateLanguage.CHINESE;
    }

    private Map<String, Object> parseAttachment(MultipartFile file, ValidatedAttachment attachment) {
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return attachment.fileName();
                }
            };
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("data", resource)
                    .filename(attachment.fileName())
                    .contentType(attachment.mediaType());

            Map<String, Object> response = restClient.post()
                    .uri(n8nAttachmentParseUri)
                    .header("X-HRAgent-Internal-Key", attachmentInternalKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(Map.class);
            return response == null ? Map.of() : response;
        } catch (IOException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "读取上传文件失败，请重新选择文件。");
        } catch (RestClientException exception) {
            log.error("Web attachment parser failed fileName={}", attachment.fileName(), exception);
            if (List.of("jpg", "jpeg", "png").contains(attachment.extension())) {
                throw new AppException(HttpStatus.BAD_GATEWAY, "图片识别失败，请确认 OCR 和 n8n 正在运行。");
            }
            throw new AppException(HttpStatus.BAD_GATEWAY, "文件解析失败，请确认 n8n 文件解析工作流正在运行。");
        }
    }

    private ValidatedAttachment validateAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("请选择要发送的文件");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw AppException.badRequest("文件不能超过 10 MB");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        String extension = extensionOf(fileName);
        if (!ALLOWED_ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw AppException.badRequest("支持 JPG、JPEG、PNG、PDF、DOCX、TXT 文件");
        }
        return new ValidatedAttachment(fileName, extension, mediaTypeOf(extension));
    }

    private String safeFileName(String originalName) {
        String candidate = originalName == null ? "" : originalName.trim();
        candidate = candidate.replace('\\', '/');
        candidate = candidate.substring(candidate.lastIndexOf('/') + 1).replace("..", "_");
        if (candidate.isBlank() || candidate.length() > 180) {
            throw AppException.badRequest("文件名无效或过长");
        }
        return candidate;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private MediaType mediaTypeOf(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "txt" -> MediaType.TEXT_PLAIN;
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            );
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public void receive(String requestId, String token, WebChatDtos.AgentCallback callback) {
        PendingReply pending = pendingReplies.get(requestId);
        if (pending == null || !constantTimeEquals(pending.token(), token)) {
            throw AppException.forbidden("网页聊天回调无效或已过期");
        }
        String content = callback == null || callback.text() == null ? null : callback.text().content();
        if (content == null || content.isBlank()) {
            throw AppException.badRequest("智能体回调没有包含文本内容");
        }
        pending.future().complete(content.trim());
    }

    private String randomToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    private record PendingReply(String token, CompletableFuture<String> future) {
    }

    private record TemplateContext(Long id, String fileName, CertificateLanguage language) {
    }

    private record ValidatedAttachment(String fileName, String extension, MediaType mediaType) {
    }
}
