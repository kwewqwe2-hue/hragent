package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.EmployeePersonalProfile;
import com.hragent.hragentv1.domain.EmploymentCertificateRequest;
import com.hragent.hragentv1.domain.EmploymentCertificateTemplate;
import com.hragent.hragentv1.domain.Tenant;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.repo.TenantRepository;
import com.hragent.hragentv1.web.AppException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VisaCertificateDocumentService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\{\\{[^{}]+}}", Pattern.UNICODE_CASE);

    private final TenantRepository tenantRepository;
    private final SecretCryptoService secretCryptoService;
    private final Path storageRoot;

    public VisaCertificateDocumentService(
            TenantRepository tenantRepository,
            SecretCryptoService secretCryptoService,
            @Value("${app.certificate.storage-root}") String storageRoot
    ) {
        this.tenantRepository = tenantRepository;
        this.secretCryptoService = secretCryptoService;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public EmploymentCertificateDocumentService.GeneratedDocument generate(
            EmploymentCertificateTemplate template,
            EmploymentCertificateRequest request,
            UserAccount employee,
            EmployeePersonalProfile profile
    ) {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> AppException.notFound("企业空间不存在"));
        String employeeNo = sanitizeFilePart(employee.getEmployeeNo());
        String fileName = "签证在职证明-" + employeeNo + "-" + request.getId() + ".docx";
        String storageKey = "tenant-" + request.getTenantId()
                + "/request-" + request.getId()
                + "/visa-employment-certificate-" + employeeNo + "-" + request.getId() + ".docx";
        Path source = resolveStorageKey(template.getStorageKey());
        Path destination = resolveStorageKey(storageKey);

        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("匹配到的 Word 模板文件不存在，请重新上传模板");
        }

        Map<String, String> values = placeholderValues(request, employee, profile, tenant);
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), "visa-certificate-", ".tmp");
            try {
                render(source, temporary, values);
                moveAtomically(temporary, destination);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new EmploymentCertificateDocumentService.GeneratedDocument(fileName, storageKey);
        } catch (IOException exception) {
            throw new IllegalStateException("生成签证在职证明文件失败", exception);
        }
    }

    private Map<String, String> placeholderValues(
            EmploymentCertificateRequest request,
            UserAccount employee,
            EmployeePersonalProfile profile,
            Tenant tenant
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("{{legalName}}", value(profile.getLegalName()));
        values.put("{{englishName}}", value(profile.getEnglishName()));
        values.put("{{employeeNo}}", value(employee.getEmployeeNo()));
        values.put("{{department}}", value(employee.getDepartment()));
        values.put("{{title}}", value(employee.getTitle()));
        values.put("{{entryDate}}", formatDate(employee.getEntryDate() != null
                ? employee.getEntryDate() : profile.getContractStartDate()));
        values.put("{{passportNumber}}", secretCryptoService.decrypt(profile.getPassportNumberEncrypted()));
        values.put("{{passportExpiryDate}}", formatDate(profile.getPassportExpiryDate()));
        values.put("{{monthlySalary}}", request.isIncludeSalary()
                ? formatMoney(profile.getMonthlySalary()) : "");
        values.put("{{currency}}", request.isIncludeSalary()
                ? valueOrDefault(profile.getCurrency(), "CNY") : "");
        values.put("{{companyName}}", value(tenant.getName()));
        values.put("{{issueDate}}", formatDate(LocalDate.now()));
        values.put("{{purpose}}", value(request.getPurpose()));
        values.put("{{destinationCountry}}", value(request.getDestinationCountry()));
        values.put("{{consulateName}}", value(request.getConsulateName()));
        return values;
    }

    private void render(Path source, Path destination, Map<String, String> values) throws IOException {
        try (InputStream input = Files.newInputStream(source);
             XWPFDocument document = new XWPFDocument(input);
             OutputStream output = Files.newOutputStream(destination)) {
            replaceBodyElements(document.getBodyElements(), values);
            document.getHeaderList().forEach(header -> replaceBodyElements(header.getBodyElements(), values));
            document.getFooterList().forEach(footer -> replaceBodyElements(footer.getBodyElements(), values));

            List<String> unresolved = findUnresolved(document);
            if (!unresolved.isEmpty()) {
                throw new IllegalStateException("模板包含不支持的占位符：" + String.join("、", unresolved));
            }
            document.write(output);
        }
    }

    private void replaceBodyElements(List<IBodyElement> elements, Map<String, String> values) {
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph paragraph) {
                values.forEach((placeholder, replacement) -> replaceInParagraph(paragraph, placeholder, replacement));
            } else if (element instanceof XWPFTable table) {
                for (XWPFTableCell cell : table.getRows().stream()
                        .flatMap(row -> row.getTableCells().stream()).toList()) {
                    replaceBodyElements(cell.getBodyElements(), values);
                }
            }
        }
    }

    private void replaceInParagraph(XWPFParagraph paragraph, String placeholder, String replacement) {
        while (true) {
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs.isEmpty()) {
                return;
            }
            String combined = runs.stream().map(this::runText).reduce("", String::concat);
            int matchStart = combined.indexOf(placeholder);
            if (matchStart < 0) {
                return;
            }
            int matchEnd = matchStart + placeholder.length();
            int cursor = 0;
            int startRun = -1;
            int endRun = -1;
            int startOffset = 0;
            int endOffset = 0;

            for (int index = 0; index < runs.size(); index++) {
                String text = runText(runs.get(index));
                int next = cursor + text.length();
                if (startRun < 0 && matchStart < next) {
                    startRun = index;
                    startOffset = matchStart - cursor;
                }
                if (matchEnd <= next) {
                    endRun = index;
                    endOffset = matchEnd - cursor;
                    break;
                }
                cursor = next;
            }

            if (startRun < 0 || endRun < 0) {
                throw new IllegalStateException("Word 模板占位符结构无法解析：" + placeholder);
            }
            String startText = runText(runs.get(startRun));
            String endText = runText(runs.get(endRun));
            if (startRun == endRun) {
                setRunText(runs.get(startRun), startText.substring(0, startOffset)
                        + replacement + startText.substring(endOffset));
            } else {
                setRunText(runs.get(startRun), startText.substring(0, startOffset) + replacement);
                for (int index = startRun + 1; index < endRun; index++) {
                    setRunText(runs.get(index), "");
                }
                setRunText(runs.get(endRun), endText.substring(endOffset));
            }
        }
    }

    private List<String> findUnresolved(XWPFDocument document) {
        Map<String, Boolean> unresolved = new LinkedHashMap<>();
        collectUnresolved(document.getBodyElements(), unresolved);
        document.getHeaderList().forEach(header -> collectUnresolved(header.getBodyElements(), unresolved));
        document.getFooterList().forEach(footer -> collectUnresolved(footer.getBodyElements(), unresolved));
        return unresolved.keySet().stream().toList();
    }

    private void collectUnresolved(List<IBodyElement> elements, Map<String, Boolean> unresolved) {
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph paragraph) {
                Matcher matcher = UNRESOLVED_PLACEHOLDER.matcher(paragraph.getText());
                while (matcher.find()) {
                    unresolved.put(matcher.group(), Boolean.TRUE);
                }
            } else if (element instanceof XWPFTable table) {
                table.getRows().forEach(row -> row.getTableCells()
                        .forEach(cell -> collectUnresolved(cell.getBodyElements(), unresolved)));
            }
        }
    }

    private String runText(XWPFRun run) {
        String text = run.text();
        return text == null ? "" : text;
    }

    private void setRunText(XWPFRun run, String value) {
        while (run.getCTR().sizeOfTArray() > 0) {
            run.getCTR().removeT(0);
        }
        run.setText(value == null ? "" : value);
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw AppException.notFound("Word 模板文件尚未上传");
        }
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw AppException.forbidden("证明文件路径无效");
        }
        return resolved;
    }

    private void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String sanitizeFilePart(String value) {
        String sanitized = value == null ? "employee" : value.replaceAll("[^A-Za-z0-9_-]", "-");
        return sanitized.isBlank() ? "employee" : sanitized;
    }

    private String formatDate(LocalDate value) {
        return value == null ? "" : value.format(DATE_FORMAT);
    }

    private String formatMoney(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
