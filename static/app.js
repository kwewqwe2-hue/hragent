const state = {
  token: localStorage.getItem("hragent_token") || "",
  user: null,
  roleLabel: "",
  page: "home",
  chat: [],
};

const navItems = [
  ["home", "首页"],
  ["chat", "智能体对话"],
  ["documents", "文档生成"],
  ["salary", "工资查询"],
  ["social", "社保/公积金"],
  ["flow", "入职/离职流程"],
  ["tickets", "工单处理"],
  ["knowledge", "知识库"],
  ["connectors", "接入中心"],
  ["audit", "权限记录"],
  ["future", "其他HR模块"],
  ["settings", "模型设置"],
];

function $(id) { return document.getElementById(id); }

async function api(path, options = {}) {
  const headers = options.headers || {};
  headers["Content-Type"] = "application/json";
  if (state.token) headers["Authorization"] = `Bearer ${state.token}`;
  const res = await fetch(path, { ...options, headers });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `请求失败 ${res.status}`);
  return data;
}

function escapeHtml(text) {
  return String(text ?? "").replace(/[&<>"']/g, s => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;"
  }[s]));
}

function setTitle(title, subtitle = "") {
  $("pageTitle").textContent = title;
  $("pageSubtitle").textContent = subtitle || "员工关系智能体 Demo";
}

function renderNav() {
  const nav = $("nav");
  nav.innerHTML = "";
  for (const [id, label] of navItems) {
    const btn = document.createElement("button");
    btn.className = `nav-btn ${state.page === id ? "active" : ""}`;
    btn.textContent = label;
    btn.onclick = () => {
      state.page = id;
      render();
    };
    nav.appendChild(btn);
  }
}

async function bootstrap() {
  if (!state.token) return;
  try {
    const data = await api("/api/me");
    state.user = data.user;
    state.roleLabel = data.role_label;
    $("loginView").classList.add("hidden");
    $("appView").classList.remove("hidden");
    $("roleLabel").textContent = state.roleLabel;
    $("userChip").textContent = `${data.employee?.name || data.user.display_name} · ${state.roleLabel}`;
    render();
  } catch (err) {
    localStorage.removeItem("hragent_token");
    state.token = "";
  }
}

async function login() {
  $("loginError").textContent = "";
  try {
    const data = await api("/api/login", {
      method: "POST",
      body: JSON.stringify({ username: $("username").value, password: $("password").value }),
    });
    state.token = data.token;
    state.user = data.user;
    state.roleLabel = data.role_label;
    localStorage.setItem("hragent_token", state.token);
    $("loginView").classList.add("hidden");
    $("appView").classList.remove("hidden");
    $("roleLabel").textContent = state.roleLabel;
    await bootstrap();
  } catch (err) {
    $("loginError").textContent = err.message;
  }
}

function logout() {
  localStorage.removeItem("hragent_token");
  state.token = "";
  state.user = null;
  $("appView").classList.add("hidden");
  $("loginView").classList.remove("hidden");
}

async function render() {
  renderNav();
  const handlers = {
    home: renderHome,
    chat: renderChat,
    documents: renderDocuments,
    salary: renderSalary,
    social: renderSocial,
    flow: renderFlow,
    tickets: renderTickets,
    knowledge: renderKnowledge,
    connectors: renderConnectors,
    audit: renderAudit,
    future: renderFuture,
    settings: renderSettings,
  };
  await (handlers[state.page] || renderHome)();
}

async function renderHome() {
  setTitle("首页", "员工关系智能体 Demo 总览");
  const data = await api("/api/dashboard");
  const emp = data.employee || {};
  $("content").innerHTML = `
    <div class="grid">
      <div class="card"><h3>当前员工</h3><p>${escapeHtml(emp.name)} · ${escapeHtml(emp.department)}</p><div class="stat">${escapeHtml(emp.city || "-")}</div></div>
      <div class="card"><h3>我的工单</h3><p>当前账号可见工单数量</p><div class="stat">${data.my_ticket_count}</div></div>
      <div class="card"><h3>系统工单</h3><p>HR/管理员可查看全量</p><div class="stat">${data.ticket_count}</div></div>
    </div>
    <h3 class="section-title">HR模块</h3>
    <div class="grid two">
      ${data.modules.map(m => `
        <div class="card">
          <h3>${escapeHtml(m.name)} <span class="tag ${m.status === "已实现" ? "ok" : "warn"}">${escapeHtml(m.status)}</span></h3>
          <p>${escapeHtml(m.desc)}</p>
        </div>`).join("")}
    </div>
  `;
}

