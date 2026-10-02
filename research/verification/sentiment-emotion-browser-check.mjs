import {spawn} from 'node:child_process';
import {mkdtemp, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';

const profile=await mkdtemp(resolve(tmpdir(),'sakshi-sentiment-browser-'));
const child=spawn(process.env.CHROMIUM_PATH || 'chromium',['--headless','--no-sandbox','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{stdio:['ignore','ignore','pipe']});
const devtools=await new Promise((resolve,reject)=>{let output='';const timer=setTimeout(()=>reject(new Error('No DevTools endpoint')),20000);child.stderr.on('data',chunk=>{output+=chunk.toString();const match=output.match(/DevTools listening on (ws:\/\/[^\s]+)/);if(match){clearTimeout(timer);resolve(match[1]);}});child.on('error',reject);});
const socket=new WebSocket(devtools);
await new Promise((resolve,reject)=>{socket.addEventListener('open',resolve,{once:true});socket.addEventListener('error',reject,{once:true});});
let sequence=0;
const pending=new Map();
const exceptions=[];
const requests=[];
socket.addEventListener('message',event=>{const value=JSON.parse(event.data);if(value.id){const item=pending.get(value.id);if(!item)return;pending.delete(value.id);if(value.error)item.reject(new Error(JSON.stringify(value.error)));else item.resolve(value.result);}else{if(value.method==='Runtime.exceptionThrown')exceptions.push(value.params);if(value.method==='Network.requestWillBeSent')requests.push(value.params.request.url);}});
function send(method,params={},sessionId){return new Promise((resolve,reject)=>{const id=++sequence;pending.set(id,{resolve,reject});socket.send(JSON.stringify({id,method,params,...(sessionId?{sessionId}:{})}));});}
const {targetId}=await send('Target.createTarget',{url:'about:blank'});
const {sessionId}=await send('Target.attachToTarget',{targetId,flatten:true});
const call=(method,params={})=>send(method,params,sessionId);
await call('Page.enable');
await call('Runtime.enable');
await call('Network.enable');
async function evaluate(expression){const result=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});assert.equal(result.exceptionDetails,undefined,JSON.stringify(result.exceptionDetails));return result.result.value;}
const screenshotFiles=[];
const checks=[];
try{
for(const width of [1440,390]){
 await call('Emulation.setDeviceMetricsOverride',{width,height:1000,deviceScaleFactor:1,mobile:false});
 await call('Page.navigate',{url:pathToFileURL(resolve('.lavish/sakshi-sentiment-emotion-architecture.html')).href});
 await new Promise(resolve=>setTimeout(resolve,400));
 assert.equal(await evaluate('document.readyState'),'complete');
 assert.equal(await evaluate('document.querySelectorAll("#architecture-table tbody tr:not([hidden])").length'),4);
 for(const [query,count] of [['confidence-only',1],['independent signals',1],['no-match-query',0],['',4]]){
   await evaluate(`document.querySelector('#filter').value=${JSON.stringify(query)};document.querySelector('#filter').dispatchEvent(new Event('input'));`);
   assert.equal(await evaluate('document.querySelectorAll("#architecture-table tbody tr:not([hidden])").length'),count);
 }
 for(const fixture of ['anger','control','exposure','quote','language']){
   await evaluate(`document.querySelector('#fixture').value=${JSON.stringify(fixture)};document.querySelector('#fixture').dispatchEvent(new Event('change'));`);
   assert.equal(await evaluate('document.querySelector("#fixture-text").textContent.length>0'),true);
   assert.equal(await evaluate('document.querySelector("#fixture-route").textContent.includes("Suggested review behaviour")'),true);
 }
 await evaluate(`document.querySelector('#fixture').value='exposure';document.querySelector('#fixture').dispatchEvent(new Event('change'));`);
 assert.equal(await evaluate('document.querySelector("#fixture-scores").textContent.includes("0.000657")'),true);
 for(const theme of ['dark','light']){
   await evaluate(`if(document.documentElement.dataset.theme!==${JSON.stringify(theme)})document.querySelector('#theme').click();`);
   assert.equal(await evaluate('document.documentElement.scrollWidth<=innerWidth'),true,`${width} ${theme}`);
   assert.equal(await evaluate('document.querySelector("#theme").getAttribute("aria-pressed")'),String(theme==='light'));
   const overflow=await evaluate(`Array.from(document.querySelectorAll('svg text')).filter(t=>{const r=t.getBoundingClientRect(),s=t.closest('svg').getBoundingClientRect();return r.left<s.left-1||r.right>s.right+1||r.top<s.top-1||r.bottom>s.bottom+1;}).map(t=>t.textContent)`);
   assert.deepEqual(overflow,[]);
   const {data}=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});
   const path=resolve(profile,`sentiment-${theme}-${width}.png`);
   await writeFile(path,Buffer.from(data,'base64'));screenshotFiles.push(path);
   checks.push({width,theme,pageOverflow:false,svgTextOverflow:false});
 }
 assert.equal(await evaluate(`Array.from(document.querySelectorAll('nav a')).every(a=>document.querySelector(a.getAttribute('href'))!==null)`),true);
 assert.equal(await evaluate(`Array.from(document.querySelectorAll('details')).every(d=>{d.querySelector('summary').click();return d.open;})`),true);
 const ids=await evaluate('Array.from(document.querySelectorAll("[id]")).map(e=>e.id)');
 assert.equal(new Set(ids).size,ids.length);
}
assert.deepEqual(exceptions,[]);
assert.equal(requests.some(url=>/^https?:/.test(url)),false);
const result={checked_at:new Date().toISOString(),artifact:'.lavish/sakshi-sentiment-emotion-architecture.html',method:'Standalone headless Chromium CDP; in-app browser tool unavailable',checks,filterThemeDetailsAnchorsFixtureSelection:'passed',pageExceptions:0,remoteRequests:0,screenshotFiles};
await writeFile('research/verification/sentiment-emotion-browser.json',JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result,null,2));
}finally{
 await send('Browser.close');socket.close();
}
