# HRAgent 管理端使用说明

这份说明给第一版公司 Demo 使用。你可以先用 HR 账号 `wanghr / 123456` 登录，然后按下面顺序演示。

## 1. 组织配置

位置：左侧菜单 `组织配置`

这里维护员工导入和员工账号依赖的基础数据：

- 部门：例如 `R&D Center`、`Human Resources`、`Operations`
- 岗位：例如 `Java Engineer`、`Engineering Manager`、`HR Manager`

导入员工时，系统不会自动创建部门和岗位。如果表格里的部门或岗位不存在，会在预览校验里提示错误。这样做更接近真实企业系统，避免脏数据进入数据库。

## 2. 员工数据

位置：左侧菜单 `员工数据`

HR 可以手动新增、编辑员工，设置直属主管，也可以把员工密码重置为 `123456`。

当前字段包括：

- 工号 `employeeNo`
- 姓名
- 手机号
- 邮箱
- 部门
- 岗位
- 直属主管工号
- 入职日期
- 员工状态

第一版还是单公司 Demo，但后端保存时已经带着 `tenantId`，以后扩成多公司时可以继续沿用。

## 3. 数据导入

位置：左侧菜单 `数据导入`

导入分两步：

1. 预览校验：只读文件，不写数据库。
2. 确认导入：全部行校验通过后才写数据库。

员工模板：

```csv
userId,employeeNo,name,role,phone,email,department,title,managerEmployeeNo,entryDate,status
USR-XXXXXXXXXX,E002,Alice Chen,EMPLOYEE,13800000004,alice@example.com,R&D Center,Java Engineer,M001,2026-01-15,ACTIVE
```

`userId` 是注册账号页面显示的公开账号 ID。用户的加入申请审核通过后，管理员在导入表中填写该 ID，导入成功后账号才能查看企业内的假期数据。`role` 支持 `EMPLOYEE`、`MANAGER` 和 `HR`。

余额模板：

```csv
employeeNo,annualBalance,sickBalance,personalBalance,marriageBalance
E002,10,10,5,10
```

模板文件也放在：

```text
docs/templates/employees-template.csv
docs/templates/leave-balances-template.csv
```

## 4. 接口中心

位置：左侧菜单 `接口中心`

HR 可以创建 API Key，外部系统调用接口时在请求头加入：

```text
X-API-Key: 你的密钥
```

当前开放接口：

```text
GET  /api/openapi/v1/employees/{employeeNo}
GET  /api/openapi/v1/balances/{employeeNo}
POST /api/openapi/v1/employees/sync
```

示例：

```powershell
$apiKey = "把接口中心创建出来的密钥放这里"
Invoke-RestMethod -Method GET `
  -Uri "http://localhost:5173/api/openapi/v1/employees/E001" `
  -Headers @{ "X-API-Key" = $apiKey }
```

调用记录会显示在接口中心下方的日志表里。

## 5. 智能体配置

位置：左侧菜单 `智能体配置`

HR 可以维护 DeepSeek 的 API Base URL、模型名称、API Key 和启用状态。保存后的 API Key 使用 AES-GCM 加密写入数据库，查询接口只返回掩码，不返回明文。

操作顺序：

1. 填写 DeepSeek API Key。
2. 确认 Base URL 和模型名称。
3. 打开“启用智能体”并保存。
4. 点击“测试连接”。

以后只修改模型或地址时，API Key 输入框保持为空即可，系统会保留原密钥。

`APP_ENCRYPTION_KEY` 是解密数据库中 API Key 的主密钥。正式环境必须设置为足够长的随机值并妥善备份；修改或丢失它会导致已有 API Key 无法解密。
