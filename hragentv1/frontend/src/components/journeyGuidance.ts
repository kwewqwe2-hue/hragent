export const journeyGuidance: Record<string, {steps:string[];path:string;action:string}> = {
 'day1-account':{steps:['确认报到时间、办公地点和当天联系人。','核对电脑、电源等领取物品；发现缺件及时反馈。','试登录企业账号和常用系统，异常时记录系统名称和错误提示，再联系 IT。'],path:'/account',action:'查看我的账号'},
 'day1-buddy':{steps:['确认直属负责人、导师或入职对接人。','用几句话介绍自己的岗位和近期工作。','约定遇到问题时的联系渠道与方便沟通的时间。'],path:'/directory',action:'查找团队联系人'},
 'week-policy':{steps:['确认工作地适用的考勤安排和打卡入口。','查看自己的假期余额，了解提交和审批步骤。','遇到特殊情况，先向负责人或 HR 确认办理要求。'],path:'/my-leave',action:'查看假期与请假入口'},
 'week-tools':{steps:['列出完成当前工作真正需要的系统。','逐项写清用途、只读或编辑范围、使用期限。','使用下方工具生成申请说明，再交给企业 IT 审批；开通后验证能否访问。'],path:'/directory',action:'查找 IT 或协作联系人'},
 'week-culture':{steps:['确认项目资料、会议纪要和任务记录放在哪里。','了解例会频率、进展同步渠道和回复预期。','把不清楚的词放进下方解释工具，带着具体问题向同事确认。'],path:'/employee-experience?section=onboarding',action:'留在新人指引'},
 'month-goals':{steps:['列出本月最重要的工作成果和学习目标。','把目标拆成可以检查的小任务和时间节点。','与导师确认完成标准、需要的支持和下次回顾时间。'],path:'/directory',action:'查找导师或负责人'},
 'month-feedback':{steps:['准备一件做得顺利的事和一个遇到的困难。','记录希望得到的建议、培训或协作支持。','与导师约定下一步，转正时间以 HR 通知为准。'],path:'/employee-experience?section=support',action:'整理沟通与支持需求'},
 'exit-work':{steps:['列出负责项目、当前进展和待解决的问题。','与负责人确认交接人及交接时间。','只通过已授权渠道交接资料，并确认接收结果。'],path:'/directory',action:'查找交接联系人'},
 'exit-equipment':{steps:['核对领取过的设备和归还清单。','与 IT 确认归还方式及账号处理时间。','保留归还凭证；账号停用由正式流程执行。'],path:'/directory',action:'查找 IT 联系人'},
 'exit-settlement':{steps:['列出待处理的报销、工资和证明需求。','与 HR 确认结算安排及社保事项。','在证明工作台跟进已提交的申请。'],path:'/certificates',action:'查看证明申请'},
 'exit-agreement':{steps:['找到本人签署的保密、竞业等相关文件。','记录不清楚的条款和需要核实的事实。','存在争议时通过专业咨询渠道处理。'],path:'/employee-experience?section=compliance',action:'进入专业咨询'}
}