async function renderChat() {
  setTitle("智能体对话", "基于知识库、模拟员工数据、DeepSeek API和联网兜底回答");
  $("content").innerHTML = `
    <div class="chat-layout">
      <div class="card">
        <div id="chatBox" class="chat-box"></div>
        <div class="chat-input">
          <textarea id="chatInput" placeholder="例如：我想开在职证明 / 我的工资为什么少了 / 上海员工离职流程是什么 / 我想投诉主管"></textarea>
          <button class="primary-btn" id="sendChatBtn">发送</button>
        </div>
      </div>
      <div class="card">
        <h3>说明</h3>
        <p>系统会展示可公开的处理步骤：意图识别、知识库检索、是否联网、是否建议转人工。不会展示模型隐藏推理链。</p>
        <p>涉及投诉、劳动争议、薪酬异议、情绪风险时，AI只做辅助说明并建议转人工。</p>
        <div class="actions">
          <button class="secondary-btn quick-q">我要开在职证明</button>
          <button class="secondary-btn quick-q">我的社保公积金怎么查？</button>
          <button class="secondary-btn quick-q">我想投诉主管</button>
        </div>
      </div>
    </div>`;
  $("sendChatBtn").onclick = sendChat;
  document.querySelectorAll(".quick-q").forEach(btn => btn.onclick = () => {
    $("chatInput").value = btn.textContent;
    sendChat();
  });
  drawChat();
}

function drawChat() {
  const box = $("chatBox");
  if (!box) return;
  box.innerHTML = state.chat.length ? state.chat.map(item => {
    if (item.role === "user") return `<div class="bubble user">${escapeHtml(item.text)}</div>`;
    const sources = item.sources?.length ? `<h3>依据</h3><ul class="process-list">${item.sources.map(s => `<li>${escapeHtml(s.title)}：${escapeHtml(s.snippet)}</li>`).join("")}</ul>` : "";
    const web = item.web?.length ? `<h3>联网补充</h3><ul class="process-list">${item.web.map(s => `<li>${escapeHtml(s.title)} ${s.url ? `<a href="${escapeHtml(s.url)}" target="_blank">打开</a>` : ""}</li>`).join("")}</ul>` : "";
    return `<div class="bubble ai">
      ${escapeHtml(item.text)}
      ${item.escalate ? `<p><span class="tag danger">建议转人工</span></p>` : ""}
      <h3>处理步骤</h3>
      <ul class="process-list">${(item.process || []).map(p => `<li>${escapeHtml(p)}</li>`).join("")}</ul>
      ${sources}${web}
    </div>`;
  }).join("") : `<div class="bubble ai">你好，我是HR智能体。你可以问员工关系、证明开具、工资查询、社保公积金、入职离职流程、工单和投诉转人工相关问题。</div>`;
  box.scrollTop = box.scrollHeight;
}

async function sendChat() {
  const msg = $("chatInput").value.trim();
  if (!msg) return;
  state.chat.push({ role: "user", text: msg });
  $("chatInput").value = "";
  drawChat();
  try {
    const data = await api("/api/chat", { method: "POST", body: JSON.stringify({ message: msg }) });
    state.chat.push({ role: "ai", text: data.answer, process: data.process, sources: data.sources, web: data.web_results, escalate: data.escalate });
  } catch (err) {
    state.chat.push({ role: "ai", text: `请求失败：${err.message}`, process: [] });
  }
  drawChat();
}

