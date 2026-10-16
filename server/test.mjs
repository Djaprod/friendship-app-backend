import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp,rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { once } from 'node:events';
import { createArtiiServer } from './server.mjs';

test('account, friendship, chat, location and game socket authorization',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'artii-test-'));let now=Date.now();const server=createArtiiServer({file:join(dir,'db.json'),clock:()=>now});server.listen(0,'127.0.0.1');await once(server,'listening');const base=`http://127.0.0.1:${server.address().port}`;
 const clients=[];
 try{
 const request=async(path,token,method='GET',body)=>{const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:r.status,data:await r.json()}};
 const accounts=[];for(let i=0;i<3;i++){const r=await request('/auth/register',null,'POST',{email:`user${i}@example.com`,password:'StrongTestPass123',name:`User ${i}`,username:`user_${i}`,adult:true});assert.equal(r.status,200);accounts.push(r.data)}const [a,b,c]=accounts;
 assert.equal((await request('/state')).status,401);
 assert.equal((await request('/rooms',a.token,'POST',{user:b.user.id})).status,403);
 await request('/friends/request',a.token,'POST',{user:b.user.id});const incoming=(await request('/state',b.token)).data.requests[0];assert.equal((await request('/friends/respond',c.token,'POST',{request:incoming.id,accept:true})).status,404);await request('/friends/respond',b.token,'POST',{request:incoming.id,accept:true});
 const room=(await request('/rooms',a.token,'POST',{user:b.user.id})).data.room;
 assert.equal((await request(`/rooms/${room.id}/messages`,c.token)).status,403);await request(`/rooms/${room.id}/messages`,a.token,'POST',{text:'Hello!'});assert.equal((await request(`/rooms/${room.id}/messages`,b.token)).data.messages[0].text,'Hello!');
 assert.equal((await request('/location',a.token,'PUT',{lat:10,lon:20,audience:[c.user.id],expires:now+60000})).status,403);
 await request('/settings',a.token,'PUT',{ghost:false});
 await request('/location',a.token,'PUT',{lat:10,lon:20,audience:[b.user.id],expires:now+60000});assert.equal((await request('/state',b.token)).data.locations.length,1);assert.equal((await request('/state',c.token)).data.locations.length,0);
 now+=61000;assert.equal((await request('/state',b.token)).data.locations.length,0);
 const websocket=async token=>{const ws=new WebSocket(base.replace('http','ws')+'/games/socket');clients.push(ws);await new Promise((r,j)=>{ws.onopen=r;ws.onerror=j});const inbox=[];ws.onmessage=e=>inbox.push(JSON.parse(e.data));const wait=async type=>{for(let i=0;i<200;i++){const index=inbox.findIndex(x=>x.type===type);if(index>=0)return inbox.splice(index,1)[0];await new Promise(r=>setTimeout(r,10))}throw Error('Timed out waiting for '+type)};ws.send(JSON.stringify({type:'hello',token}));await wait('ready');return {ws,wait,inbox}};
 const x=await websocket(a.token),y=await websocket(b.token);x.ws.send(JSON.stringify({type:'create',game:'ludo'}));let state=(await x.wait('state')).game;assert.match(state.code,/^[A-Z]{4}$/);y.ws.send(JSON.stringify({type:'join',code:state.code}));state=(await x.wait('state')).game;await y.wait('state');x.ws.send(JSON.stringify({type:'action',action:'start',version:state.version}));state=(await x.wait('state')).game;assert.equal(state.phase,'playing');const ys=(await y.wait('state')).game;assert.equal(ys.my.id,b.user.id);assert.equal(state.my.id,a.user.id);
 const z=await websocket(c.token);z.ws.send(JSON.stringify({type:'join',code:state.code}));assert.match((await z.wait('error')).message,/started/);
 y.ws.send(JSON.stringify({type:'hello',token:c.token}));assert.match((await y.wait('error')).message,/already signed in/);
 x.ws.close();const re=await websocket(a.token);re.ws.send(JSON.stringify({type:'join',code:state.code}));const resumed=(await re.wait('state')).game;assert.equal(resumed.id,state.id);assert.equal(resumed.turn,state.turn);
 await request('/block',b.token,'POST',{user:a.user.id});assert.equal((await request(`/rooms/${room.id}/messages`,a.token)).status,403);assert.equal((await request('/state',b.token)).data.friends.length,0);
 }finally{for(const c of clients)c.close();server.closeAllConnections();await new Promise(r=>server.close(r));await rm(dir,{recursive:true,force:true});}
});
