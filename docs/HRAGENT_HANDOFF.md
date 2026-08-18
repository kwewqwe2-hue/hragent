# HRAgent 项目交接与运行手册

更新时间：2026-07-31

本文记录当前实际运行状态。它面向演示人员和后续开发者，不包含任何真实 API Key、密码密钥或钉钉凭据。

## 1. 当前结论

HRAgent 是运行在 Windows + Docker Desktop 上的单企业 HR SaaS Demo。当前核心闭环是：

1. SaaS 管理组织、员工、直属主管、假期余额和请假单。
2. 员工可以从独立 AI 网页或钉钉机器人发起查询、文件分析和请假。
3. n8n 使用 DeepSeek 理解意图，并调用 SaaS 的真实接口，不把聊天记忆当作业务数据。
4. 主管在钉钉或 SaaS 处理申请，SaaS 自动完成 HR 备案并通知员工。
5. SaaS 知识库页面通过 n8n 将文件索引到 Qdrant；DeepSeek 根据检索内容回答并标注来源。
6. 图片由本地 RapidOCR 服务识别；PDF、DOCX 和文本文件由本地解析链路处理。

本项目已经具备完整本地演示能力，但还不是生产系统。Quick Tunnel、多租户向量隔离、数据库迁移、正式对象存储、联网搜索和工资查询仍需后续建设。

## 2. 项目位置

| 模块 | 绝对路径 | 用途 |
| --- | --- | --- |
| SaaS 主项目 | `E:\wk\hragent\hragentv1` | Docker Compose、Spring Boot 后端、Vue 管理端 |
| SaaS 后端 | `E:\wk\hragent\hragentv1\backend` | 业务、权限、MySQL 实体和 n8n 集成接口 |
| SaaS 管理端 | `E:\wk\hragent\hragentv1\frontend` | 企业后台网页 |
| 独立 AI 网页 | `E:\wk\hragent\hragent-chat` | 使用 SaaS 账号登录的聊天端源码 |
| n8n 项目 | `E:\wk\n8nwork` | n8n、PostgreSQL、Qdrant、Ollama、OCR、文件解析和隧道 |
| n8n 工作流导出 | `E:\wk\n8nwork\workflows` | 可重新导入的工作流 JSON |
| 一键启动脚本 | `E:\wk\start-hragent-local.ps1` | 启动整套服务并同步 Quick Tunnel |
| 隧道刷新脚本 | `E:\wk\refresh-hragent-tunnel.ps1` | 获取或新建隧道并更新本地 n8n 配置 |
| 健康检查脚本 | `E:\wk\check-hragent-local.ps1` | 只检查容器、接口、隧道、密钥和工作流 |
| 只读验收脚本 | `E:\wk\run-hragent-acceptance.ps1` | 验证请假边界和权限，不创建真实请假单 |
| 本轮审计备份 | `E:\wk\backups\20260731-audit-fix` | 2026-07-31 修复前后相关文件备份 |
| 本交接文档 | `E:\wk\HRAGENT_HANDOFF.md` | 当前项目的首要入口 |

## 3. 架构和数据边界

```text
SaaS 管理端 :5173          独立 AI 网页 :5174
       |                         |
       +------ Spring Boot :8080-+
                    |
              MySQL + Redis
                    |
                    +------ n8n :5678 ------ DeepSeek
                              |   |   |
                              |   |   +------ RapidOCR
                              |   +---------- 文件解析服务
                              +-------------- Qdrant + Ollama bge-m3
                                      |
                               钉钉开放 API
                                      |
                             Cloudflare Quick Tunnel
```

关键边界：

- SaaS 后端是员工身份、组织关系、余额、请假单、审批和通知的唯一真实数据源。
- n8n 负责对话编排、RAG 检索、文件解析和工具调用，不直接决定用户权限。
- 员工和主管权限由 SaaS 接口再次校验，不能依赖提示词防越权。
- 网页聊天使用 SaaS 登录态签发的 HMAC 身份；浏览器不能自行提交工号冒充他人。
- 网页附件以及 RAG 搜索/文件目录接口通过 SaaS 与 n8n 共享的内部密钥保护，该密钥已随机化且两端一致。
- Qdrant 保存知识向量，Ollama 的 `bge-m3` 只负责本地向量化；聊天模型仍是 DeepSeek。

