# HRAgent 新人接手 10 分钟启动清单

完整长版请看 `docs/HRAGENT_HANDOFF_LONG.md`。

## 先记住

- `v2` 是可运行代码分支。
- `handover` 是交接说明分支。

## 1. 准备

- Windows 10/11
- Docker Desktop
- PowerShell
- Git

## 2. 克隆

```powershell
git clone -b handover https://github.com/kwewqwe2-hue/hragent.git
cd hragent
```

## 3. 初始化配置

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\initialize-hragent-config.ps1
```

会生成：

- `hragentv1/.env`
- `n8nwork/.env`
- `n8nwork/saas-agent.env`

只填本机值，不要提交密钥。

## 4. 启动

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-hragent-local.ps1 -Build
```

## 5. 检查

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-hragent-local.ps1 -NoPause
```

重点看：

- `SaaS frontend` / `HRAgent AI web` / `SaaS API` / `n8n editor` 都是 `HTTP 200`
- `Required n8n workflows - active=9/9`
- `n8n error workflow bindings - bound=8/8`

## 6. 常用地址

- SaaS：`http://localhost:5173`
- AI 网页：`http://localhost:5174`
- API：`http://localhost:8080/api`
- n8n：`http://localhost:5678`

## 7. 隧道变更

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\refresh-hragent-tunnel.ps1 -NewTunnel
```

然后把脚本输出的两个钉钉地址更新到钉钉后台并重新发布。

## 8. 不要上传

- `.env`
- `saas-agent.env` 真实密钥
- 数据库卷
- 上传文件
- 任何 token / API Key
