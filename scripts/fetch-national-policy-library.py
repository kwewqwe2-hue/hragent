"""Fetch a curated official catalogue into review snapshots; never publishes automatically.

Usage: python -X utf8 scripts/fetch-national-policy-library.py [--refresh] [id ...]
Only explicitly catalogued HTTPS government pages are read; failures remain visible.
"""
from pathlib import Path
from urllib.parse import urlparse
from urllib.robotparser import RobotFileParser
import concurrent.futures, hashlib, json, re, sys, time
import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'n8nwork/knowledge-files/national-policy-catalog.json'
OUT = ROOT / '.artifacts/national-policy-library'
OUT.mkdir(parents=True, exist_ok=True)
AGENT = 'HRAgentPolicyLibrary/1.0'

def fetch(row):
    dest = OUT / (row['id'] + '.json')
    if dest.exists() and '--refresh' not in sys.argv:
        old = json.loads(dest.read_text(encoding='utf-8'))
        if old.get('ok') and old['url'] == row['url']:
            return old
    result = dict(id=row['id'], title=row['title'], url=row['url'], ok=False)
    try:
        u = urlparse(row['url'])
        assert u.scheme == 'https' and u.hostname.endswith('.gov.cn') and not u.username
        session = requests.Session()
        session.headers['User-Agent'] = AGENT
        robots = session.get(f'{u.scheme}://{u.netloc}/robots.txt', timeout=20, allow_redirects=False)
        # MOF subdomains use their main site's missing-page redirect for absent robots.txt.
        # Only this verified official missing-page response is accepted; other redirects fail closed.
        if robots.status_code == 302 and u.hostname in ('kjs.mof.gov.cn', 'm.mof.gov.cn') and robots.headers.get('Location') == 'http://www.mof.gov.cn/404.htm':
            missing = session.get('https://www.mof.gov.cn/404.htm', timeout=20, allow_redirects=False)
            if missing.status_code == 200 and '您访问的页面不存在或已删除' in BeautifulSoup(missing.content, 'html.parser').get_text():
                result['robotsNote'] = '官方 robots.txt 跳转至经核对的缺失页面；未发现抓取禁令'
                robots.status_code = 404
        if robots.status_code == 200:
            rp = RobotFileParser(); rp.parse(robots.text.splitlines())
            if not rp.can_fetch(AGENT, row['url']):
                raise ValueError('官方 robots 规则不允许采集')
        elif robots.status_code != 404:
            raise ValueError('robots 状态无法确认: ' + str(robots.status_code))
        response = session.get(row['url'], timeout=30, allow_redirects=False)
        response.raise_for_status()
        if response.status_code != 200 or len(response.content) > 5_000_000:
            raise ValueError('需要核对重定向或正文体积')
        soup = BeautifulSoup(response.content, 'html.parser')
        for tag in soup.select('script,style,iframe,nav,header,footer'):
            tag.decompose()
        body = soup.select_one(row.get('selector', '.text-bottom, .TRS_Editor, #UCAP-CONTENT, #UCAP-CONTENT1, .pages_content, .article-content, #zoom, #Zoom, .view, .content-article, #content'))
        # Discovery fallback only; reviewer must select precise start/end before publication.
        if body is None:
            body = soup.body or soup
        links = [{'title': a.get_text(' ', strip=True), 'href': a.get('href')} for a in body.select('a[href]')]
        images = [{'alt': a.get('alt', ''), 'src': a.get('src')} for a in body.select('img[src]')]
        # Keep inline dates/amounts intact; only block boundaries introduce line breaks.
        for tag in body.select('br'):
            tag.replace_with('\n')
        for tag in body.select('p,div,h1,h2,h3,h4,tr,li'):
            tag.append('\n')
        for tag in body.select('td,th'):
            tag.append(' | ')
        text = body.get_text('', strip=False)
        text = re.sub(r'[\t\u3000\xa0 ]+', ' ', text)
        text = '\n'.join(line.strip() for line in text.splitlines() if line.strip())
        if len(text) < 100 or not re.search(r'第[一二三四五六七八九十]+条|通知|办法|规范|验证', text):
            raise ValueError('未取得完整可识别的政策正文')
        result.update(ok=True, content=text, sha256=hashlib.sha256(text.encode()).hexdigest(), links=links, images=images,
                      checkedAt=time.strftime('%Y-%m-%d'), htmlTitle=soup.title.get_text() if soup.title else '')
    except Exception as e:
        result['error'] = str(e)
    dest.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    return result

if __name__ == '__main__':
    catalog = json.loads(CATALOG.read_text(encoding='utf-8'))
    selected = [x for x in sys.argv[1:] if not x.startswith('--')]
    rows = [x for x in catalog if not selected or x['id'] in selected]
    # Low concurrency; robots checked before each curated document, no unbounded link traversal.
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(fetch, rows))
    for r in results:
        print(r['id'], len(r.get('content', '')), r.get('error', 'OK'))
    (OUT / 'fetch-report.json').write_text(json.dumps([{k:v for k,v in r.items() if k not in ('content','links')} for r in results], ensure_ascii=False, indent=2), encoding='utf-8')