## 4. 服务、端口和账号

| 服务 | 地址/端口 |
| --- | --- |
| SaaS 管理端 | `http://localhost:5173` |
| 独立 AI 网页 | `http://localhost:5174` |
| SaaS API | `http://localhost:8080/api` |
| 后端健康接口 | `http://localhost:8080/api/health` |
| n8n 编辑器 | `http://localhost:5678` |
| Qdrant | `http://localhost:6333` |
| Ollama | `http://localhost:11434` |
| MySQL 宿主机端口 | `localhost:3307` |
| Redis | `localhost:6379` |

默认演示密码均为 `123456`：

| 角色 | 用户名 | 说明 |
| --- | --- | --- |
| 员工 | `zhangsan` | 张三，请假发起人 |
| 主管 | `lisi` | 李四，张三的直属主管 |
| 空间管理员/HR | `wanghr` | 企业空间和 HR 管理 |
| 平台管理员 | `platformadmin` | 查看企业级汇总和运行数据 |

演示环境也应尽快修改默认密码。不要在文档或截图中公开 DeepSeek、钉钉和 n8n 凭据。

## 5. 当前已完成功能

### 5.1 SaaS 和权限

- 登录、注册、退出、个人资料、头像和修改密码。
- 企业空间、空间码、加入申请、退出企业和成员管理。
- 部门、岗位、员工档案、工号、直属主管和钉钉用户 ID 绑定。
- 员工、主管、空间管理员/HR、平台管理员四类角色视图。
- 员工只能查询自己的数据；主管可查看组织内员工并处理直属待办；空间管理员管理本企业；平台管理员只看企业级汇总。
- CSV/XLSX 员工和假期余额导入、预览、校验、记录，以及平台内手工维护。
- API Key、接口日志、操作日志、智能体调用记录和空间级汇总。

### 5.2 请假闭环

- 假别：年假、病假、事假、婚假。
- 流程：员工提交 -> 直属主管同意/拒绝 -> SaaS 自动 HR 备案 -> 结果通知员工。
- 创建请假前必须二次确认。
- 周六、周日默认休息，不计入消耗天数；纯周末申请会被拒绝。
- 余额不足、重复日期、重复审批、无效身份、越权访问均由后端拒绝。
- 已批准日期会出现在员工真实日历和轨迹中。
- SaaS 和钉钉的审批读取同一张请假单，重复点击不会重复扣除余额。

### 5.3 AI 网页和钉钉

- 两个入口使用同一套 n8n 智能体能力和同一 SaaS 数据源。
- 普通问答、余额查询、请假预检、创建请假、状态查询和主管审批。
- SaaS 通知发件箱由 n8n 定时派发，可主动通知主管和员工。
- 钉钉审批卡片支持同意和拒绝。
- AI 网页支持新对话、快捷问题、Markdown 表格美化和附件上传。
- DeepSeek、钉钉、SaaS 或子工作流异常会进入统一错误工作流，不应无提示沉默。

### 5.4 RAG、文件和 OCR

- SaaS 已有知识库页面，文件管理操作调用 n8n，向量数据保存在 Qdrant 集合 `hragent_knowledge`。
- n8n 负责导入、重新索引、删除和搜索；SaaS 记录与向量记录联动。
- 智能体会对公司内部缩写、陌生术语和指定文档内容强制检索 RAG；支持列出知识库文件及精确判断指定文件是否存在。
- Ollama `bge-m3` 在本地生成向量，DeepSeek 使用检索结果回答。
- 命中时返回来源文件名；没有命中时明确说明“当前知识库没有相关制度”，不能编造公司制度。
- 已实测知识库命中和无命中闭环，当前 Qdrant 集合状态为绿色。
- 已实测聊天附件：TXT、PDF、DOCX、PNG；图片走 RapidOCR。
- 知识库导入链路已实测 TXT、PDF、XLSX，工作流还支持 CSV。
- 当前图片 OCR 支持 JPG/JPEG/PNG；扫描型 PDF 需要先转为图片，不能承诺直接识别所有扫描 PDF。
- “临时分析附件”和“导入公司知识库”是两个不同操作，临时附件不会自动成为公司制度。

