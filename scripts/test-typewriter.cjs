// Component timer/lifecycle tests with a minimal text-node DOM; no browser or employee data.
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const vue=require('../hragent-chat/node_modules/vue'),ts=require('../hragent-chat/node_modules/typescript');
const source=fs.readFileSync('hragent-chat/src/components/TypewriterReply.vue','utf8').match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm,'')+'\nglobalThis.subject={body,revealing,finish};';
function fixture(animate=true,reduced=false){
 let mounted,unmounted,callback,now=0;const events=[];const props=vue.reactive({html:'<p>好呀🌱，日期是 <strong>2026-09-15</strong>，我陪你一起核对。</p>',animate});
 const element={nodes:[],saved:'',set innerHTML(value){this.saved=value;this.nodes=[{data:value.replace(/<[^>]*>/g,'')}]},get innerHTML(){return this.saved}};
 const motion={matches:reduced,addEventListener(){},removeEventListener(){}};
 const context={...vue,defineProps:()=>props,defineEmits:()=>name=>events.push(name),onMounted:f=>mounted=f,onBeforeUnmount:f=>unmounted=f,
  Date:{now:()=>now},setTimeout:f=>{callback=f;return 1},clearTimeout:()=>{callback=undefined},NodeFilter:{SHOW_TEXT:4},
  document:{hidden:false,addEventListener(){},removeEventListener(){},createTreeWalker(el){let i=-1;return {nextNode(){return ++i<el.nodes.length},get currentNode(){return el.nodes[i]}}}},window:{matchMedia:()=>motion}};
 vm.createContext(context);vm.runInContext(ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.None}}).outputText,context);
 context.subject.body.value=element;mounted();return {context,props,element,events,unmounted,advance(ms){now+=ms;callback?.()},get callback(){return callback}};
}
const a=fixture();assert.equal(a.context.subject.revealing.value,true);assert.ok(a.element.nodes[0].data.length<20);a.advance(300);assert.ok(a.element.nodes[0].data.length>1);a.context.subject.finish();assert.equal(a.element.innerHTML,a.props.html);assert.equal(a.context.subject.revealing.value,false);assert.equal(a.callback,undefined);
const b=fixture();b.advance(8000);assert.equal(b.context.subject.revealing.value,false);assert.equal(b.element.innerHTML,b.props.html);
for(const [animate,reduced] of [[false,false],[true,true]]){const c=fixture(animate,reduced);assert.equal(c.context.subject.revealing.value,false);assert.equal(c.element.innerHTML,c.props.html);assert.equal(c.callback,undefined)}
const d=fixture(),late=d.callback;d.unmounted();const prior=d.events.length;late();assert.equal(d.events.length,prior);assert.equal(d.callback,undefined);
console.log('PASS: incremental text, immediate reveal, completion, historical replies, reduced motion and unmount cleanup');
