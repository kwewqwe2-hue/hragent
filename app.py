import html
import json
import os
import re
import sqlite3
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime
from http import HTTPStatus
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path


BASE_DIR = Path(__file__).resolve().parent
STATIC_DIR = BASE_DIR / "static"
DATA_DIR = BASE_DIR / "data"
KB_DIR = BASE_DIR / "knowledge_base"
TEMPLATE_DIR = BASE_DIR / "templates"
GENERATED_DIR = BASE_DIR / "generated_docs"
DB_PATH = DATA_DIR / "hragent_demo.sqlite3"
CONFIG_PATH = DATA_DIR / "config.json"

APP_NAME = "HR智能体"
PORT = int(os.environ.get("HRAGENT_PORT", "8765"))
SESSIONS = {}


ROLE_LABELS = {
    "employee": "员工端",
    "manager": "主管端",
    "hr": "HR端",
    "admin": "管理员端",
}


DEFAULT_CONFIG = {
    "deepseek_api_key": "",
    "deepseek_base_url": "https://api.deepseek.com",
    "deepseek_model": "deepseek-chat",
    "enable_web_fallback": True,
    "show_process": True,
}


KEYWORDS = [
    "入职", "离职", "在职证明", "收入证明", "离职证明", "调岗", "培训", "工资", "工资条",
    "社保", "公积金", "假期", "年假", "病假", "婚假", "产假", "陪产假", "合同", "劳动合同",
    "投诉", "劳动争议", "仲裁", "情绪", "压力", "绩效", "福利", "工单", "证明", "权限",
    "上海", "北京", "广州", "深圳", "海外", "客户", "派遣", "外包", "实习生",
]


def now_text():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def ensure_dirs():
    for path in [DATA_DIR, KB_DIR, TEMPLATE_DIR, GENERATED_DIR, STATIC_DIR]:
        path.mkdir(parents=True, exist_ok=True)


def read_json(path, default):
    if not path.exists():
        return dict(default)
    try:
        with path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        merged = dict(default)
        merged.update(data)
        return merged
    except Exception:
        return dict(default)


def write_json(path, data):
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    ensure_dirs()
    with db() as conn:
        conn.executescript(
            """
            create table if not exists users (
                id integer primary key autoincrement,
                username text unique,
                password text,
                role text,
                employee_id text,
                display_name text
            );
            create table if not exists employees (
                employee_id text primary key,
                name text,
                city text,
                department text,
                title text,
                employee_type text,
                hire_date text,
                contract_entity text,
                manager_id text,
                base_salary real,
                bonus real,
                social_status text,
                housing_fund_status text,
                leave_balance real,
                email text
            );
            create table if not exists tickets (
                id text primary key,
                creator_employee_id text,
                category text,
                title text,
                description text,
                status text,
                priority text,
                assigned_to text,
                created_at text,
                updated_at text
            );
            create table if not exists connectors (
                id text primary key,
                name text,
                connector_type text,
                target_system text,
                auth_type text,
                endpoint text,
                status text,
                description text
            );
            create table if not exists audit_logs (
                id integer primary key autoincrement,
                actor text,
                role text,
                action text,
                detail text,
                created_at text
            );
            """
        )
        if conn.execute("select count(*) from users").fetchone()[0] == 0:
            seed_db(conn)


