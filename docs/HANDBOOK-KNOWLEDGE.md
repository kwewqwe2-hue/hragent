# 原聊天入口的手册知识库

按用户要求恢复原聊天与服务中心页面，不新增服务面板。知识库通过原聊天接口检索，原知识库管理页可查看“员工手册参考”分类。

个人助手查询优先使用业务接口，支持年假等假期余额、总额和已用天数、请假审批进度、证明申请状态，以及本人的工号、部门、岗位和入职日期。口语提问如“我还有多少年假”直接查询当前登录员工的台账；没有台账时提示待维护，不用知识库推算个人余额。`scripts/test-personal-assistant.cjs` 对照两个演示账号的业务接口验证结果，并检查原聊天页面。

资料为用户提供的《人力资源和社会保障管理实务手册》2022年版，共171页。前三页目录不作为回答依据；正文按条文拆分，附录按页保留。它只补充原知识库，不覆盖现有条目或业务功能。个人台账、适用的公司制度优先；缺少公司依据时本地检索参考要点，并简短说明适用版本待核实。日常回答不再展示整篇条文和手册开场白；用户追问出处时展示原有来源、版本和页码。不向外部模型发送手册。

导入步骤：安装 pdfplumber 后执行 `python scripts/extract-hrmanual.py <PDF路径>`，启动本地后端后执行 `node scripts/import-hrmanual.mjs`。导入账号默认使用演示HR账号，可通过 HRAGENT_IMPORT_USERNAME / HRAGENT_IMPORT_PASSWORD 设置。索引保存在忽略版本控制的 n8nwork/knowledge-files 下，导入限于登录账号所在企业；重复导入更新同来源条目，过时片段标记 SUPERSEDED。

验证：HandbookKnowledgeServiceTest / EmployeeAgentRouterTest 覆盖参考检索、按需出处、租户及审核隔离、个人业务和公司制度优先；`node scripts/test-concise-chat.cjs` 验证简洁回答与原功能页面。