async function renderDocuments() {
  setTitle("文档生成", "根据员工模拟数据生成证明类文档");
  $("content").innerHTML = `
    <div class="grid two">
      <div class="card">
        <h3>生成证明</h3>
        <label>证明类型</label>
        <select id="certType">
          <option value="employment">在职证明</option>
          <option value="income">收入证明</option>
          <option value="resignation">离职证明</option>
          <option value="training">培训证明</option>
        </select>
        <label>用途</label>
        <input id="certPurpose" placeholder="例如：办理签证 / 贷款 / 子女入学" />
        <div class="actions"><button class="primary-btn" id="genCertBtn">生成文档</button></div>
        <p id="certResult" class="muted"></p>
      </div>
      <div class="card">
        <h3>规则</h3>
        <p>Demo生成的是Word可打开的HTML文档，仅用于演示。正式系统中，对外证明、盖章文件、收入类文件应进入HR审核或审批流程。</p>
      </div>
    </div>`;
  $("genCertBtn").onclick = async () => {
    const data = await api("/api/certificates", {
      method: "POST",
      body: JSON.stringify({ type: $("certType").value, purpose: $("certPurpose").value }),
    });
    $("certResult").innerHTML = `已生成：<a href="${data.document.download_url}" target="_blank">${escapeHtml(data.document.title)}</a>`;
  };
}

async function renderSalary() {
  setTitle("工资查询", "敏感数据：Demo只展示当前登录员工模拟数据");
  try {
    const data = await api("/api/salary");
    const s = data.salary;
    $("content").innerHTML = `
      <div class="card">
        <h3>${escapeHtml(s.employee)} · ${escapeHtml(s.month)} 工资示例</h3>
        <table>
          <tr><th>项目</th><th>金额</th></tr>
          <tr><td>基本工资</td><td>${s.base_salary}</td></tr>
          <tr><td>奖金</td><td>${s.bonus}</td></tr>
          <tr><td>社保扣款估算</td><td>${s.social_security_deduction}</td></tr>
          <tr><td>公积金扣款估算</td><td>${s.housing_fund_deduction}</td></tr>
          <tr><td>个税估算</td><td>${s.tax_estimate}</td></tr>
          <tr><td><strong>实发工资Demo</strong></td><td><strong>${s.net_salary_demo}</strong></td></tr>
        </table>
        <p class="muted">${escapeHtml(data.notice)}</p>
      </div>`;
  } catch (err) {
    $("content").innerHTML = `<div class="card"><p class="error">${escapeHtml(err.message)}</p></div>`;
  }
}

async function renderSocial() {
  setTitle("社保/公积金查询", "按员工地区展示模拟状态");
  const data = await api("/api/social-fund");
  const s = data.social;
  $("content").innerHTML = `
    <div class="card">
      <h3>${escapeHtml(s.employee)} · ${escapeHtml(s.city)}</h3>
      <table>
        <tr><th>项目</th><th>状态</th></tr>
        <tr><td>社保</td><td>${escapeHtml(s.social_status)}</td></tr>
        <tr><td>公积金</td><td>${escapeHtml(s.housing_fund_status)}</td></tr>
      </table>
      <p class="muted">${escapeHtml(s.notice)}</p>
    </div>`;
}

async function renderFlow() {
  setTitle("入职/离职流程", "员工关系高频流程展示");
  $("content").innerHTML = `
    <div class="grid two">
      <div class="card"><h3>入职流程</h3><ol class="process-list">
        <li>确认录用信息和合同主体</li><li>收集身份证明、银行卡、学历等材料</li>
        <li>签署合同与保密/合规文件</li><li>创建员工档案和账号</li>
        <li>确认社保、公积金、考勤和培训安排</li></ol></div>
      <div class="card"><h3>离职流程</h3><ol class="process-list">
        <li>提交离职申请或确认离职原因</li><li>审批与工作交接</li>
        <li>薪酬结算、社保公积金处理</li><li>归还资产、关闭账号</li>
        <li>开具离职证明；争议场景转人工处理</li></ol></div>
    </div>`;
}

