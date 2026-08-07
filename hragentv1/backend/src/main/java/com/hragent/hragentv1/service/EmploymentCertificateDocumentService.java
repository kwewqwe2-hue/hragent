package com.hragent.hragentv1.service;

import com.hragent.hragentv1.domain.EmployeePersonalProfile;
import com.hragent.hragentv1.domain.EmploymentCertificateRequest;
import com.hragent.hragentv1.domain.Tenant;
import com.hragent.hragentv1.domain.UserAccount;
import com.hragent.hragentv1.repo.TenantRepository;
import com.hragent.hragentv1.web.AppException;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class EmploymentCertificateDocumentService {
    private static final DateTimeFormatter CHINESE_DATE = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");

    private final TenantRepository tenantRepository;
    private final Path storageRoot;
    private final String companyAddress;
    private final String companyContact;

    public EmploymentCertificateDocumentService(
            TenantRepository tenantRepository,
            @Value("${app.certificate.storage-root}") String storageRoot,
            @Value("${app.certificate.company-address}") String companyAddress,
            @Value("${app.certificate.company-contact}") String companyContact
    ) {
        this.tenantRepository = tenantRepository;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.companyAddress = companyAddress;
        this.companyContact = companyContact;
    }

    public GeneratedDocument generateStandardChinese(
            EmploymentCertificateRequest request,
            UserAccount employee,
            EmployeePersonalProfile profile
    ) {
        Tenant tenant = tenantRepository.findById(request.getTenantId())
                .orElseThrow(() -> AppException.notFound("企业空间不存在"));
        String safeEmployeeNo = sanitizeFilePart(employee.getEmployeeNo());
        String fileName = "在职证明-" + safeEmployeeNo + "-" + request.getId() + ".docx";
        String storageKey = "tenant-" + request.getTenantId()
                + "/request-" + request.getId()
                + "/employment-certificate-" + safeEmployeeNo + "-" + request.getId() + ".docx";
        Path destination = resolveStorageKey(storageKey);

        try {
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), "certificate-", ".tmp");
            try {
                writeStandardDocument(temporary, request, employee, profile, tenant);
                moveAtomically(temporary, destination);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new GeneratedDocument(fileName, storageKey);
        } catch (IOException exception) {
            throw new IllegalStateException("生成在职证明文件失败", exception);
        }
    }

    public byte[] read(String storageKey) {
        Path path = resolveStorageKey(storageKey);
        if (!Files.isRegularFile(path)) {
            throw AppException.notFound("证明文件不存在，请联系空间管理员重新生成");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new IllegalStateException("读取在职证明文件失败", exception);
        }
    }

    private void writeStandardDocument(
            Path path,
            EmploymentCertificateRequest request,
            UserAccount employee,
            EmployeePersonalProfile profile,
            Tenant tenant
    ) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); OutputStream output = Files.newOutputStream(path)) {
            configurePage(document);
            document.getProperties().getCoreProperties().setTitle("在职证明 - " + employee.getEmployeeNo());
            document.getProperties().getCoreProperties().setCreator(tenant.getName() + " 人力资源部");

            XWPFParagraph reference = document.createParagraph();
            reference.setAlignment(ParagraphAlignment.RIGHT);
            addRun(reference, "编号：HR-" + tenant.getCode() + "-" + LocalDate.now().getYear()
                    + "-" + String.format("%04d", request.getId()), 10, false);

            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            title.setSpacingBefore(320);
            title.setSpacingAfter(520);
            addRun(title, "在 职 证 明", 20, true);

            LocalDate employmentStart = employee.getEntryDate() != null
                    ? employee.getEntryDate()
                    : profile.getContractStartDate();
            String body = "兹证明，" + profile.getLegalName() + "（员工编号：" + employee.getEmployeeNo()
                    + "）自 " + employmentStart.format(CHINESE_DATE) + " 起在我司任职，现任"
                    + value(employee.getDepartment()) + value(employee.getTitle())
                    + "，当前劳动关系存续且工作状态为在职。";
            addBodyParagraph(document, body);

            if (request.isIncludeSalary()) {
                String currency = profile.getCurrency() == null ? "CNY" : profile.getCurrency();
                addBodyParagraph(document, "截至本证明开具日，该员工税前月薪为 " + currency + " "
                        + MONEY_FORMAT.format(profile.getMonthlySalary()) + "。");
            }

            addBodyParagraph(document, "本证明仅用于“" + request.getPurpose()
                    + "”，不作为本公司对该员工任何经济责任或其他事项的担保。");
            addBodyParagraph(document, "特此证明。");

            XWPFParagraph company = document.createParagraph();
            company.setAlignment(ParagraphAlignment.RIGHT);
            company.setSpacingBefore(1200);
            addRun(company, tenant.getName(), 12, true);

            XWPFParagraph department = document.createParagraph();
            department.setAlignment(ParagraphAlignment.RIGHT);
            addRun(department, "人力资源部（盖章）", 12, false);

            XWPFParagraph issuedAt = document.createParagraph();
            issuedAt.setAlignment(ParagraphAlignment.RIGHT);
            addRun(issuedAt, LocalDate.now().format(CHINESE_DATE), 12, false);

            XWPFFooter footer = document.createFooter(HeaderFooterType.DEFAULT);
            XWPFParagraph footerParagraph = footer.getParagraphs().isEmpty()
                    ? footer.createParagraph()
                    : footer.getParagraphs().getFirst();
            footerParagraph.setBorderTop(Borders.SINGLE);
            footerParagraph.setAlignment(ParagraphAlignment.CENTER);
            addRun(footerParagraph, "公司地址：" + companyAddress + "    联系电话：" + companyContact, 9, false);

            document.write(output);
        }
    }

    private void configurePage(XWPFDocument document) {
        CTSectPr section = document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = section.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(11906));
        pageSize.setH(BigInteger.valueOf(16838));
        CTPageMar margins = section.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1440));
        margins.setBottom(BigInteger.valueOf(1440));
        margins.setLeft(BigInteger.valueOf(1800));
        margins.setRight(BigInteger.valueOf(1800));
        margins.setFooter(BigInteger.valueOf(720));
    }

    private void addBodyParagraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.BOTH);
        paragraph.setIndentationFirstLine(480);
        paragraph.setSpacingBetween(1.6);
        paragraph.setSpacingAfter(220);
        addRun(paragraph, text, 12, false);
    }

    private void addRun(XWPFParagraph paragraph, String text, int fontSize, boolean bold) {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(fontSize);
        run.setBold(bold);
        run.setFontFamily("宋体");
        CTRPr runProperties = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = runProperties.sizeOfRFontsArray() > 0
                ? runProperties.getRFontsArray(0)
                : runProperties.addNewRFonts();
        fonts.setEastAsia("宋体");
    }

    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw AppException.notFound("证明文件尚未生成");
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

    private String value(String value) {
        return value == null ? "" : value;
    }

    public record GeneratedDocument(String fileName, String storageKey) {
    }
}