## 6. n8n 正式工作流

n8n 当前版本：`2.29.11`。以下 8 条工作流在 n8n 数据库中均为 Active：

| ID | 名称 | 用途 |
| --- | --- | --- |
| `LLWdzAOEECp9eSIf` | `HRAgent-02-钉钉机器人` | 钉钉入口、网页聊天、DeepSeek 和 SaaS 工具编排 |
| `3RiI6nH28eRUuOaz` | `HRAgent-03-钉钉文件解析` | 钉钉与网页附件解析、OCR 子流程 |
| `b83719c3-7b65-4d8a-9c48-6f5fa4b7f421` | `HRAgent-06-DingTalk-Notification-Dispatcher` | 派发 SaaS 通知发件箱 |
| `c8d9e0f1-2a3b-4c5d-6e7f-8a9b0c1d2e3f` | `HRAgent-07-DingTalk-Leave-Card-Action` | 钉钉审批卡片动作 |
| `e8f1a4c2-7b90-4d35-9c61-2a5e8f0b3d17` | `HRAgent-08-Error-Handler` | 统一错误记录 |
| `9f6f1e91-1d0e-4f5c-8fb5-7c2f4f3d9a01` | `HRAgent-RAG-Admin` | 知识库管理操作 |
| `d5e6f7a8-9012-4b3c-8d5e-6f708192a3b4` | `HRAgent-RAG-Ingest` | 文件提取、切分、向量化和写入 |
| `c4e6a8b0-2d1f-4c93-8e75-1a6b9d0f2c34` | `HRAgent-RAG-Search` | 向量检索和来源整理 |

除统一错误处理本身外，其余 7 条工作流均绑定 `HRAgent-08-Error-Handler`。

`HRAgent-RAG-Official-2335-Baseline.json` 是保留的官方模板参考，不属于上述 8 条运行必需工作流。工作流导出 JSON 中的 `active` 字段可能与数据库当前状态不同，运行状态以 n8n 页面、数据库和健康脚本为准。

## 7. 一键启动

先启动 Docker Desktop，等待 Docker Engine 正常，再打开 PowerShell：

```powershell
cd E:\wk
powershell -ExecutionPolicy Bypass -File .\start-hragent-local.ps1
```

修改 Java、Vue、Dockerfile 或依赖后需要重新构建：

```powershell
cd E:\wk
powershell -ExecutionPolicy Bypass -File .\start-hragent-local.ps1 -Build
```

脚本会：

1. 检查并在需要时启动 Docker Desktop。
2. 检查 SaaS 与 n8n 的内部密钥是否存在且一致。
3. 启动 n8n 及其依赖、SaaS、独立 AI 网页和 Quick Tunnel。
4. 获取当前临时域名，更新 n8n 的 `WEBHOOK_URL` 与审批地址。
5. 重建 n8n 使新域名生效。
6. 运行健康检查并输出钉钉应填写的两个地址。

脚本默认在结束时等待按 Enter。被其他脚本调用时可增加 `-NoPause`。

## 8. 手动启动和停止

不使用一键脚本时，按以下顺序启动：

```powershell
cd E:\wk\n8nwork
docker compose up -d ocr ollama pdf-parser postgres qdrant n8n

cd E:\wk\hragent\hragentv1
docker compose up -d

cd E:\wk\n8nwork
docker compose --profile quick-tunnel up -d cloudflared-quick
```

查看状态：

```powershell
docker ps
```

普通停止不会删除数据：

```powershell
cd E:\wk\hragent\hragentv1
docker compose stop

cd E:\wk\n8nwork
docker compose --profile quick-tunnel stop
```

禁止执行 `docker compose down -v`，其中 `-v` 会删除 MySQL、n8n、Qdrant 和 Ollama 数据卷。

## 9. Quick Tunnel 与钉钉

Quick Tunnel 只适合 Demo。电脑关机、容器重建或隧道重启后，`trycloudflare.com` 域名可能变化。

