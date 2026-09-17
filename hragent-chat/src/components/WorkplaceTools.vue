<template>
  <section class="wt-card" aria-labelledby="workplace-tools-title">
    <h3 id="workplace-tools-title">把职场用语说清楚</h3>
    <p>输入一个词或同事说的整句话，看看它在工作中是什么意思。</p>
    <form @submit.prevent="explain"><label>想理解的话<textarea v-model="phrase" maxlength="600" rows="2" placeholder="例如：先对齐目标，再把这件事闭环" required /></label><div class="wt-chips"><button v-for="word in ['对齐','闭环','颗粒度','抓手']" :key="word" type="button" @click="phrase = word; explain()">{{ word }}</button></div><button class="wt-primary">解释这句话</button></form>
    <div v-if="explained" class="wt-result" role="status">
      <template v-if="matches.length"><h4>换成通俗的话</h4><p>{{ plainPhrase }}</p><article v-for="item in matches" :key="item.word"><strong>{{ item.word }}：{{ item.meaning }}</strong><p>工作例子：{{ item.example }}</p><p>可以这样确认：“{{ item.ask }}”</p></article></template>
      <p v-else>暂未识别到常见术语。可以补充前后文，再让助手帮你理解；团队内部的缩写最好也向同事确认。</p>
      <form @submit.prevent="askAssistant"><label>补充上下文（可选）<textarea v-model="context" maxlength="250" rows="2" placeholder="例如：这是项目周会上，负责人对交付进度的要求" /></label><button class="wt-outline" :disabled="asking">{{ asking ? '正在解释…' : '让助手结合上下文解释' }}</button></form>
      <p v-if="assistantAnswer" class="wt-pre" role="status">{{ assistantAnswer }}</p><p v-if="error" role="alert">{{ error }}</p>
    </div>
  </section>
  <section class="wt-card" aria-labelledby="permission-title"><h3 id="permission-title">准备一份清楚的权限申请</h3><p>填写需要的工具和用途，生成可交给 IT 的申请说明。</p>
    <form @submit.prevent="generate"><div class="wt-grid"><label>系统或工具<input v-model.trim="permission.system" required maxlength="80" placeholder="例如：项目文档库" /></label><label>权限范围<select v-model="permission.scope"><option>只读</option><option>编辑</option><option>按岗位授权</option></select></label></div><label>工作用途<input v-model.trim="permission.purpose" required maxlength="200" placeholder="例如：查阅新项目的需求文档" /></label><label>使用期限<input v-model.trim="permission.duration" required maxlength="80" placeholder="例如：本月项目结束前" /></label><button class="wt-primary">生成申请说明</button></form>
    <div v-if="draft" class="wt-result"><pre>{{ draft }}</pre><button class="wt-outline" @click="downloadDraft">下载申请说明</button><p>这是申请草稿，尚未提交，也不会自动开通权限。请通过企业 IT 审批渠道办理。</p></div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, reactive } from 'vue'
