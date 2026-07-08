# HR智能体 v0 展示版

本仓库是 **HR智能体 v0 展示版本**，用于演示最基础的员工关系 Agent 和基础交互功能。

当前版本不是生产系统，也不是完整商业产品。它主要展示：

- 员工关系智能体问答
- 本地知识库检索
- 文档生成
- 工资查询 Demo
- 社保/公积金查询 Demo
- 入职/离职流程展示
- 工单创建与敏感问题转人工
- 接入中心展示
- 权限记录/审计日志展示
- Electron 桌面外壳加载现有网页

## 版本边界

v0 只实现最基础的员工关系 Agent。

以下模块当前只做入口或展示，不做完整业务闭环：

- 招聘
- 薪酬
- 福利
- 绩效考核
- 培训

知识库内容均为 Demo 示例，不代表上海外服集团正式制度、正式政策或客户真实制度。

## 技术结构

```text
Electron 桌面外壳
  └─ 加载本地网页 http://127.0.0.1:8765

Python 本地后端
  ├─ 登录与角色权限
  ├─ 员工关系问答
  ├─ 知识库检索
  ├─ 工资/社保/公积金模拟查询
  ├─ 文档生成
  ├─ 工单创建
  ├─ 接入中心
  └─ 审计日志

本地数据
  ├─ SQLite Demo 数据库
  ├─ Markdown 知识库
  └─ 本地生成文档
```

## 开发运行

需要：

- Python 3
- Node.js
- pnpm

安装依赖：

```powershell
pnpm install
```

初始化本地配置：

```powershell
Copy-Item data\config.example.json data\config.json
```

启动后端：

```powershell
python app.py
```

浏览器访问：

```text
http://127.0.0.1:8765
```

启动 Electron：

```powershell
pnpm start
```

## Demo 账号

| 角色 | 账号 | 密码 |
|---|---|---|
| 员工 | employee01 | 123456 |
| 员工 | employee02 | 123456 |
| 主管 | manager01 | 123456 |
| HR | hr01 | 123456 |
| 管理员 | admin | admin123 |

## DeepSeek API

可在系统的“模型设置”页面填写 DeepSeek API Key。

本仓库不会提交真实 API Key。`data/config.json` 已被 `.gitignore` 排除。

## 打包说明

Electron 打包产物、Electron 运行时、Python runtime、node_modules、SQLite 数据库、生成文档均不提交到仓库。

如需制作 Windows 绿色版，需要在本地安装依赖后打包，并将 Python runtime 放入应用运行目录。

## AI 安全边界

智能体只做辅助，不做最终人事判断。

以下场景应建议转人工：

- 投诉
- 劳动争议
- 薪酬异议
- 绩效争议
- 离职纠纷
- 员工情绪风险
- 知识库依据不足

