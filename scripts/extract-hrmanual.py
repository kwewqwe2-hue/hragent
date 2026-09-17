"""Extract supplied reference material without interpreting it as instructions or current company policy."""
from pathlib import Path
import hashlib, json, re, sys
import pdfplumber
source = Path(sys.argv[1])
output = Path(__file__).resolve().parents[1] / 'n8nwork/knowledge-files/hrmanual-index.json'
def clean(value):
    return re.sub(r'(?<=[\u4e00-\u9fff0-9，；、（÷×-])\n(?=[\u4e00-\u9fff0-9，；、）])', '', (value or '').strip())
chunks = []
last_title = '手册条文'
last_number = ''
last_date = ''
with pdfplumber.open(source) as document:
    for page_no, page in enumerate(document.pages, 1):
        if page_no <= 3: continue  # Index pages are not substantive evidence.
        if page_no >= 166:  # Contact directories have a different four-column schema.
            text = clean(page.extract_text() or '')
            if text:
                chunks.append({'page': page_no, 'title': '办事机构联系方式（手册原文）', 'content': text})
            continue
        tables = page.find_tables()
        for table in tables:
            for row in table.extract():
                if len(row) == 4 and row[2] and len(row[2]) > 8 and re.sub(r'\s+', '', row[2]) != '内容摘要':
                    number, title, body, date = row
                    if title and title.strip():
                        last_title = re.sub(r'\s+', '', title)
                        last_number = re.sub(r'\s+', '', number or '')
                        last_date = re.sub(r'\s+', '', date or '')
                    body = clean(body)
                    prefix = f'条文标题：{last_title}\n文号：{last_number or "本页未注明"}\n手册记载执行时间：{last_date or "本页未注明"}\n'
                    chunks.append({'page': page_no, 'title': last_title, 'content': prefix + body})
                elif len(row) != 4 and any(row):
                    text = ' | '.join(clean(c) for c in row if c)
                    if len(text) > 40: chunks.append({'page': page_no, 'title': '手册资料', 'content': text})
        outside = page.filter(lambda obj: not any(t.bbox[0] <= (obj.get('x0',0)+obj.get('x1',0))/2 <= t.bbox[2] and t.bbox[1] <= (obj.get('top',0)+obj.get('bottom',0))/2 <= t.bbox[3] for t in tables))
        text = clean(outside.extract_text() or '')
        if len(text) > 70:
            chunks.append({'page': page_no, 'title': text.split('\n')[0][:65], 'content': text})
        if page_no % 30 == 0: print(f'Extracted {page_no}/{len(document.pages)} pages', flush=True)
    page_count = len(document.pages)
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps({'fileName': source.name, 'title': '人力资源和社会保障管理实务手册', 'version': '2022年版', 'pages': page_count, 'sha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'chunks': chunks}, ensure_ascii=False), encoding='utf-8')
print(f'Extracted {len(chunks)} reference sections to {output}')
