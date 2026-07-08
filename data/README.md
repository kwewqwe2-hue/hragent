# data 目录

此目录用于本地运行时数据。

请复制示例配置：

```powershell
Copy-Item data\config.example.json data\config.json
```

运行后端时会自动生成 Demo SQLite 数据库：

```text
data/hragent_demo.sqlite3
```

真实 API Key、SQLite 数据库和运行时数据不提交到 Git。
