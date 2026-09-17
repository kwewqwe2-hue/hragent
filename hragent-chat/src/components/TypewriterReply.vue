<template>
  <div class="reply-reveal" aria-live="off">
    <div ref="body" class="markdown-content" :aria-busy="revealing" @click="emit('link', $event)"></div>
    <div v-if="revealing" class="reveal-controls"><span aria-hidden="true">正在轻轻打字…</span><button type="button" @click="finish">立即显示</button></div>
    <span v-if="!revealing && animate" class="sr-only" role="status">回复已显示完整</span>
  </div>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
const props=defineProps<{html:string;animate:boolean}>()
const emit=defineEmits<{complete:[];progress:[];link:[event:MouseEvent]}>()
const body=ref<HTMLElement>(),revealing=ref(false)
let timer:ReturnType<typeof setTimeout>|undefined,alive=true,started=0,total=0,duration=0
let nodes:{node:Text;characters:string[];start:number}[]=[]
let motion:MediaQueryList|undefined
function finish(){
 clearTimeout(timer)
 if(body.value)body.value.innerHTML=props.html
 revealing.value=false;nodes=[]
 if(alive){emit('complete');emit('progress')}
}
function tick(){
 if(!alive||!revealing.value)return
 if(document.hidden||motion?.matches){finish();return}
 const count=Math.min(total,Math.max(1,Math.ceil((Date.now()-started)/duration*total)))
 for(const part of nodes)part.node.data=part.characters.slice(0,Math.max(0,count-part.start)).join('')
 emit('progress')
 if(count>=total){finish();return}
 timer=setTimeout(tick,32)
}
function start(){
 clearTimeout(timer);if(!body.value)return
 // html is already sanitized by the shared Markdown renderer. Reveal text nodes only,
 // keeping links, formatting and complete markup intact throughout the animation.
 body.value.innerHTML=props.html;nodes=[];total=0
 if(!props.animate||motion?.matches||document.hidden){finish();return}
 const walker=document.createTreeWalker(body.value,NodeFilter.SHOW_TEXT)
 while(walker.nextNode()){
  const node=walker.currentNode as Text,characters=Array.from(node.data)
  nodes.push({node,characters,start:total});total+=characters.length;node.data=''
 }
 if(!total){finish();return}
 duration=Math.min(7000,Math.max(350,total*24));started=Date.now();revealing.value=true;tick()
}
function accessibilityChanged(){if(revealing.value&&(document.hidden||motion?.matches))finish()}
onMounted(()=>{motion=window.matchMedia('(prefers-reduced-motion: reduce)');motion.addEventListener('change',accessibilityChanged);document.addEventListener('visibilitychange',accessibilityChanged);start()})
watch(()=>props.html,start)
watch(()=>props.animate,value=>{if(!value&&revealing.value)finish()})
onBeforeUnmount(()=>{alive=false;clearTimeout(timer);nodes=[];motion?.removeEventListener('change',accessibilityChanged);document.removeEventListener('visibilitychange',accessibilityChanged)})
</script>
<style scoped>
.reveal-controls{display:flex;gap:14px;align-items:center;margin:8px 0;color:#72998f;font-size:12px}.reveal-controls button{font:inherit;color:#347e6e;background:#f1f8f4;border:1px solid #d8eae2;border-radius:14px;padding:4px 10px;cursor:pointer}.markdown-content[aria-busy=true]{animation:reply-soft-in .22s ease-out}.sr-only{position:absolute;width:1px;height:1px;overflow:hidden;clip-path:inset(50%)}@keyframes reply-soft-in{from{opacity:.4;transform:translateY(3px)}to{opacity:1;transform:translateY(0)}}@media(prefers-reduced-motion:reduce){.markdown-content{animation:none!important}}
</style>
