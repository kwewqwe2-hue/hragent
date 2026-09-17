"""Small, idempotent source migration for employee service metadata."""
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
java = root / 'hragentv1/backend/src/main/java/com/hragent/hragentv1'
backup = root / '.backups/employee-services'

def edit(path, transform):
    text = path.read_text(encoding='utf-8')
    updated = transform(text)
    if updated != text:
        saved = backup / path.relative_to(root)
        if not saved.exists():
            saved.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, saved)
        path.write_text(updated, encoding='utf-8')

fields = [('String', 'jobGrades', 240), ('String', 'workTypes', 240),
          ('String', 'legalEntities', 500), ('String', 'sourceUrl', 1000),
          ('LocalDate', 'effectiveFrom', 0), ('LocalDate', 'effectiveTo', 0)]
def metadata(text):
    if 'private String jobGrades;' in text:
        return text
    declarations = '\n'.join((f'    @Column(length = {size})\n' if size else '') + f'    private {kind} {name};' for kind, name, size in fields)
    methods = '\n'.join(f'    public {kind} get{name[0].upper()+name[1:]}() {{ return {name}; }}\n    public void set{name[0].upper()+name[1:]}({kind} value) {{ this.{name} = value; }}' for kind, name, _ in fields)
    return text.replace('    public Long getId()', declarations + '\n\n' + methods + '\n\n    public Long getId()', 1)
edit(java / 'domain/KnowledgeArticle.java', metadata)

def dto(text):
    if 'String jobGrades' in text:
        return text
    old = '@Size(max = 40) String reviewStatus\n    ) {\n    }'
    new = '''@Size(max = 40) String reviewStatus,
            @Size(max = 240) String jobGrades,
            @Size(max = 240) String workTypes,
            @Size(max = 500) String legalEntities,
            @Size(max = 1000) String sourceUrl,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        public KnowledgeUpsertRequest(String category, String title, String content, String source,
                String region, LocalDate publishedAt, LocalDate updatedAt, String reviewStatus) {
            this(category, title, content, source, region, publishedAt, updatedAt, reviewStatus,
                    null, null, null, null, null, null);
        }
    }'''
    assert old in text
    return text.replace(old, new)
edit(java / 'dto/AdminDtos.java', dto)

def admin(text):
    if 'article.setJobGrades' in text:
        return text
    target = 'article.setCategory(request.category());'
    extra = '''if (request.effectiveFrom() != null && request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw AppException.badRequest("失效日期不能早于生效日期");
        }
        EmployeeServiceSupport.requireHttpUrl(request.sourceUrl());
        article.setJobGrades(request.jobGrades());
        article.setWorkTypes(request.workTypes());
        article.setLegalEntities(request.legalEntities());
        article.setSourceUrl(request.sourceUrl());
        article.setEffectiveFrom(request.effectiveFrom());
        article.setEffectiveTo(request.effectiveTo());
        '''
    assert target in text
    return text.replace(target, extra + target)
edit(java / 'service/AdminService.java', admin)

edit(java / 'domain/EmploymentCertificateType.java', lambda t: t.replace('STANDARD("标准在职证明"),', 'STANDARD("标准在职证明"),\n    INCOME("收入证明"),') if 'INCOME(' not in t else t)
edit(java / 'service/EmploymentCertificateService.java', lambda t: t.replace('request.setIncludeSalary(input.includeSalary());', 'request.setIncludeSalary(input.includeSalary() || input.certificateType() == EmploymentCertificateType.INCOME);').replace('request.getCertificateType() == EmploymentCertificateType.STANDARD', 'request.getCertificateType() != EmploymentCertificateType.VISA'))
def document(text):
    if 'String documentTitle =' in text:
        return text
    text = text.replace('String fileName = "在职证明-"', 'String fileName = (request.getCertificateType() == com.hragent.hragentv1.domain.EmploymentCertificateType.INCOME ? "收入证明-" : "在职证明-")')
    text = text.replace('configurePage(document);', 'configurePage(document);\n            String documentTitle = request.getCertificateType() == com.hragent.hragentv1.domain.EmploymentCertificateType.INCOME ? "收入证明" : "在职证明";')
    text = text.replace('setTitle("在职证明 - " +', 'setTitle(documentTitle + " - " +').replace('addRun(title, "在 职 证 明",', 'addRun(title, documentTitle,')
    text = text.replace('"人力资源部（盖章）"', '"人力资源部（请联系 HR 加盖公章或完成企业电子签章）"')
    return text
edit(java / 'service/EmploymentCertificateDocumentService.java', document)
print('Employee service metadata and income certificate source updated.')