自动读取当前地址并同步本地 n8n：

```powershell
cd E:\wk
powershell -ExecutionPolicy Bypass -File .\refresh-hragent-tunnel.ps1
```

主动生成新域名并复制机器人地址：

```powershell
powershell -ExecutionPolicy Bypass -File E:\wk\refresh-hragent-tunnel.ps1 -NewTunnel -CopyCallback
```

钉钉开发者后台需要填写：

```text
https://当前域名.trycloudflare.com/webhook/hragent-dingtalk-8a2d811d-0fe5-4198-8e61-288ec417fc04
https://当前域名.trycloudflare.com/webhook/hragent-dingtalk-leave-card
```

域名变化后仍需在钉钉开发者后台更新消息接收地址并重新发布应用版本。本地脚本不能代替这一步。

## 10. 健康检查与验收

状态检查不会重启或修改服务：

```powershell
powershell -ExecutionPolicy Bypass -File E:\wk\check-hragent-local.ps1 -NoPause
```

通过标准：

- SaaS、AI 网页、API、n8n 均为 `[OK]`。
- Quick Tunnel、`WEBHOOK_URL` 和审批地址一致。
- `Required n8n workflows - active=8/8`。
- `n8n error workflow bindings - bound=7/7`。
- 网页附件内部密钥一致且非默认值。
- 网页身份密钥已配置且非默认值。

只读边界验收：

```powershell
powershell -ExecutionPolicy Bypass -File E:\wk\run-hragent-acceptance.ps1 -NoPause
```

通过标准为 `Failed: 0`。当前覆盖身份、余额、纯周末、跨周末、余额不足、重复日期、钉钉未绑定身份、无效 API Key、主管待办和员工越权。

演示前还应手动做一次完整业务闭环：

1. 张三查询余额并提交一个未来工作日请假。
2. 李四在钉钉审批卡片中同意或拒绝。
3. 张三收到结果并在 SaaS 日历中查看状态。
4. 上传一个制度文件后提问，确认回答包含文件来源。
5. 上传一张清晰图片，确认 OCR 能返回中文内容。

## 11. 环境变量与安全

必须保留：

- `E:\wk\hragent\hragentv1\.env`
- `E:\wk\n8nwork\.env`
- `E:\wk\n8nwork\saas-agent.env`
- n8n 中保存的 DeepSeek、钉钉、Qdrant 和 Ollama 凭据
- 原始 `N8N_ENCRYPTION_KEY`

关键但不得公开值的变量包括：

- `HRAGENT_WEB_ATTACHMENT_INTERNAL_KEY`
- `HRAGENT_WEB_CHAT_IDENTITY_SECRET`
- `N8N_ENCRYPTION_KEY`
- DeepSeek API Key
- 钉钉 AppKey/AppSecret

附件密钥必须在 SaaS 与 n8n 的 `.env` 中一致。`N8N_ENCRYPTION_KEY` 一旦保存过凭据就不能随意更换，否则 n8n 无法解密现有凭据。

## 12. 数据持久化和备份

主要 Docker 数据卷：

- `hragentv1_mysql-data`：SaaS 业务数据。
- `n8nwork_n8n-postgres-data`：n8n 工作流、凭据元数据和执行记录。
- `n8nwork_n8n-data`：n8n 本地数据。
- `n8nwork_qdrant-data`：知识库向量。
- `n8nwork_ollama-data`：本地模型文件。

重大修改前至少备份 MySQL、n8n PostgreSQL、两套 `.env` 和工作流 JSON。Qdrant 需要与对应知识文件和元数据一起备份。恢复 n8n 时必须同时保留原 `N8N_ENCRYPTION_KEY`。

项目当前不是 Git 仓库。后续应尽快初始化私有 Git 仓库，并用 `.gitignore` 排除 `.env`、数据库备份、上传文件和真实凭据。

## 13. 常见故障

### 钉钉完全不回复

1. 运行健康检查。
2. 确认 Quick Tunnel 域名与钉钉后台地址一致。
3. 确认钉钉应用修改后已重新发布。
4. 确认 `HRAgent-02-钉钉机器人` 为 Active。
5. 查看 `docker logs hragent-n8n --tail 200`。

