const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
function client(fetch) {
  const window = new EventTarget();
  const ctx = vm.createContext({fetch, state:{csrf:null,user:{}}, window, CustomEvent, FormData});
  vm.runInContext(fs.readFileSync('../backend/src/main/resources/static/http.js','utf8'),ctx);
  return {api:vm.runInContext('api',ctx), csrf:vm.runInContext('csrf',ctx), window};
}
const response=(status,body)=>({status,ok:status>=200&&status<300,text:async()=>typeof body==='string'?body:JSON.stringify(body),json:async()=>body});
test('parallel mutations share CSRF acquisition and send same-origin credentials',async()=>{
 let tokens=0, writes=0;
 const c=client(async(path,options)=>{
  if(path.endsWith('/csrf')){tokens++;await new Promise(r=>setTimeout(r,10));return response(200,{headerName:'X-CSRF',token:'token'});}
  writes++; assert.equal(options.headers['X-CSRF'],'token');assert.equal(options.credentials,'same-origin');return response(204);
 });
 await Promise.all([c.api('/a',{method:'POST'}),c.api('/b',{method:'post'})]);assert.equal(tokens,1);assert.equal(writes,2);
});
test('expired session emits one event per response and preserves error status',async()=>{
 const c=client(async()=>response(401,{message:'Entre novamente'}));let expired=0;c.window.addEventListener('fintry:session-expired',()=>expired++);
 await assert.rejects(c.api('/a'),e=>e.status===401&&e.message==='Entre novamente');assert.equal(expired,1);
});
test('aborted reads do not become connectivity errors',async()=>{
 const aborted=new DOMException('cancelled','AbortError');const c=client(async()=>{throw aborted});await assert.rejects(c.api('/a'),e=>e===aborted);
});
test('malformed success, validation errors and unsafe paths are rejected',async()=>{
 const c=client(async()=>response(200,'<html>bad</html>'));await assert.rejects(c.api('/a'),/Resposta inválida/);await assert.rejects(c.api('//other.test'),/Caminho/);
 const invalid=client(async()=>response(422,{errors:{amount:'Valor inválido'}}));await assert.rejects(invalid.api('/a'),e=>e.status===422&&e.fields.amount==='Valor inválido');
});
test('failed CSRF is retryable and prevents an unprotected mutation',async()=>{
 let attempts=0;const c=client(async()=>++attempts===1?response(500,{}):response(200,{headerName:'X-CSRF',token:'ok'}));await assert.rejects(c.csrf());await c.csrf();assert.equal(attempts,2);
});
