# HR Agent 本地启动与使用手册

这份手册适用于当前电脑上的三个项目：

- SaaS：`E:\wk\hragent\hragentv1`
- 独立 AI 网页：`E:\wk\hragent\hragent-chat`
- n8n：`E:\wk\n8nwork`

## 一、每天开机后一键启动（推荐）

打开 PowerShell，执行：

```powershell
cd E:\wk
powershell -ExecutionPolicy Bypass -File .\start-hragent-local.ps1
```

脚本会自动完成以下操作：

1. 检查 Docker；如果 Docker Desktop 没有运行则自动启动并等待。
2. 启动 n8n 和 PostgreSQL，并创建 SaaS 与 n8n 共用的 Docker 网络。
3. 启动 SaaS 的 MySQL、Redis、后端、管理端和独立 AI 网页。
4. 启动 Quick Tunnel。
5. 读取本次 `trycloudflare.com` 地址，同时更新 n8n 的消息回调和审批卡片地址。
6. 重建 n8n 容器使新地址生效。
7. 执行本地健康检查并输出钉钉机器人回调地址。

如果修改过 Java、Vue 或 Docker 构建文件，使用：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-hragent-local.ps1 -Build
```

Quick Tunnel 地址变化后，脚本只能更新本机配置，不能替你修改钉钉开放平台。请把脚本输出的 `DingTalk robot callback` 填到钉钉机器人消息接收地址并重新发布应用版本。

## 二、手动启动顺序

### 1. 启动 Docker Desktop

等待 Docker Desktop 显示 Engine 正常运行，再打开 PowerShell。

### 2. 先启动 n8n 和 PostgreSQL

SaaS 后端会通过 `n8nwork_default` Docker 网络调用 n8n，因此第一次手动启动时必须先创建这个网络：

```powershell
cd E:\wk\n8nwork
docker compose up -d postgres n8n
```

访问 n8n：`http://localhost:5678`

### 3. 启动 SaaS 和独立 AI 网页

```powershell
cd E:\wk\hragent\hragentv1
docker compose up -d
docker compose ps
```

正常情况下应看到 `mysql`、`redis`、`backend`、`frontend`、`chat` 都是 `Up`，其中 MySQL 和 Redis 最终会显示 `healthy`。

访问地址：

- SaaS 页面：`http://localhost:5173`
- 独立 AI 网页：`http://localhost:5174`
- 后端接口：`http://localhost:8080/api`

### 4. 启动临时公网隧道

```powershell
cd E:\wk\n8nwork
docker compose --profile quick-tunnel up -d
docker compose ps
```

查看临时隧道地址：

```powershell
docker compose logs cloudflared-quick --tail=50
```

日志中的 `https://...trycloudflare.com` 是本次临时公网域名。钉钉机器人回调地址应为：

```text
https://本次域名/webhook/hragent-dingtalk-8a2d811d-0fe5-4198-8e61-288ec417fc04
```

Quick Tunnel 容器被重新创建后域名可能变化。域名变化时，必须在钉钉开放平台更新机器人消息接收地址并重新发布版本。

审批卡片按钮也使用这个公网域名。若 `.env` 中的 `WEBHOOK_URL` 不是当前 Quick Tunnel 地址，请额外设置：

```dotenv
DINGTALK_APPROVAL_BASE_URL=https://本次域名
```

修改 `.env` 后重建 n8n：

```powershell
docker compose up -d --force-recreate n8n
```

## 三、启动后的快速检查

### 检查 SaaS

```powershell
cd E:\wk\hragent\hragentv1
docker compose ps
docker compose logs backend --tail=80
```

后端日志出现 `Started HrAgentApplication` 表示启动完成。

### 检查 n8n

```powershell
cd E:\wk\n8nwork
docker compose ps
docker compose logs n8n --tail=80
```

日志应同时出现以下两个已启用工作流：

- `HRAgent-02-SaaS-DingTalk-Agent`
- `HRAgent-06-DingTalk-Notification-Dispatcher`
- `HRAgent-07-DingTalk-Leave-Card-Action`
- `HRAgent-08-Error-Handler`

### 在钉钉测试

员工账号可以发送：

```text
查询我的请假
查询请假 #8 最新状态
我要请年假
```

主管账号可以发送：

```text
查看我的待审批请假
同意 #请假编号
拒绝 #请假编号 原因
```

