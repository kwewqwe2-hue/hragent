<template>
  <section class="cw-card"><h3>先整理一件困扰你的事</h3><p>可以只写愿意分享的部分。下面的整理在当前页面完成，不会自动提交给 HR。</p>
    <form @submit.prevent="makePlan"><label>更接近哪种情况？<select v-model="topic"><option>事情太多，不知道先做什么</option><option>和同事沟通不顺畅</option><option>刚加入团队，还不太适应</option></select></label><label>现在最困扰你的事<textarea v-model.trim="situation" rows="3" maxlength="500" required placeholder="例如：三个任务都要求今天交付，我不知道怎么安排" /></label><label>你希望先改变什么？<input v-model.trim="goal" maxlength="200" placeholder="例如：和负责人确认今天的首要任务" /></label><button class="cw-primary">整理下一步</button></form>
    <article v-if="plan" class="cw-plan" role="status"><h4>先从这一步开始</h4><p>{{ plan.situation }}</p><ol><li v-for="step in plan.steps" :key="step">{{ step }}</li></ol><h4>可以这样开口</h4><p>{{ plan.opening }}</p><button @click="clearPlan">清空本页记录</button></article>
  </section>
  <section class="cw-card"><h3>和助手聊一聊</h3><p>工作安排、人际沟通或情绪上的困扰，都可以在这里聊。可以一起想想下一步，也可以先倾诉，不急着找答案。只分享你愿意说的部分，需要专业支持时可查看下方渠道。</p>
    <form @submit.prevent="send"><label>想咨询的问题<textarea v-model.trim="question" rows="3" maxlength="800" required placeholder="例如：怎么和主管沟通工作安排，让彼此的预期更清楚？" /></label><button class="cw-primary" :disabled="sending">{{ sending ? '正在回复…' : '发送咨询' }}</button></form><p v-if="error" role="alert">{{ error }}</p><div v-if="answer" class="cw-answer" role="status">{{ answer }}</div>
  </section>
</template>
<script setup lang="ts">
import {ref} from 'vue'
const props=defineProps<{request:(path:string, options?:{method?:string;body?:unknown})=>Promise<any>}>()
const topic=ref('事情太多，不知道先做什么'),situation=ref(''),goal=ref(''),plan=ref<{situation:string;steps:string[];opening:string}>()
function makePlan(){const steps=topic.value.includes('事情太多')?['列出正在处理的任务和各自截止时间。','和负责人确认最重要的一项，并说明其他任务需要的时间。','先完成一个可交付的小步骤，再同步进展。']:topic.value.includes('沟通')?['写下具体发生的事，先区分事实与自己的感受。','找一个方便沟通的时间，说明影响和希望调整的地方。','确认双方同意的下一步，以及后续沟通时间。']:['列出目前不清楚的流程、联系人或协作习惯。','选一位导师或同事，先请教最影响工作的一件事。','把确认过的做法记下来，逐步形成自己的入职笔记。'];plan.value={situation:`你现在想处理的是：${situation.value}`,steps:goal.value?[`你希望先做到：${goal.value}`, ...steps]:steps,opening:`我最近遇到“${situation.value}”。${goal.value?`我希望先做到“${goal.value}”。`:''}方便一起确认一个可行的下一步吗？`}}
let replyVersion=0
function clearPlan(){replyVersion++;sending.value=false;plan.value=undefined;situation.value='';goal.value='';question.value='';answer.value='';error.value=''}
const question=ref(''),answer=ref(''),sending=ref(false),error=ref('')
async function send(){if(sending.value)return;const version=++replyVersion;sending.value=true;answer.value='';error.value='';try{const result=await props.request('/web-chat/messages',{method:'POST',body:{message:question.value}});if(version===replyVersion)answer.value=result.answer}catch(e:any){if(version===replyVersion)error.value=e.response?.data?.message||e.message||'暂时无法回复，请稍后重试'}finally{if(version===replyVersion)sending.value=false}}
</script>
<style scoped>
.cw-card{padding:24px;border:1px solid #dfe8e2;border-radius:14px;margin:20px 0}.cw-card p,.cw-card li{line-height:1.8;color:#64766d}.cw-card form{display:grid;gap:14px}.cw-card label{display:grid;gap:8px}.cw-card :is(input,textarea,select){font:inherit;padding:11px;border:1px solid #cfddd4;border-radius:8px;background:#fbfdfb;color:#263c34;box-sizing:border-box;width:100%}.cw-card button{font:inherit;padding:10px 16px;border:1px solid #cbded1;border-radius:8px;cursor:pointer;justify-self:start;background:#fff;color:#285f47}.cw-card .cw-primary{background:#257958;color:white}.cw-card button:disabled{opacity:.6;cursor:wait}.cw-plan,.cw-answer{margin-top:20px;padding:18px;background:#f5faf6;border-radius:10px;overflow-wrap:anywhere}.cw-answer{white-space:pre-wrap;line-height:1.9}.cw-card :is(button,input,textarea,select):focus-visible{outline:3px solid #78b7a0;outline-offset:3px}@media(max-width:700px){.cw-card{padding:16px}}
</style>