### 网页聊天返回 401 或要求重新登录

1. 重新登录 AI 网页。
2. 检查 SaaS 后端和 n8n 是否运行。
3. 运行健康检查，确认网页身份密钥和附件密钥正常。
4. 查看 `docker logs hragent-backend --tail 200` 和 `docker logs hragent-n8n --tail 200`。

### 知识库没有相关制度

1. 在 SaaS 知识库页面确认文件状态为已索引。
2. 运行健康检查，确认 RAG 三条工作流为 Active。
3. 打开 `http://localhost:6333/dashboard` 检查 `hragent_knowledge` 集合。
4. 查看 RAG Ingest/Search 最近一次 n8n 执行。
5. 确认提问与文件正文确实相关；没有命中时返回“没有相关制度”是预期保护行为。

### 图片或文件解析失败

1. 确认 `hragent-n8n-ocr` 和 `hragent-n8n-pdf-parser` 正在运行。
2. 确认 `HRAgent-03-钉钉文件解析` 为 Active。
3. 使用清晰 JPG/JPEG/PNG 测试 OCR。
4. 扫描 PDF 先转图片；超大文件先压缩或拆分。

### 日志中的已知非故障信息

- 浏览器刷新或取消请求可能产生 `Broken pipe`。
- 容器重建瞬间，Nginx 可能短暂记录 `Connection refused`。
- 只读验收会故意产生无效 API Key 和未绑定钉钉身份的拒绝日志。
- n8n 的 `@napi-rs/canvas` 原生绑定警告当前不影响已验证的文件、OCR 和 RAG 链路。

## 14. 2026-07-31 最终审计结果

- 两套 Docker Compose 配置通过校验。
- 后端、SaaS 前端、独立 AI 网页均成功重新构建。
- Spring Boot 全部单元测试通过。
- 只读业务验收：`10 passed / 0 failed / 0 skipped`。
- 8/8 必需 n8n 工作流启用，7/7 业务工作流绑定统一错误处理。
- 网页附件内部密钥一致、非默认；网页身份密钥为随机非默认值。
- TXT、PDF、DOCX、PNG OCR 解析通过。
- 网页普通聊天和附件聊天真实调用 n8n/DeepSeek 通过。
- RAG 命中返回来源文件，无命中不编造；Qdrant 集合健康。
- AI 网页和 SaaS 页面无横向溢出，聊天输入框可见。
- SaaS API 的 `LocalDate` 已统一输出为 `YYYY-MM-DD`，不再输出数组日期。
- SaaS 前端生产依赖审计为 0 个漏洞。
- 修复后近期 OCR、PDF 和 Qdrant 日志无异常；其余日志只包含上述已知非故障项。

## 15. 未完成项和下一步

按优先级建议：

1. 实现工资查询：先在 SaaS 建立工资数据、字段级权限和审计，再开放智能体工具。
2. 实现联网搜索：使用明确的搜索 API，并把网络信息与公司知识库引用分开显示。
3. 为 Qdrant 增加 `workspaceId` 元数据过滤，实现企业/租户级知识隔离。
4. 将 Quick Tunnel 替换为固定域名和正式 Cloudflare Tunnel。
5. 引入 Flyway 或 Liquibase，替代 Hibernate `ddl-auto: update`。
6. 建立对象存储、文件保留期限、病假材料脱敏和访问审计制度。
7. 将 Vue/Vite/vue-tsc 开发工具链单独升级；当前生产依赖无漏洞，但开发依赖仍有 5 个 high、1 个 moderate。
8. 建立自动备份、恢复演练、监控告警和生产部署方案。

其他已知风险：

- 当前是单公司 Demo，不是严格生产级多租户平台。
- Qdrant 客户端 `1.16.2` 与服务端 `1.18.3` 有兼容提示，但当前 RAG 实测正常；不要直接降级已有数据卷。
- 本地 n8n 不受 n8n Cloud 套餐到期影响，但云端账号中的工作流和凭据不会自动同步到本地。
- 工资查询和联网搜索尚未实现，演示时不要声明已经支持。