const props=defineProps<{request:(path:string, options?:{method?:string;body?:unknown})=>Promise<any>}>()
const glossary=[
 {word:'对齐',meaning:'确认目标和理解一致',example:'开始写方案前，先确认交付内容、负责人和截止时间。',ask:'我们这次要交付什么，什么时候算完成？'},
 {word:'闭环',meaning:'跟进到完成，并反馈结果',example:'发现问题后，确认负责人、处理时间，再核实结果。',ask:'这件事由谁跟进，完成后在哪里反馈？'},
 {word:'颗粒度',meaning:'描述或拆解事情的细致程度',example:'把“完成上线”拆成测试、发布和验收等具体步骤。',ask:'需要细化到每个任务，还是列出主要阶段就可以？'},
 {word:'抓手',meaning:'可以采取的具体行动',example:'要减少返工，可以先增加一次需求确认。',ask:'我们准备先做哪一件具体的事？'},
 {word:'复盘',meaning:'回顾结果，找出原因和改进办法',example:'项目结束后一起讨论做得好的地方与下次的改进。',ask:'这次最值得保留和改进的各是什么？'},
 {word:'同步',meaning:'把进展或变化告诉相关同事',example:'需求变更后，在团队渠道说明影响和下一步安排。',ask:'需要告诉哪些人，用什么方式沟通？'},
 {word:'里程碑',meaning:'项目中需要检查的重要阶段成果',example:'把原型确认、测试完成和正式发布作为检查节点。',ask:'这个节点要完成哪些成果，由谁确认？'},
 {word:'优先级',meaning:'任务处理的先后顺序',example:'时间不够时，先确认哪个任务必须今天完成。',ask:'如果今天只能完成一项，应该先做哪项？'}
]
const phrase=ref(''), submitted=ref(''), context=ref(''), explained=ref(false), asking=ref(false), assistantAnswer=ref(''), error=ref('')
const matches=computed(()=>glossary.filter(item=>submitted.value.includes(item.word)))
const plainPhrase=computed(()=>{
 const sentence=submitted.value
  .replace(/对齐目标/g,'确认目标一致')
  .replace(/对齐需求/g,'确认需求和预期一致')
  .replace(/对齐理解/g,'确认理解一致')
  .replace(/把(问题|这件事|事情)闭环/g,'跟进$1直到解决，并反馈结果')
 return matches.value.reduce((text,item)=>text.split(item.word).join(item.meaning),sentence)
})
let replyVersion=0
function explain(){replyVersion++;asking.value=false;if(!phrase.value.trim())return;submitted.value=phrase.value.trim();explained.value=true;assistantAnswer.value='';error.value=''}
async function askAssistant(){if(asking.value)return;const version=++replyVersion;asking.value=true;error.value='';assistantAnswer.value='';try{const r=await props.request('/web-chat/messages',{method:'POST',body:{message:`请用通俗中文解释这句话的意思，并给我一句可向同事确认的话。只解释措辞，不开通任何权限。原话：${submitted.value}。上下文：${context.value||'未提供'}`}});if(version===replyVersion)assistantAnswer.value=r.answer}catch(e:any){if(version===replyVersion)error.value=e.response?.data?.message||e.message||'暂时无法解释，请稍后重试'}finally{if(version===replyVersion)asking.value=false}}
const permission=reactive({system:'',scope:'只读',purpose:'',duration:''}),draft=ref('')
function generate(){draft.value=`权限申请草稿\n系统 / 工具：${permission.system}\n工作用途：${permission.purpose}\n权限范围：${permission.scope}\n使用期限：${permission.duration}\n请确认审批人、所需材料和开通结果。`}
function downloadDraft(){const url=URL.createObjectURL(new Blob([draft.value],{type:'text/plain;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='权限申请说明.txt';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000)}
</script>
<style scoped>
.wt-card{border:1px solid #dfe8e2;border-radius:14px;padding:24px;margin:20px 0}.wt-card h3{margin:0 0 12px}.wt-card p{line-height:1.8;color:#64766d}.wt-card form{display:grid;gap:14px}.wt-card label{display:grid;gap:8px;font-size:14px}.wt-card input,.wt-card textarea,.wt-card select{font:inherit;width:100%;box-sizing:border-box;border:1px solid #cfddd4;border-radius:8px;padding:11px;background:#fbfdfb;color:#263c34}.wt-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.wt-chips{display:flex;gap:8px;flex-wrap:wrap}.wt-card button{font:inherit;cursor:pointer;border:1px solid #cbded1;border-radius:8px;padding:9px 14px;background:#f5faf6;color:#285f47;justify-self:start}.wt-card .wt-primary{background:#257958;color:#fff}.wt-card button:disabled{opacity:.6;cursor:wait}.wt-result{margin-top:20px;background:#f5faf6;border-radius:10px;padding:18px}.wt-result article{border-top:1px solid #dce7df;padding-top:14px;margin-top:14px}.wt-result h4{margin-top:0}.wt-card pre,.wt-pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;line-height:1.8}.wt-card :is(button,input,textarea,select):focus-visible{outline:3px solid #78b7a0;outline-offset:3px}@media(max-width:700px){.wt-card{padding:16px}.wt-grid{grid-template-columns:1fr}}
</style>
