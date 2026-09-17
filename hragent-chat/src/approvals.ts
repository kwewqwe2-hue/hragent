import type { AuthSession } from './types'
export type ApprovalKind = 'leave' | 'certificate' | 'lifecycle' | 'onboarding' | 'access'
export type ApprovalRow = { key: string; id: number; kind: ApprovalKind; label: string; employeeId?: number; employeeName: string; status: string; statusLabel: string; summary: string; opinion: string; fields: Record<string,string>; signature: string; complete: boolean; raw: any }
export type ApprovalSource = { key: string; kind: ApprovalKind; side: 'mine' | 'pending'; path: string }
export function isSubmissionReceipt(reply:{provider?:string;answer:string}):boolean{return reply.provider==='hrssc-lifecycle'&&/^(?:已提交|提交好啦)[，,]\s*服务单\s*#\d+/.test(reply.answer)}
const labels: Record<string,string> = { PENDING:'待 HR 审核', PENDING_MANAGER:'待主管审批', PENDING_HR:'待 HR 审核', SUBMITTED:'待 HRSSC 审核', NEEDS_INFO:'待补充材料', APPROVED:'审核通过，办理中', COMPLETED:'已办结', GENERATED:'文件已生成', GENERATION_FAILED:'审核已通过，文件生成待处理', REJECTED:'未通过', CANCELLED:'已取消' }
export function approvalSources(session: AuthSession): ApprovalSource[] {
 const result: ApprovalSource[] = [], role=session.user.role, left=session.user.employeeStatus==='LEFT', active=!left&&['EMPLOYEE','MANAGER','HR'].includes(role||'')
 const add=(kind:ApprovalKind,side:'mine'|'pending',path:string)=>result.push({key:`${side}:${kind}`,kind,side,path})
 add('lifecycle','mine','/lifecycle/mine')
 if(!left&&(role==='NEW_HIRE'||role==='EMPLOYEE'))add('onboarding','mine','/onboarding/my')
 if(active){add('leave','mine','/leave/my');add('certificate','mine','/employment-certificates/my');add('access','mine','/platform-access/mine')}
 if(active&&(role==='HR'||role==='MANAGER')){add('leave','pending',`/leave/${role==='HR'?'hr':'manager'}/pending`);add('access','pending',role==='HR'?'/platform-access/hr/pending':'/platform-access/manager')}
 if(active&&role==='HR'){add('certificate','pending','/employment-certificates/hr/all');add('lifecycle','pending','/lifecycle/hr/queue');add('onboarding','pending','/onboarding/hr/pending')}
 return result
}
export function normalizeApproval(kind: ApprovalKind, r:any): ApprovalRow {
 const completed=kind==='certificate'?r.status==='GENERATED':kind==='lifecycle'?r.status==='COMPLETED':r.status==='APPROVED'
 let fields:Record<string,string>={},label='',summary='',opinion=r.hrOpinion||r.reviewOpinion||r.opinion||r.managerOpinion||''
 if(kind==='leave'){label=r.leaveTypeLabel||'请假申请';summary=`${r.startDate} 至 ${r.endDate} · ${r.days??''} 天`;fields={'申请说明':r.reason||'', '主管回复':r.managerOpinion||'', 'HR 回复':r.hrOpinion||''}}
 if(kind==='certificate'){label=r.certificateTypeLabel||'证明申请';summary=r.purpose||'';fields={'用途':r.purpose||'', '语言':r.languageLabel||'', '目的国家':r.destinationCountry||'', '受理机构':r.consulateName||'', '包含薪资':r.includeSalary?'是':'否','补充要求':r.remarks||'', '资料待补充':(r.missingProfileFields||[]).join('、'), '生成情况':r.generationError||''};const supplementLabels:Record<string,string>={legalName:'姓名',englishName:'英文姓名',department:'部门',title:'岗位',entryDate:'入职日期',passportNumber:'护照号码',passportExpiryDate:'护照有效期',monthlySalary:'月薪',currency:'币种'};for(const [key,value] of Object.entries(r.templateValues||{})){fields[(supplementLabels[key]||key)+'（员工补充，待核实）']=String(value)}}
 if(kind==='lifecycle'){label=r.label||'员工服务申请';summary='服务单 #'+r.id;fields=r.fields||{}}
 if(kind==='onboarding'){label='入职登记';summary=`${r.plannedEntryDate||''} · ${r.department||''}`;fields={'姓名':r.legalName||r.accountName||'', '部门':r.department||'', '岗位':r.positionTitle||'', '工作地':r.workLocation||'', '直属主管':r.managerName||'', '联系电话':r.phone||'', '学历':r.highestEducation||'', '身份证明准备':r.idDocumentPrepared?'已准备':'未准备','银行卡准备':r.bankCardPrepared?'已准备':'未准备','学历证明准备':r.educationCertificatePrepared?'已准备':'未准备','照片准备':r.photoPrepared?'已准备':'未准备','补充说明':r.remarks||''}}
 if(kind==='access'){label='平台权限申请';summary=[r.platformApi?'平台 API':'',r.agentApi?'Agent API':''].filter(Boolean).join('、');fields={'申请范围':summary,'申请原因':r.reason||'','直属主管':r.managerName||''}}
 return {key:`${kind}:${r.id}`,id:r.id,kind,label,employeeId:r.employeeId??r.newHireId,employeeName:r.employeeName||r.legalName||r.accountName||'',status:r.status,statusLabel:kind==='access'&&completed?'权限已开通':r.statusLabel||labels[r.status]||r.status,summary,opinion,fields,signature:JSON.stringify([r.status,r.managerReviewedAt,r.hrRecordedAt,r.reviewedAt,r.generatedAt,r.updatedAt]),complete:completed,raw:r}
}
export function visibleApprovals(source:ApprovalSource, rows:any[], session:AuthSession):ApprovalRow[]{
 const ownId=session.user.employeeProfileId??session.user.id
 return rows.filter(r=>!['DRAFT','CANCELLED'].includes(r.status))
 .filter(r=>source.kind!=='lifecycle'||(!r.leaveRequestId&&!r.certificateRequestId))
 .map(r=>normalizeApproval(source.kind,r))
 .filter(r=>source.side==='mine'||r.employeeId!==ownId)
 .filter(r=>source.side==='mine'||(r.kind==='lifecycle'?['SUBMITTED','APPROVED'].includes(r.status):r.kind==='certificate'?['PENDING_HR','APPROVED','GENERATION_FAILED'].includes(r.status):r.kind==='access'? (session.user.role==='MANAGER'?r.status==='PENDING_MANAGER':['PENDING','PENDING_HR'].includes(r.status)):true))
}
export type Decision = 'approve'|'reject'|'needs-info'|'complete'|'generate'
export function decisions(row:ApprovalRow,role:string):{value:Decision;label:string}[]{
 if(!['HR','MANAGER'].includes(role))return []
 if(role==='MANAGER'&&!['leave','access'].includes(row.kind))return []
 if(row.kind==='leave'&&row.status!==(role==='HR'?'PENDING_HR':'PENDING_MANAGER'))return []
 if(row.kind==='access'&&!(role==='HR'?['PENDING','PENDING_HR']:['PENDING_MANAGER']).includes(row.status))return []
 if(row.kind==='onboarding'&&row.status!=='PENDING_HR')return []
 if(row.kind==='lifecycle'&&!['SUBMITTED','APPROVED'].includes(row.status))return []
 if(row.kind==='certificate'&&!['PENDING_HR','APPROVED','GENERATION_FAILED'].includes(row.status))return []
 if(row.kind==='certificate'&&['APPROVED','GENERATION_FAILED'].includes(row.status))return [{value:'generate',label:'重新生成文件'}]
 if(row.kind==='lifecycle'&&row.status==='APPROVED')return [{value:'complete',label:'确认办结'},{value:'needs-info',label:'退回补充'}]
 return [{value:'approve',label:'审核通过'},{value:'reject',label:'驳回申请'},...(row.kind==='lifecycle'?[{value:'needs-info' as Decision,label:'退回补充'}]:[])]
}
export function reviewOperation(row:ApprovalRow,role:string,decision:Decision,opinion:string){
 if(!decisions(row,role).some(d=>d.value===decision))throw new Error('当前身份或申请状态不能执行该操作，请刷新后重试')
 const approved=decision==='approve'
 if(row.kind==='leave')return {path:role==='HR'?`/leave/hr/${row.id}/record`:`/leave/manager/${row.id}/review`,method:'PUT',body:{approved,opinion}}
 if(row.kind==='access')return {path:`/platform-access/${role==='HR'?'hr':'manager'}/${row.id}/review`,method:'PUT',body:{approved,opinion}}
 if(row.kind==='onboarding')return {path:`/onboarding/hr/${row.id}/review`,method:'PUT',body:{approved,opinion}}
 if(row.kind==='certificate')return decision==='generate'?{path:`/employment-certificates/hr/${row.id}/generate`,method:'POST',body:undefined}:{path:`/employment-certificates/hr/${row.id}/review`,method:'PUT',body:{approved,opinion}}
 return {path:`/lifecycle/hr/${row.id}/review`,method:'POST',body:{action:({approve:'APPROVED',reject:'REJECTED','needs-info':'NEEDS_INFO',complete:'COMPLETED'} as any)[decision],opinion}}
}
export function resultCopy(row:ApprovalRow):string {
 if(row.complete)return `${row.label}已经办理完成啦。${row.kind==='certificate'?'现在可以查看并下载文件。':'可以查看处理结果和回复。'}`
 if(row.status==='REJECTED')return `${row.label}暂未通过审核，可以看看回复，再决定下一步。`
 if(row.status==='NEEDS_INFO')return `${row.label}需要补充材料，查看经办人的说明后，我们接着办理。`
 if(row.status==='GENERATION_FAILED')return `${row.label}已通过审核，文件生成还需要处理，完成后会再提醒你。`
 if(row.status==='APPROVED')return `${row.label}已通过审核，正在继续办理，办结后会再提醒你。`
 return `${row.label}已送审啦。${row.status==='PENDING_MANAGER'?'正在等待主管审批。':row.status==='PENDING_HR'?'正在等待 HR 审核。':'正在等待经办人员审核。'}有新进度时会再提醒你。`
}