def seed_db(conn):
    users = [
        ("employee01", "123456", "employee", "E1001", "张小禾"),
        ("employee02", "123456", "employee", "E1002", "李明"),
        ("manager01", "123456", "manager", "E2001", "王主管"),
        ("hr01", "123456", "hr", "H3001", "陈HR"),
        ("admin", "admin123", "admin", "A9001", "系统管理员"),
    ]
    conn.executemany(
        "insert into users(username,password,role,employee_id,display_name) values(?,?,?,?,?)",
        users,
    )
    employees = [
        ("E1001", "张小禾", "上海", "客户服务一部", "客户服务专员", "正式员工", "2022-03-14", "上海外服示例主体A", "E2001", 14500, 1800, "正常缴纳", "正常缴纳", 6.5, "zhangxh@example.local"),
        ("E1002", "李明", "北京", "交付中心", "实施顾问", "正式员工", "2021-07-01", "上海外服示例主体B", "E2001", 16800, 2300, "正常缴纳", "正常缴纳", 9.0, "liming@example.local"),
        ("E2001", "王主管", "上海", "客户服务一部", "团队主管", "正式员工", "2019-11-20", "上海外服示例主体A", "", 23500, 5000, "正常缴纳", "正常缴纳", 11.0, "manager@example.local"),
        ("H3001", "陈HR", "上海", "员工关系部", "员工关系HR", "正式员工", "2018-05-08", "上海外服示例主体A", "", 21000, 4200, "正常缴纳", "正常缴纳", 8.0, "hr@example.local"),
        ("A9001", "系统管理员", "上海", "数字化部", "系统管理员", "正式员工", "2020-01-01", "上海外服示例主体A", "", 26000, 6500, "正常缴纳", "正常缴纳", 12.0, "admin@example.local"),
    ]
    conn.executemany(
        """
        insert into employees(employee_id,name,city,department,title,employee_type,hire_date,contract_entity,
        manager_id,base_salary,bonus,social_status,housing_fund_status,leave_balance,email)
        values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        employees,
    )
    connectors = [
        ("c-employee", "员工系统连接器", "REST API", "员工主数据系统", "Token / OAuth2", "https://intranet.example.local/hr/employee", "模拟可用", "查询员工基本信息、合同主体、部门岗位、入职日期。"),
        ("c-payroll", "薪酬系统连接器", "REST API", "薪酬系统", "Token + IP白名单", "https://intranet.example.local/payroll", "模拟可用", "查询本人工资条、社保公积金状态。"),
        ("c-attendance", "考勤系统连接器", "REST API", "考勤系统", "SSO", "https://intranet.example.local/attendance", "模拟可用", "查询假期余额、考勤异常。"),
        ("c-ticket", "工单系统连接器", "Webhook / API", "工单系统", "API Key", "https://intranet.example.local/tickets", "模拟可用", "创建、补充、分派、查询工单。"),
        ("c-rpa", "RPA衔接器", "RPA Adapter", "老HR系统", "机器人账号", "rpa://legacy-hr", "模拟可用", "没有接口时，由RPA模拟人工查询、录入、生成文件。"),
        ("c-kb", "外服知识库连接器", "Knowledge Connector", "内网知识库", "内网账号/SSO", "https://intranet.example.local/kb", "待接入", "后续连接外服内网知识库，Demo阶段使用本地样例知识库。"),
    ]
    conn.executemany(
        "insert into connectors(id,name,connector_type,target_system,auth_type,endpoint,status,description) values(?,?,?,?,?,?,?,?)",
        connectors,
    )


def audit(actor, role, action, detail):
    with db() as conn:
        conn.execute(
            "insert into audit_logs(actor,role,action,detail,created_at) values(?,?,?,?,?)",
            (actor or "anonymous", role or "-", action, detail[:1000], now_text()),
        )


def row_to_dict(row):
    if row is None:
        return None
    return {k: row[k] for k in row.keys()}


def get_user_by_token(headers):
    auth = headers.get("Authorization", "")
    token = ""
    if auth.startswith("Bearer "):
        token = auth[7:]
    token = headers.get("X-Token", token)
    username = SESSIONS.get(token)
    if not username:
        return None
    with db() as conn:
        return row_to_dict(conn.execute("select * from users where username=?", (username,)).fetchone())


def get_employee(employee_id):
    with db() as conn:
        return row_to_dict(conn.execute("select * from employees where employee_id=?", (employee_id,)).fetchone())


def employee_scope(user):
    with db() as conn:
        if user["role"] in ("hr", "admin"):
            return [row_to_dict(r) for r in conn.execute("select * from employees order by employee_id").fetchall()]
        if user["role"] == "manager":
            rows = conn.execute(
                "select * from employees where employee_id=? or manager_id=? order by employee_id",
                (user["employee_id"], user["employee_id"]),
            ).fetchall()
            return [row_to_dict(r) for r in rows]
        emp = conn.execute("select * from employees where employee_id=?", (user["employee_id"],)).fetchone()
        return [row_to_dict(emp)] if emp else []


def load_kb_docs():
    docs = []
    for path in sorted(KB_DIR.glob("*.md")):
        try:
            text = path.read_text(encoding="utf-8")
            title = text.splitlines()[0].lstrip("# ").strip() if text.splitlines() else path.stem
            docs.append({"id": path.stem, "title": title, "path": str(path), "text": text})
        except Exception:
            continue
    return docs


def extract_terms(query):
    query_l = query.lower()
    terms = set()
    for item in KEYWORDS:
        if item.lower() in query_l:
            terms.add(item.lower())
    for word in re.findall(r"[a-zA-Z0-9_]{2,}", query_l):
        terms.add(word)
    chinese = re.findall(r"[\u4e00-\u9fff]{2,}", query_l)
    for chunk in chinese:
        if len(chunk) <= 4:
            terms.add(chunk)
        else:
            for i in range(len(chunk) - 1):
                terms.add(chunk[i:i + 2])
    return terms


def search_kb(query, limit=4):
    terms = extract_terms(query)
    results = []
    for doc in load_kb_docs():
        text_l = doc["text"].lower()
        score = 0
        matched = []
        for term in terms:
            if term and term in text_l:
                score += 3 if term in [k.lower() for k in KEYWORDS] else 1
                matched.append(term)
        if score > 0:
            snippet = make_snippet(doc["text"], matched[:3])
            results.append({
                "title": doc["title"],
                "id": doc["id"],
                "score": score,
                "matched": matched[:8],
                "snippet": snippet,
            })
    results.sort(key=lambda x: x["score"], reverse=True)
    return results[:limit]


def make_snippet(text, matched):
    plain = re.sub(r"\s+", " ", text)
    idx = -1
    for term in matched:
        idx = plain.lower().find(term.lower())
        if idx >= 0:
            break
    if idx < 0:
        return plain[:260]
    start = max(0, idx - 90)
    end = min(len(plain), idx + 220)
    return plain[start:end]


def classify_intent(message):
    m = message.lower()
    if any(x in m for x in ["投诉", "仲裁", "劳动争议", "违法", "被迫", "情绪", "崩溃", "抑郁", "压力很大"]):
        return "risk_escalation"
    if any(x in m for x in ["在职证明", "收入证明", "离职证明", "证明"]):
        return "certificate"
    if any(x in m for x in ["工资", "工资条", "薪资", "薪水", "奖金"]):
        return "salary"
    if any(x in m for x in ["社保", "公积金", "五险一金"]):
        return "social_fund"
    if any(x in m for x in ["入职", "报到", "offer", "材料"]):
        return "onboarding"
    if any(x in m for x in ["离职", "退工", "交接"]):
        return "offboarding"
    if any(x in m for x in ["工单", "申请", "修改银行卡", "补充材料"]):
        return "ticket"
    return "qa"


def web_search(query):
    config = read_json(CONFIG_PATH, DEFAULT_CONFIG)
    if not config.get("enable_web_fallback"):
        return []
    url = "https://api.duckduckgo.com/?" + urllib.parse.urlencode({
        "q": query,
        "format": "json",
        "no_html": "1",
        "no_redirect": "1",
    })
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "HRAgentDemo/0.1"})
        with urllib.request.urlopen(req, timeout=6) as resp:
            data = json.loads(resp.read().decode("utf-8", errors="ignore"))
        results = []
        if data.get("AbstractText"):
            results.append({
                "title": data.get("Heading") or "DuckDuckGo摘要",
                "url": data.get("AbstractURL") or "",
                "snippet": data.get("AbstractText")[:320],
            })
        for topic in data.get("RelatedTopics", []):
            if isinstance(topic, dict) and topic.get("Text"):
                results.append({
                    "title": topic.get("FirstURL", "相关结果"),
                    "url": topic.get("FirstURL", ""),
                    "snippet": topic.get("Text", "")[:260],
                })
            if len(results) >= 4:
                break
        return results[:4]
    except Exception:
        return []


def call_deepseek(user_message, kb_results, web_results, user, tool_context, intent):
    config = read_json(CONFIG_PATH, DEFAULT_CONFIG)
    api_key = config.get("deepseek_api_key", "").strip()
    if not api_key:
        return None
    base_url = config.get("deepseek_base_url", "https://api.deepseek.com").rstrip("/")
    model = config.get("deepseek_model", "deepseek-chat")
    endpoint = base_url + "/chat/completions"
    kb_context = "\n\n".join([f"【{r['title']}】{r['snippet']}" for r in kb_results]) or "知识库未命中。"
    web_context = "\n\n".join([f"【{r['title']}】{r['snippet']} {r.get('url','')}" for r in web_results]) or "未使用联网结果。"
    system = (
        "你是HR智能体Demo中的顶级员工关系HR顾问。你必须用中文回答，语气专业、克制、可执行。"
        "你是辅助系统，不做最终人事判断、法律结论或处罚决定。涉及投诉、劳动争议、薪酬异议、绩效争议、员工情绪风险时，建议转人工。"
        "回答中展示“处理步骤、依据、答复、建议下一步”，但不要展示隐藏推理链。"
        "如果知识库不足，要明确说明依据不足；联网结果仅作为补充，正式口径以公司制度和官方/法务确认资料为准。"
    )
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": json.dumps({
                "用户角色": ROLE_LABELS.get(user.get("role"), user.get("role")),
                "意图": intent,
                "员工问题": user_message,
                "知识库片段": kb_context,
                "联网补充": web_context,
                "系统工具上下文": tool_context,
            }, ensure_ascii=False)},
        ],
        "temperature": 0.2,
    }
    try:
        req = urllib.request.Request(
            endpoint,
            data=json.dumps(payload).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {api_key}",
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        return data["choices"][0]["message"]["content"]
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="ignore")[:500]
        return f"DeepSeek API 调用失败：HTTP {exc.code}。请检查 API Key、模型名或网络。{detail}"
    except Exception as exc:
        return f"DeepSeek API 调用失败：{exc}"


def local_answer(message, intent, kb_results, web_results, user, tool_context):
    process = [
        f"识别意图：{intent}",
        "检索本地知识库：" + ("命中" if kb_results else "未命中"),
    ]
    if not kb_results:
        process.append("尝试联网补充：" + ("获得结果" if web_results else "未获得可用结果"))
    answer_parts = []
    escalate = intent == "risk_escalation"
    if intent == "certificate":
        answer_parts.append("你可以在“文档生成”里选择在职证明、收入证明或离职证明。系统会用当前登录员工的模拟主数据填充模板，并生成可下载文件。")
    elif intent == "salary":
        if user["role"] == "employee":
            answer_parts.append("工资查询属于敏感数据，Demo 只允许员工查看本人模拟工资信息。你可以打开“工资查询”页面查看明细。若对扣款或奖金有异议，建议创建工单转薪酬HR。")
        else:
            answer_parts.append("工资查询涉及敏感数据。HR/管理员视角应通过权限控制查看授权范围数据，并记录审计日志。")
    elif intent == "social_fund":
        answer_parts.append("社保/公积金查询需要先校验员工身份和地区。Demo 会展示本人缴纳状态；正式环境应接入社保、公积金或薪酬系统接口。")
    elif intent == "onboarding":
        answer_parts.append("入职流程通常包括资料收集、合同签署、系统建档、社保公积金确认、账号开通、入职培训等。Demo 中可用知识库和工单模拟流程。")
    elif intent == "offboarding":
        answer_parts.append("离职流程通常包括申请、审批、交接、薪资结算、社保公积金处理、证明开具等。涉及争议时应转人工。")
    elif intent == "ticket":
        answer_parts.append("该问题适合创建工单。Demo 中可在“工单处理”页面创建、补充、分派或查看工单状态。")
    elif intent == "risk_escalation":
        answer_parts.append("该问题涉及投诉、劳动争议或员工情绪风险，AI 不应直接判断责任或给出最终处理结论。建议创建高优先级工单并转员工关系HR人工处理。")
    else:
        answer_parts.append("我会优先依据本地知识库回答；如果知识库不足，会提示依据不足并尝试联网补充。")
    if kb_results:
        answer_parts.append("知识库依据：")
        for r in kb_results[:3]:
            answer_parts.append(f"- {r['title']}：{r['snippet']}")
    if web_results:
        answer_parts.append("联网补充：")
        for r in web_results[:3]:
            answer_parts.append(f"- {r['title']}：{r['snippet']} {r.get('url','')}")
    if tool_context:
        answer_parts.append(f"系统上下文：{tool_context}")
    answer_parts.append("说明：本 Demo 不替代正式HR、法务或公司制度口径。")
    return {
        "answer": "\n".join(answer_parts),
        "process": process,
        "escalate": escalate,
    }


def build_tool_context(intent, user):
    emp = get_employee(user["employee_id"])
    if not emp:
        return ""
    if intent == "salary":
        return f"当前员工：{emp['name']}，城市：{emp['city']}，基本工资示例：{emp['base_salary']}，奖金示例：{emp['bonus']}。"
    if intent == "social_fund":
        return f"当前员工：{emp['name']}，社保状态：{emp['social_status']}，公积金状态：{emp['housing_fund_status']}。"
    if intent == "certificate":
        return f"当前员工：{emp['name']}，部门：{emp['department']}，岗位：{emp['title']}，入职日期：{emp['hire_date']}，合同主体：{emp['contract_entity']}。"
    return f"当前员工：{emp['name']}，城市：{emp['city']}，部门：{emp['department']}，岗位：{emp['title']}。"


def generate_certificate(user, cert_type, purpose):
    emp = get_employee(user["employee_id"])
    if not emp:
        raise ValueError("当前用户没有员工档案")
    file_id = f"{cert_type}_{emp['employee_id']}_{int(time.time())}.html"
    path = GENERATED_DIR / file_id
    title_map = {
        "employment": "在职证明",
        "income": "收入证明",
        "resignation": "离职证明",
        "training": "培训证明",
    }
    title = title_map.get(cert_type, "员工证明")
    salary_line = ""
    if cert_type == "income":
        salary_line = f"<p>该员工月度收入示例为人民币 {emp['base_salary'] + emp['bonus']:.2f} 元。本数字为Demo模拟数据，不作为真实收入证明。</p>"
    resign_line = ""
    if cert_type == "resignation":
        resign_line = "<p>该员工离职信息需以正式离职审批和系统记录为准。Demo不生成真实离职证明。</p>"
    content = f"""<!doctype html>
<html><head><meta charset="utf-8"><title>{html.escape(title)}</title>
<style>body{{font-family:'Microsoft YaHei',Arial,sans-serif;margin:48px;line-height:1.8;color:#111}}h1{{text-align:center}}.seal{{margin-top:48px;text-align:right}}</style>
</head><body>
<h1>{html.escape(title)}</h1>
<p>兹证明，{html.escape(emp['name'])}（工号：{html.escape(emp['employee_id'])}）为我司员工，当前部门为{html.escape(emp['department'])}，岗位为{html.escape(emp['title'])}，入职日期为{html.escape(emp['hire_date'])}，合同主体为{html.escape(emp['contract_entity'])}。</p>
{salary_line}
{resign_line}
<p>用途：{html.escape(purpose or '未填写')}</p>
<p>本文件由 HR智能体 Demo 根据模拟数据生成，仅用于演示，不具备正式证明效力。正式文件应由HR审核并按公司流程盖章。</p>
<div class="seal">生成时间：{now_text()}<br>HR智能体 Demo</div>
</body></html>"""
    path.write_text(content, encoding="utf-8")
    audit(user["username"], user["role"], "生成文档", f"{title} {file_id}")
    return {"file": file_id, "download_url": f"/downloads/{urllib.parse.quote(file_id)}", "title": title}


class Handler(BaseHTTPRequestHandler):
    server_version = "HRAgentDemo/0.1"

    def log_message(self, fmt, *args):
        return

    def send_json(self, data, status=200):
        raw = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def send_text(self, text, status=200, content_type="text/plain; charset=utf-8"):
        raw = text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def read_body(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0:
            return {}
        raw = self.rfile.read(length).decode("utf-8")
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return {}

    def current_user(self):
        return get_user_by_token(self.headers)

    def require_user(self):
        user = self.current_user()
        if not user:
            self.send_json({"error": "未登录或会话已过期"}, 401)
            return None
        return user

    def do_GET(self):
        try:
            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path
            if path == "/":
                return self.serve_file(STATIC_DIR / "index.html", "text/html; charset=utf-8")
            if path.startswith("/static/"):
                return self.serve_static(path)
            if path.startswith("/downloads/"):
                return self.serve_download(path)
            if path.startswith("/api/"):
                return self.handle_api_get(path)
            self.send_text("Not found", 404)
        except Exception:
            self.send_json({"error": "服务器异常", "detail": traceback.format_exc()}, 500)

    def do_POST(self):
        try:
            parsed = urllib.parse.urlparse(self.path)
            path = parsed.path
            if path.startswith("/api/"):
                return self.handle_api_post(path)
            self.send_text("Not found", 404)
        except Exception:
            self.send_json({"error": "服务器异常", "detail": traceback.format_exc()}, 500)

    def serve_file(self, path, content_type):
        if not path.exists():
            self.send_text("Not found", 404)
            return
        raw = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def serve_static(self, url_path):
        rel = url_path.removeprefix("/static/").replace("/", os.sep)
        path = (STATIC_DIR / rel).resolve()
        if not str(path).startswith(str(STATIC_DIR.resolve())):
            return self.send_text("Forbidden", 403)
        content_type = "text/plain; charset=utf-8"
        if path.suffix == ".css":
            content_type = "text/css; charset=utf-8"
        elif path.suffix == ".js":
            content_type = "application/javascript; charset=utf-8"
        elif path.suffix == ".html":
            content_type = "text/html; charset=utf-8"
        return self.serve_file(path, content_type)

    def serve_download(self, url_path):
        name = urllib.parse.unquote(url_path.removeprefix("/downloads/"))
        path = (GENERATED_DIR / name).resolve()
        if not str(path).startswith(str(GENERATED_DIR.resolve())) or not path.exists():
            return self.send_text("Not found", 404)
        raw = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "application/msword; charset=utf-8")
        self.send_header("Content-Disposition", f"attachment; filename*=UTF-8''{urllib.parse.quote(name)}")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def handle_api_get(self, path):
        if path == "/api/health":
            return self.send_json({"ok": True, "app": APP_NAME, "time": now_text()})
        if path == "/api/config":
            cfg = read_json(CONFIG_PATH, DEFAULT_CONFIG)
            return self.send_json({
                "deepseek_base_url": cfg.get("deepseek_base_url"),
                "deepseek_model": cfg.get("deepseek_model"),
                "enable_web_fallback": cfg.get("enable_web_fallback"),
                "has_api_key": bool(cfg.get("deepseek_api_key")),
            })
        user = self.require_user()
        if not user:
            return
        if path == "/api/me":
            emp = get_employee(user["employee_id"])
            return self.send_json({"user": user, "employee": emp, "role_label": ROLE_LABELS.get(user["role"])})
        if path == "/api/dashboard":
            return self.api_dashboard(user)
        if path == "/api/knowledge":
            docs = [{"id": d["id"], "title": d["title"], "chars": len(d["text"])} for d in load_kb_docs()]
            return self.send_json({"docs": docs})
        if path == "/api/salary":
            return self.api_salary(user)
        if path == "/api/social-fund":
            return self.api_social(user)
        if path == "/api/tickets":
            return self.api_tickets(user)
        if path == "/api/connectors":
            return self.api_connectors(user)
        if path == "/api/audit":
            return self.api_audit(user)
        if path == "/api/employees":
            if user["role"] not in ("hr", "admin", "manager"):
                return self.send_json({"error": "无权查看员工列表"}, 403)
            return self.send_json({"employees": employee_scope(user)})
        return self.send_json({"error": "unknown api"}, 404)

    def handle_api_post(self, path):
        body = self.read_body()
        if path == "/api/login":
            return self.api_login(body)
        if path == "/api/logout":
            token = self.headers.get("X-Token", "")
            SESSIONS.pop(token, None)
            return self.send_json({"ok": True})
        if path == "/api/config":
            return self.api_save_config(body)
        user = self.require_user()
        if not user:
            return
        if path == "/api/chat":
            return self.api_chat(user, body)
        if path == "/api/certificates":
            return self.api_certificate(user, body)
        if path == "/api/tickets":
            return self.api_create_ticket(user, body)
        if path == "/api/connectors/test":
            return self.api_test_connector(user, body)
        if path == "/api/knowledge/search":
            query = body.get("query", "")
            return self.send_json({"results": search_kb(query)})
        return self.send_json({"error": "unknown api"}, 404)

    def api_login(self, body):
        username = body.get("username", "")
        password = body.get("password", "")
        with db() as conn:
            row = conn.execute("select * from users where username=? and password=?", (username, password)).fetchone()
        if not row:
            audit(username, "-", "登录失败", "账号或密码错误")
            return self.send_json({"error": "账号或密码错误"}, 401)
        token = uuid.uuid4().hex
        SESSIONS[token] = username
        user = row_to_dict(row)
        audit(username, user["role"], "登录", "登录成功")
        return self.send_json({"token": token, "user": user, "role_label": ROLE_LABELS.get(user["role"])})

    def api_save_config(self, body):
        cfg = read_json(CONFIG_PATH, DEFAULT_CONFIG)
        for key in ["deepseek_api_key", "deepseek_base_url", "deepseek_model", "enable_web_fallback"]:
            if key in body:
                cfg[key] = body[key]
        write_json(CONFIG_PATH, cfg)
        return self.send_json({"ok": True, "has_api_key": bool(cfg.get("deepseek_api_key"))})

    def api_dashboard(self, user):
        emp = get_employee(user["employee_id"])
        with db() as conn:
            ticket_count = conn.execute("select count(*) from tickets").fetchone()[0]
            my_ticket_count = conn.execute("select count(*) from tickets where creator_employee_id=?", (user["employee_id"],)).fetchone()[0]
        modules = [
            {"id": "employee-relations", "name": "员工关系", "status": "已实现", "desc": "问答、证明、查询、工单、转人工。"},
            {"id": "recruiting", "name": "招聘", "status": "入口展示", "desc": "后续扩展候选人匹配、初筛、邀约。"},
            {"id": "payroll", "name": "薪酬", "status": "入口展示", "desc": "Demo仅提供本人模拟工资查询。"},
            {"id": "benefits", "name": "福利", "status": "入口展示", "desc": "后续接福利政策和供应商系统。"},
            {"id": "performance", "name": "绩效考核", "status": "入口展示", "desc": "敏感模块，AI只做流程辅助。"},
            {"id": "training", "name": "培训", "status": "入口展示", "desc": "后续接课程、报名、记录。"},
        ]
        return self.send_json({"employee": emp, "modules": modules, "ticket_count": ticket_count, "my_ticket_count": my_ticket_count})

    def api_salary(self, user):
        emp = get_employee(user["employee_id"])
        if not emp:
            return self.send_json({"error": "没有员工档案"}, 404)
        if user["role"] not in ("employee", "manager", "hr", "admin"):
            return self.send_json({"error": "无权限"}, 403)
        detail = {
            "employee": emp["name"],
            "month": datetime.now().strftime("%Y-%m"),
            "base_salary": emp["base_salary"],
            "bonus": emp["bonus"],
            "social_security_deduction": round(emp["base_salary"] * 0.105, 2),
            "housing_fund_deduction": round(emp["base_salary"] * 0.07, 2),
            "tax_estimate": round(max(emp["base_salary"] + emp["bonus"] - 5000, 0) * 0.08, 2),
        }
        detail["net_salary_demo"] = round(detail["base_salary"] + detail["bonus"] - detail["social_security_deduction"] - detail["housing_fund_deduction"] - detail["tax_estimate"], 2)
        audit(user["username"], user["role"], "查询工资", f"查询 {emp['employee_id']} 模拟工资")
        return self.send_json({"salary": detail, "notice": "这是Demo模拟数据，不代表真实工资。"})

    def api_social(self, user):
        emp = get_employee(user["employee_id"])
        if not emp:
            return self.send_json({"error": "没有员工档案"}, 404)
        audit(user["username"], user["role"], "查询社保公积金", emp["employee_id"])
        return self.send_json({
            "social": {
                "employee": emp["name"],
                "city": emp["city"],
                "social_status": emp["social_status"],
                "housing_fund_status": emp["housing_fund_status"],
                "notice": "Demo仅展示状态；正式环境需接薪酬/社保/公积金系统并按地区政策解释。",
            }
        })

    def api_tickets(self, user):
        with db() as conn:
            if user["role"] in ("hr", "admin"):
                rows = conn.execute("select * from tickets order by created_at desc").fetchall()
            else:
                rows = conn.execute("select * from tickets where creator_employee_id=? order by created_at desc", (user["employee_id"],)).fetchall()
        return self.send_json({"tickets": [row_to_dict(r) for r in rows]})

    def api_create_ticket(self, user, body):
        category = body.get("category", "员工咨询")
        title = body.get("title", "").strip() or "未命名工单"
        desc = body.get("description", "").strip()
        priority = body.get("priority", "普通")
        sensitive = any(x in (category + title + desc) for x in ["投诉", "劳动争议", "仲裁", "情绪", "工资异议", "绩效争议"])
        status = "待人工处理" if sensitive else "已创建"
        assigned = "员工关系HR" if sensitive else "HR服务台"
        ticket_id = "T" + datetime.now().strftime("%Y%m%d%H%M%S") + uuid.uuid4().hex[:4]
        with db() as conn:
            conn.execute(
                """
                insert into tickets(id,creator_employee_id,category,title,description,status,priority,assigned_to,created_at,updated_at)
                values(?,?,?,?,?,?,?,?,?,?)
                """,
                (ticket_id, user["employee_id"], category, title, desc, status, priority, assigned, now_text(), now_text()),
            )
        audit(user["username"], user["role"], "创建工单", f"{ticket_id} {category} {title}")
        return self.send_json({"ticket": {"id": ticket_id, "status": status, "assigned_to": assigned}})

    def api_connectors(self, user):
        if user["role"] not in ("hr", "admin", "manager"):
            return self.send_json({"error": "接入中心仅HR/主管/管理员可查看"}, 403)
        with db() as conn:
            rows = conn.execute("select * from connectors order by id").fetchall()
        explainer = {
            "REST API": "系统之间通过HTTP接口交换JSON数据，适合有现代接口的员工系统、薪酬系统、工单系统。",
            "Webhook": "外部系统发生事件后主动通知本系统，适合工单状态变化、审批完成等场景。",
            "数据库连接": "直接读写数据库，正式环境需严格权限和审计，Demo不建议直连真实库。",
            "RPA Adapter": "没有接口的老系统，由RPA模拟人工点击、查询、录入。",
            "文件导入": "通过Excel/CSV/Word/PDF导入数据或知识库，适合Demo和初期验证。",
        }
        return self.send_json({"connectors": [row_to_dict(r) for r in rows], "explainer": explainer})

    def api_test_connector(self, user, body):
        if user["role"] not in ("hr", "admin"):
            return self.send_json({"error": "只有HR或管理员可以测试连接"}, 403)
        connector_id = body.get("id")
        with db() as conn:
            row = conn.execute("select * from connectors where id=?", (connector_id,)).fetchone()
        if not row:
            return self.send_json({"error": "连接器不存在"}, 404)
        audit(user["username"], user["role"], "测试连接器", connector_id)
        return self.send_json({
            "ok": True,
            "message": f"{row['name']} 测试通过（Demo模拟）。正式环境需要真实地址、认证、字段映射和错误处理。",
            "connector": row_to_dict(row),
        })

    def api_audit(self, user):
        if user["role"] not in ("hr", "admin"):
            return self.send_json({"error": "无权查看审计日志"}, 403)
        with db() as conn:
            rows = conn.execute("select * from audit_logs order by id desc limit 100").fetchall()
        return self.send_json({"logs": [row_to_dict(r) for r in rows]})

    def api_chat(self, user, body):
        message = body.get("message", "").strip()
        if not message:
            return self.send_json({"error": "请输入问题"}, 400)
        intent = classify_intent(message)
        kb_results = search_kb(message)
        web_results = []
        if not kb_results or (kb_results and kb_results[0]["score"] < 3):
            web_results = web_search(message)
        tool_context = build_tool_context(intent, user)
        deepseek_answer = call_deepseek(message, kb_results, web_results, user, tool_context, intent)
        local = local_answer(message, intent, kb_results, web_results, user, tool_context)
        answer = deepseek_answer if deepseek_answer else local["answer"]
        process = local["process"]
        if deepseek_answer:
            process.append("调用大模型：DeepSeek API")
        else:
            process.append("调用大模型：未配置API，使用本地规则回答")
        if intent == "risk_escalation":
            process.append("安全判断：建议转人工")
        audit(user["username"], user["role"], "智能体问答", f"{intent}: {message[:120]}")
        return self.send_json({
            "answer": answer,
            "intent": intent,
            "process": process,
            "sources": kb_results,
            "web_results": web_results,
            "escalate": local["escalate"],
        })

    def api_certificate(self, user, body):
        cert_type = body.get("type", "employment")
        purpose = body.get("purpose", "")
        if user["role"] not in ("employee", "manager", "hr", "admin"):
            return self.send_json({"error": "无权限生成证明"}, 403)
        result = generate_certificate(user, cert_type, purpose)
        return self.send_json({"ok": True, "document": result})


def main():
    ensure_dirs()
    if not CONFIG_PATH.exists():
        write_json(CONFIG_PATH, DEFAULT_CONFIG)
    init_db()
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"{APP_NAME} Demo running at http://127.0.0.1:{PORT}")
    print("Demo accounts: employee01/123456, manager01/123456, hr01/123456, admin/admin123")
    server.serve_forever()


if __name__ == "__main__":
    main()