### 在独立 AI 网页测试

打开 `http://localhost:5174`，使用与 SaaS 相同的账号密码登录。网页消息链路为：

```text
AI 网页 -> SaaS 后端身份校验 -> 本地 n8n -> DeepSeek / SaaS 工具 -> AI 网页
```

网页与钉钉共用同一套 n8n 智能体和 SaaS 业务数据，但聊天会话彼此独立。员工在网页发起请假后，SaaS 仍会通过现有通知流程把审批消息主动发送给钉钉上的直属主管；钉钉机器人及审批卡片无需修改。

## 四、当前真实业务流程

1. 员工在钉钉提交请假并二次确认。
2. n8n 调用 SaaS 创建真实请假单。
3. SaaS 把主管通知写入通知发件箱。
4. 通知派发器每分钟主动发送给直属主管。
5. 主管可在钉钉审批，也可在 SaaS 网页审批。
6. 钉钉审批会自动完成 HR 备案；网页流程可继续由 HR 在 SaaS 备案。
7. 最终结果写入通知发件箱，并主动发送给员工。
8. AI 查询申请时始终读取 SaaS 最新状态，不以聊天记忆作为业务事实。

通知发送临时失败时不会丢失。未送达记录保留在发件箱，下一分钟自动重试；只有钉钉接口成功后才会标记为已送达。

## 五、运行验收

只读边界验收不会创建新请假单，可以随时执行：

```powershell
cd E:\wk
powershell -ExecutionPolicy Bypass -File .\run-hragent-acceptance.ps1
```

当前脚本覆盖员工身份、余额、纯周末、跨周末、余额不足、重复日期、未绑定账号、无效 API Key、主管待办和员工权限边界。全部通过时最后一行应显示 `Failed: 0`。

## 六、排查错误和请求日志

机器人报错时先记下回复或 HTTP 响应中的 `X-Request-Id`。这个编号用于把一次请求在后端日志和 API 调用日志中对应起来。

查看 SaaS 后端日志：

```powershell
cd E:\wk\hragent\hragentv1
docker compose logs backend --tail=200
```

查看 n8n 日志：

```powershell
cd E:\wk\n8nwork
docker compose logs n8n --tail=200
```

在 n8n 页面打开 `Executions` 可以查看每次工作流执行到了哪个节点。未处理的工作流异常会由 `HRAgent-08-Error-Handler` 写入 SaaS 的 API 调用日志，记录工作流、执行编号、最后节点和错误摘要。

## 七、关闭系统

关闭 n8n 和隧道：

```powershell
cd E:\wk\n8nwork
docker compose --profile quick-tunnel stop
```

关闭 SaaS：

```powershell
cd E:\wk\hragent\hragentv1
docker compose stop
```

不要运行 `docker compose down -v`。其中 `-v` 会删除 MySQL、n8n 和 PostgreSQL 数据卷。

## 八、代码更新后重新构建

只有修改了 Java、Vue 或 Docker 构建文件时才需要 `--build`：

```powershell
cd E:\wk\hragent\hragentv1
docker compose up -d --build
```

平时开机启动只用 `docker compose up -d`，不需要每次重新构建。

## 九、常见故障

### 钉钉发消息完全没反应

依次检查：

1. `docker compose ps` 中 n8n 和隧道是否为 `Up`。
2. Quick Tunnel 域名是否变化。
3. 钉钉回调地址是否为当前域名加固定 webhook 路径。
4. n8n 主工作流是否为已发布状态。

### 能聊天，但主管或员工收不到主动通知

```powershell
cd E:\wk\n8nwork
docker compose logs n8n --tail=150
```

同时确认 `HRAgent-06-DingTalk-Notification-Dispatcher` 已发布。当前 Docker 配置已把 `api.dingtalk.com` 固定到 IPv4，避免 Docker 无 IPv6 路由导致 `ENETUNREACH`。

### SaaS 页面显示 502

```powershell
cd E:\wk\hragent\hragentv1
docker compose ps
docker compose logs backend --tail=150
```

先确认后端已出现 `Started HrAgentApplication`，再刷新页面。

### 不要在聊天或截图中公开的内容

- DeepSeek API Key
- SaaS Agent API Key
- 钉钉 AppSecret
- n8n 加密密钥
- Cloudflare Tunnel Token
- 完整的钉钉用户 ID、员工 ID 和临时回调 URL
