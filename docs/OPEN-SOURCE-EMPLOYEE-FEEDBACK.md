# 员工体验反馈：开源集成记录

2026-09-08，实际下载并集成 SurveyJS Form Library 3.0.3 的 `survey-core` 与 `survey-vue3-ui`，两个 Vue 客户端均锁定精确版本，npm lockfile 保存完整性校验。上游：https://github.com/surveyjs/survey-library 。许可证 MIT，原文保存在 `third-party/surveyjs/LICENSE`。使用免费 Form Library，不依赖收费 Creator、Dashboard 或外部调查平台。组件按需加载。

检索过 Frappe HRMS（https://github.com/frappe/hrms，完整 HR 系统）及 Formbricks（https://github.com/formbricks/formbricks，反馈平台）；本次采用可嵌入现有 Vue + Spring Boot 的 SurveyJS。没有把其他项目的商业模块复制进来，也没有宣称移植了整套 HRMS。

员工入口：工作台 → 支持与关怀 → 员工体验反馈。HR 入口：组织运营 → 员工反馈。五个原创问题覆盖目标、工作量、沟通、支持、工具，不是临床量表，不生成个人情绪或离职风险。每自然月一期，上海时区，用户主动勾选知情同意后才能提交，每账号每期一次，不自动向员工发送消息。

后端 `/employee-relations/pulse`：GET 获取本期和本人提交状态；POST 校验同意、当前月份和五项整数评分。数据库 `er_pulse_responses` 存企业、月份、HMAC 防重复标记及 AES-GCM 密文，不存姓名、部门、原文或精确提交时间。管理员持有系统密钥时仍可能关联账号，因此界面不承诺完全匿名。提交后不可修改；保留记录供后续月份汇总，无自动删除策略。

HR 的 `/employee-relations/pulse/results?period=YYYY-MM` 仅返回本企业已结束月份的汇总；至少 5 人才显示人数及均分，没有个人答案接口、部门筛选或实时差分展示。当前月份、未来月份和不足人数的月份均无分数或人数。问卷全部在本地浏览器呈现，答案只发到现有后端。

验证：EmployeePulseServiceTest 覆盖同意、非法值、过期写入、重复提交、加密、角色权限、企业作用域、实时统计封锁及五人门槛。浏览器验收不向真实员工账号写入测试答案。

依赖检查：本次 npm audit 未列出 SurveyJS 漏洞；现有工作台依赖仍有 4 条、聊天客户端有 6 条告警，包含 Vite/esbuild 等原有依赖。本次未强制升级现有构建工具，也不宣称全系统审计通过。SurveyJS 许可证随前端发布在 `/licenses/surveyjs-MIT.txt`。
