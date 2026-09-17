from pathlib import Path
root=Path(__file__).resolve().parents[1]
for folder in ['hragentv1/frontend/src/components','hragent-chat/src/components']:
 p=root/folder/'EmployeeRelations.vue';t=p.read_text(encoding='utf-8')
 t=t.replace('@change="toggleTask(task)"','@change="toggleTask(task, $event)"')
 t=t.replace('async function toggleTask(task: Task) { await run', 'async function toggleTask(task: Task, event: Event) { (event.target as HTMLInputElement).checked = task.done; await run')
 # The new interactive care workspace replaces the previous static introductory card.
 start=t.find('        <article class="er-care-message">')
 if start>=0:t=t[:start]+t[t.index('</article>',start)+len('</article>'):]
 p.write_text(t,encoding='utf-8')
 p=root/folder/'CareWorkspace.vue';t=p.read_text(encoding='utf-8')
 t=t.replace('function clearPlan(){','let replyVersion=0\nfunction clearPlan(){replyVersion++;sending.value=false;')
 t=t.replace('if(sending.value)return;sending.value=true;', 'if(sending.value)return;const version=++replyVersion;sending.value=true;')
 t=t.replace('answer.value=result.answer}catch(e:any){error.value=', 'if(version===replyVersion)answer.value=result.answer}catch(e:any){if(version===replyVersion)error.value=')
 t=t.replace('finally{sending.value=false}', 'finally{if(version===replyVersion)sending.value=false}')
 t=t.replace('需要人工或专业支持时，可使用下方企业渠道。','涉及紧急安全风险或实际劳动争议时，助手可能转入人工支持流程。需要专业支持时，可使用下方企业渠道。')
 p.write_text(t,encoding='utf-8')
 p=root/folder/'WorkplaceTools.vue';t=p.read_text(encoding='utf-8')
 t=t.replace('function explain(){', 'let replyVersion=0\nfunction explain(){replyVersion++;asking.value=false;')
 t=t.replace('if(asking.value)return;asking.value=true;', 'if(asking.value)return;const version=++replyVersion;asking.value=true;')
 t=t.replace('assistantAnswer.value=r.answer}catch(e:any){error.value=', 'if(version===replyVersion)assistantAnswer.value=r.answer}catch(e:any){if(version===replyVersion)error.value=')
 t=t.replace('finally{asking.value=false}', 'finally{if(version===replyVersion)asking.value=false}')
 p.write_text(t,encoding='utf-8')