async function renderTickets() {
  setTitle("工单处理", "创建、查看、转人工处理员工关系问题");
  const data = await api("/api/tickets");
  $("content").innerHTML = `
    <div class="grid two">
      <div class="card">
        <h3>创建工单</h3>
        <label>类别</label>
        <select id="ticketCategory">
          <option>员工咨询</option><option>证明开具</option><option>薪酬异议</option>
          <option>社保公积金</option><option>投诉</option><option>劳动争议</option><option>情绪风险</option>
        </select>
        <label>标题</label><input id="ticketTitle" />
        <label>描述</label><textarea id="ticketDesc"></textarea>
        <label>优先级</label><select id="ticketPriority"><option>普通</option><option>高</option><option>紧急</option></select>
        <div class="actions"><button class="primary-btn" id="createTicketBtn">创建工单</button></div>
        <p id="ticketResult" class="muted"></p>
      </div>
      <div class="card">
        <h3>工单列表</h3>
        ${ticketTable(data.tickets)}
      </div>
    </div>`;
  $("createTicketBtn").onclick = async () => {
    const res = await api("/api/tickets", {
      method: "POST",
      body: JSON.stringify({
        category: $("ticketCategory").value,
        title: $("ticketTitle").value,
        description: $("ticketDesc").value,
        priority: $("ticketPriority").value,
      }),
    });
    $("ticketResult").textContent = `已创建 ${res.ticket.id}，状态：${res.ticket.status}，处理人：${res.ticket.assigned_to}`;
    setTimeout(renderTickets, 500);
  };
}

function ticketTable(tickets) {
  if (!tickets.length) return `<p class="muted">暂无工单</p>`;
  return `<table><tr><th>编号</th><th>类别</th><th>标题</th><th>状态</th><th>处理人</th></tr>
    ${tickets.map(t => `<tr><td>${escapeHtml(t.id)}</td><td>${escapeHtml(t.category)}</td><td>${escapeHtml(t.title)}</td><td>${escapeHtml(t.status)}</td><td>${escapeHtml(t.assigned_to)}</td></tr>`).join("")}</table>`;
}

async function renderKnowledge() {
  setTitle("知识库", "本地Demo样例知识库；正式环境替换为外服内网知识库");
  const data = await api("/api/knowledge");
  $("content").innerHTML = `
    <div class="card">
      <h3>知识库文件</h3>
      <p class="muted">这些是Demo样例资料，不是正式政策或外服内部制度。</p>
      <table><tr><th>文档</th><th>字符数</th></tr>
      ${data.docs.map(d => `<tr><td>${escapeHtml(d.title)}</td><td>${d.chars}</td></tr>`).join("")}</table>
    </div>`;
}

async function renderConnectors() {
  setTitle("接入中心", "为未来员工系统、薪酬系统、工单系统、知识库和RPA预留通用接入插头");
  try {
    const data = await api("/api/connectors");
    $("content").innerHTML = `
      <div class="card">
        <h3>接口类型说明</h3>
        <table><tr><th>类型</th><th>说明</th></tr>
        ${Object.entries(data.explainer).map(([k,v]) => `<tr><td>${escapeHtml(k)}</td><td>${escapeHtml(v)}</td></tr>`).join("")}</table>
      </div>
      <h3 class="section-title">连接器</h3>
      <div class="grid two">
      ${data.connectors.map(c => `<div class="card">
        <h3>${escapeHtml(c.name)} <span class="tag ${c.status.includes("可用") ? "ok" : "warn"}">${escapeHtml(c.status)}</span></h3>
        <p>${escapeHtml(c.description)}</p>
        <p class="muted">${escapeHtml(c.connector_type)} · ${escapeHtml(c.auth_type)}</p>
        <p class="muted">${escapeHtml(c.endpoint)}</p>
        <button class="secondary-btn test-connector" data-id="${escapeHtml(c.id)}">测试连接</button>
      </div>`).join("")}
      </div>`;
    document.querySelectorAll(".test-connector").forEach(btn => btn.onclick = async () => {
      const res = await api("/api/connectors/test", { method: "POST", body: JSON.stringify({ id: btn.dataset.id }) });
      alert(res.message);
    });
  } catch (err) {
    $("content").innerHTML = `<div class="card"><p class="error">${escapeHtml(err.message)}</p><p class="muted">普通员工看不到接入中心，请切换 HR 或管理员账号。</p></div>`;
  }
}

