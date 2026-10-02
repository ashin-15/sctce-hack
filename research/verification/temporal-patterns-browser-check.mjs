import {spawn} from 'node:child_process';
import {mkdtemp, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';

const profile=await mkdtemp(resolve(tmpdir(),'sakshi-temporal-browser-'));
const child=spawn(process.env.CHROMIUM_PATH || 'chromium',['--headless','--no-sandbox','--remote-debugging-port=0',`--user-data-dir=${profile}`,'about:blank'],{stdio:['ignore','ignore','pipe']});
const devtools=await new Promise((resolve,reject)=>{let output='';const timer=setTimeout(()=>{child.kill();reject(new Error(`No DevTools endpoint: ${output}`));},20000);child.stderr.on('data',chunk=>{output+=chunk.toString();const match=output.match(/DevTools listening on (ws:\/\/[^\s]+)/);if(match){clearTimeout(timer);resolve(match[1]);}});child.on('error',error=>{clearTimeout(timer);reject(error);});child.on('exit',code=>{clearTimeout(timer);reject(new Error(`Chromium exited ${code}: ${output}`));});});
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
 await call('Page.navigate',{url:pathToFileURL(resolve('.lavish/sakshi-temporal-patterns.html')).href});
 await new Promise(resolve=>setTimeout(resolve,400));
 assert.equal(await evaluate('document.readyState'),'complete');
 for(const theme of ['dark','light']){
   await evaluate(`if((document.documentElement.dataset.theme||'dark')!==${JSON.stringify(theme)})document.querySelector('#theme').click();`);
   for(const mode of ['selected','private','unknown']){
     await evaluate(`document.querySelector('#boundary-mode').value=${JSON.stringify(mode)};document.querySelector('#boundary-mode').dispatchEvent(new Event('change'));`);
     for(let count=0;count<=6;count++){
       await evaluate(`document.querySelector('#prefix').value=${count};document.querySelector('#prefix').dispatchEvent(new Event('input'));`);
       assert.equal(await evaluate('document.querySelectorAll("#timeline-a li").length'),count+1);
       assert.equal(await evaluate('document.querySelector("#contact-count").textContent'),`${count} retained ${count===1?'contact':'contacts'}`);
       assert.equal(await evaluate('document.querySelector("#prefix-value").textContent'),`${count} of 6`);
       const result=await evaluate('document.querySelector("#pattern-result").textContent');
       assert.ok(result.includes(count<3?'cue is not met':mode==='selected'?'after the selected stop-request':mode==='private'?'private disengagement':'no established after-boundary'));
       const span=await evaluate('document.querySelector("#contact-span").textContent');
       assert.ok(span.includes(count===0?'not a safety assessment':count===1?'no inter-contact span':`${[0,0,2,5,10,15,35][count]} minutes`));
     }
   }
   await evaluate("document.querySelector('#boundary-mode').value='selected';document.querySelector('#boundary-mode').dispatchEvent(new Event('change'));");
   const total=await evaluate('document.querySelectorAll("#algorithm-table tbody tr").length');
   assert.equal(total,10);
   for(const query of ['recurrence','graph','quadratic','no-match-query','']){
     await evaluate(`document.querySelector('#algorithm-filter').value=${JSON.stringify(query)};document.querySelector('#algorithm-filter').dispatchEvent(new Event('input'));`);
     const expected=await evaluate(`Array.from(document.querySelectorAll('#algorithm-table tbody tr')).filter(r=>r.textContent.toLocaleLowerCase().includes(${JSON.stringify(query)})).length`);
     assert.equal(await evaluate('document.querySelectorAll("#algorithm-table tbody tr:not([hidden])").length'),expected);
     assert.equal(await evaluate('document.querySelector("#algorithm-count").textContent'),`${expected} of ${total} algorithm rows`);
     assert.equal(query==='no-match-query'?expected===0:expected>0,true);
   }
   assert.equal(await evaluate('document.documentElement.scrollWidth<=innerWidth'),true,`${width} ${theme}`);
   assert.equal(await evaluate('document.querySelector("#theme").getAttribute("aria-pressed")'),String(theme==='light'));
   const overflow=await evaluate(`Array.from(document.querySelectorAll('svg text')).filter(t=>{const r=t.getBoundingClientRect(),s=t.closest('svg').getBoundingClientRect();return r.left<s.left-1||r.right>s.right+1||r.top<s.top-1||r.bottom>s.bottom+1;}).map(t=>t.textContent)`);
   assert.deepEqual(overflow,[]);
   await evaluate('scrollTo(0,0)');
   const {data}=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:true});
   const path=resolve(profile,`temporal-${theme}-${width}.png`);
   await writeFile(path,Buffer.from(data,'base64'));screenshotFiles.push(path);
   for(const region of ['header','replay']){
     await evaluate(region==='header'?'scrollTo(0,0)':"document.querySelector('#replay').scrollIntoView()");
     const shot=await call('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});
     const regionPath=resolve(profile,`temporal-${theme}-${width}-${region}.png`);
     await writeFile(regionPath,Buffer.from(shot.data,'base64'));screenshotFiles.push(regionPath);
   }
   checks.push({width,theme,pageOverflow:false,svgTextOverflow:false});
 }
 assert.equal(await evaluate(`Array.from(document.querySelectorAll('nav a')).every(a=>document.querySelector(a.getAttribute('href'))!==null)`),true);
 assert.equal(await evaluate(`Array.from(document.querySelectorAll('details')).every(d=>{const before=d.open;d.querySelector('summary').click();const toggled=d.open!==before;d.querySelector('summary').click();return toggled&&d.open===before;})`),true);
 const ids=await evaluate('Array.from(document.querySelectorAll("[id]")).map(e=>e.id)');
 assert.equal(new Set(ids).size,ids.length);
}
assert.deepEqual(exceptions,[]);
assert.equal(requests.some(url=>/^https?:/.test(url)),false);
const result={checked_at:new Date().toISOString(),artifact:'.lavish/sakshi-temporal-patterns.html',method:'Standalone headless Chromium CDP; in-app browser tool unavailable',checks,filterThemeDetailsAnchorsReplay:'passed', replayStatesPerViewportTheme:21,pageExceptions:0,remoteRequests:0,screenshotFiles};
await writeFile('research/verification/temporal-patterns-browser.json',JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result,null,2));
}finally{
 await send('Browser.close');socket.close();
}
