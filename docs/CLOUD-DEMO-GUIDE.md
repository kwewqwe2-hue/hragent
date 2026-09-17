# HRAgent 云端演示指南

本指南用于部署受 HTTPS 保护的演示环境，不适用于处理真实员工生产数据。

## 公开边界

云端版本使用 Caddy 自动申请 TLS 证书，并只公开以下域名：

| 域名 | 用途 | 公开范围 |
| --- | --- | --- |
| `app.<你的域名>` | SaaS 管理与员工门户 | 完整站点 |
| `chat.<你的域名>` | AI 员工助手 | 完整站点 |
| `automation.<你的域名>` | 钉钉和审批卡片回调 | 仅 `/webhook/*` |

MySQL、Redis、Qdrant、Ollama、Spring Boot API 和 n8n 编辑器不对公网监听。管理员通过 SSH 隧道打开 n8n 编辑器。

## 部署前准备

1. 准备一台 Linux 云服务器，建议至少 4 vCPU、8 GB 内存、60 GB 磁盘，并安装或允许脚本安装 Docker Compose。
2. 在安全组和系统防火墙中仅放行 TCP `22`、`80`、`443`。不要放行 `5173`、`5174`、`5678`、`8080`、`3306`、`6379`、`6333`、`11434`。
3. 准备一个域名，例如 `demo.example.com`，并创建下列 A 记录，全部指向云服务器公网 IP：

   - `app.demo.example.com`
   - `chat.demo.example.com`
   - `automation.demo.example.com`

4. 等待 DNS 生效。Caddy 需要能够从公网接收 80/443 的验证请求，才能签发 HTTPS 证书。
5. 本机需要 Bash、`ssh`、`scp` 和 Java 21。部署脚本会上传本地构建的后端 JAR。

## 部署

在仓库根目录先构建后端：

```bash
cd hragentv1/backend
./mvnw clean package -DskipTests
```

没有 Maven Wrapper 时使用本机 Maven：

```bash
mvn clean package -DskipTests
```

返回根目录执行部署。第二个参数是根域名，不要包含 `https://`：

```bash
./scripts/deploy-hragent-cloud.sh <服务器公网IP> demo.example.com root /opt/hragent
```

脚本会生成服务器本地密钥、启动容器、导入工作流，并配置：

```text
https://app.demo.example.com
https://chat.demo.example.com
https://automation.demo.example.com/webhook/...
```

部署后先检查：

```bash
curl -I https://app.demo.example.com
curl -I https://chat.demo.example.com
curl -I https://automation.demo.example.com/webhook/not-a-real-hook
```

前两条应返回 200，最后一条应返回 404 或工作流自己的响应。若 TLS 失败，先检查 DNS 是否都已指向本机和 80/443 是否放行。

## 初始化 n8n 与 AI

1. 从本机建立 SSH 隧道：

   ```bash
   ssh -L 5678:127.0.0.1:5678 root@<服务器公网IP>
   ```

2. 保持该窗口打开，在浏览器访问 `http://localhost:5678`。
3. 首次创建 n8n Owner 账号。
4. 为 DeepSeek Chat Model 配置真实凭据。不要把 API Key 写入工作流 JSON 或提交到仓库。
5. 打开 `https://app.<你的域名>`，使用 HR 演示账号登录，在“开放平台 / 接口中心”生成 SaaS Agent API Key。
6. 在服务器的 `/opt/hragent/n8nwork/saas-agent.env` 填入 `SAAS_AGENT_API_KEY`，然后重启 n8n：

   ```bash
   cd /opt/hragent/n8nwork
   docker compose --env-file .env -f docker-compose.yml -f docker-compose.cloud.yml up -d n8n
   ```

7. 在钉钉开发者后台配置机器人回调：

   ```text
   https://automation.<你的域名>/webhook/hragent-dingtalk-8a2d811d-0fe5-4198-8e61-288ec417fc04
   ```

   审批卡片使用同一 `automation` 域名下的工作流 Webhook。修改钉钉 AppKey/AppSecret 后重启 n8n。

## 推荐演示顺序

1. 打开 `app` 域名，以 `zhangsan / 123456` 登录，展示个人信息、假期余额和请假页面。
2. 打开 `chat` 域名，用同一账号登录，询问“我的年假余额”或“请假制度”。
3. 在 AI 对话中发起请假：先填写假别、日期和原因，确认预检信息后发送“确认提交”。
4. 以 `lisi / 123456` 登录 SaaS 或钉钉，展示主管审批；以 `wanghr / 123456` 展示 HR 备案与审计记录。
5. 展示在职证明申请和知识库检索。云端轻量版禁用了图片 OCR，PDF/DOCX/TXT 流程仍可演示。

演示账号和默认密码只能用于隔离的 Demo 数据库。对外演示前应修改或禁用不使用的默认账号。

## 运维检查

服务器上查看服务状态：

```bash
cd /opt/hragent/hragentv1
docker compose --env-file .env -f docker-compose.yml -f docker-compose.cloud.yml ps

cd /opt/hragent/n8nwork
docker compose --env-file .env -f docker-compose.yml -f docker-compose.cloud.yml ps
```

查看日志：

```bash
docker logs --tail=200 hragent-backend
docker logs --tail=200 hragent-n8n
docker logs --tail=200 hragent-cloud-proxy
```

日常停止服务使用 `docker compose stop`。不要在演示环境执行 `docker compose down -v`，该命令会删除数据库、n8n 和向量库数据卷。

## 已知边界

- 当前钉钉消息入口尚未实现平台级回调验签与防重放，不能把它视为真实生产 HR 系统。
- n8n 工作流通过环境变量读取 SaaS Agent Key；只有受控管理员可以编辑工作流或读取服务器环境。
- 数据库迁移仍使用 Hibernate `ddl-auto=update`，正式生产应切换到 Flyway 或 Liquibase，并建立备份、监控、密钥轮换和数据出境治理。