async function renderAudit() {
  setTitle("权限记录", "关键操作审计日志");
  try {
    const data = await api("/api/audit");
    $("content").innerHTML = `<div class="card"><h3>最近100条日志</h3>
      <table><tr><th>时间</th><th>账号</th><th>角色</th><th>动作</th><th>详情</th></tr>
      ${data.logs.map(l => `<tr><td>${escapeHtml(l.created_at)}</td><td>${escapeHtml(l.actor)}</td><td>${escapeHtml(l.role)}</td><td>${escapeHtml(l.action)}</td><td>${escapeHtml(l.detail)}</td></tr>`).join("")}</table></div>`;
  } catch (err) {
    $("content").innerHTML = `<div class="card"><p class="error">${escapeHtml(err.message)}</p><p class="muted">审计日志仅HR/管理员可查看。</p></div>`;
  }
}

async function renderFuture() {
  setTitle("其他HR模块", "招聘、薪酬、福利、绩效、培训入口展示");
  $("content").innerHTML = `
    <div class="grid">
      ${[
        ["招聘", "后续可扩展候选人匹配、主动触达、初筛、岗位推荐。"],
        ["薪酬", "正式环境需接薪酬系统；AI只做解释和工单分流，不做薪酬决定。"],
        ["福利", "接福利政策、供应商系统、员工福利申请。"],
        ["绩效考核", "敏感模块，AI只做流程提醒和材料辅助，不做评价结论。"],
        ["培训", "接课程库、报名系统、培训记录和培训证明。"],
      ].map(([name, desc]) => `<div class="card"><h3>${name} <span class="tag warn">入口展示</span></h3><p>${desc}</p></div>`).join("")}
    </div>`;
}

async function renderSettings() {
  setTitle("模型设置", "填写 DeepSeek API Key 后可调用外网大模型");
  const cfg = await api("/api/config");
  $("content").innerHTML = `
    <div class="grid two">
      <div class="card">
        <h3>DeepSeek 配置</h3>
        <label>API Key</label><input id="apiKey" type="password" placeholder="${cfg.has_api_key ? "已保存，如需替换请重新输入" : "请输入你的DeepSeek API Key"}" />
        <label>Base URL</label><input id="baseUrl" value="${escapeHtml(cfg.deepseek_base_url || "https://api.deepseek.com")}" />
        <label>模型</label><input id="model" value="${escapeHtml(cfg.deepseek_model || "deepseek-chat")}" />
        <label><input id="webFallback" type="checkbox" ${cfg.enable_web_fallback ? "checked" : ""} style="width:auto" /> 知识库未命中时尝试联网检索</label>
        <div class="actions"><button class="primary-btn" id="saveConfigBtn">保存配置</button></div>
        <p id="configResult" class="muted"></p>
      </div>
      <div class="card">
        <h3>说明</h3>
        <p>API Key 保存在本机 data/config.json。Demo阶段方便演示，正式产品应使用系统密钥管理或加密存储。</p>
        <p>如果不填写API Key，系统仍会用本地知识库和规则回答。</p>
      </div>
    </div>`;
  $("saveConfigBtn").onclick = async () => {
    const body = {
      deepseek_base_url: $("baseUrl").value,
      deepseek_model: $("model").value,
      enable_web_fallback: $("webFallback").checked,
    };
    if ($("apiKey").value.trim()) body.deepseek_api_key = $("apiKey").value.trim();
    const res = await api("/api/config", { method: "POST", body: JSON.stringify(body) });
    $("configResult").textContent = `已保存。API Key状态：${res.has_api_key ? "已配置" : "未配置"}`;
  };
}

$("loginBtn").onclick = login;
$("logoutBtn").onclick = logout;
bootstrap();
