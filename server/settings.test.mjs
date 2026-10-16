import test from 'node:test';import assert from 'node:assert/strict';import {mkdtemp,rm,readFile} from 'node:fs/promises';import {tmpdir} from 'node:os';import {join} from 'node:path';import {once} from 'node:events';import {createArtiiServer} from './server.mjs';
test('settings, privacy, editable usernames, consent routes, stale locations and deletion are enforced by API',async()=>{
 const sockets=[];
 const dir=await mkdtemp(join(tmpdir(),'artii-settings-')),file=join(dir,'db.json');let now=Date.now();const server=createArtiiServer({file,clock:()=>now});server.listen(0,'127.0.0.1');await once(server,'listening');const base='http://127.0.0.1:'+server.address().port;
 async function req(path,token,body,method){const r=await fetch(base+path,{method:method||(body?'POST':'GET'),headers:{'Content-Type':'application/json',...(token?{Authorization:'Bearer '+token}:{})},...(body?{body:JSON.stringify(body)}:{})});return {status:r.status,data:await r.json()}}
 try{
  const users=[];for(const n of ['alice','bobby','chris'])users.push((await req('/auth/register',null,{email:n+'@example.com',username:n,password:'StrongTesting123!',adult:true})).data);const [a,b,c]=users;
  for(const account of [a,b]){const ws=new WebSocket(base.replace('http','ws')+'/chat/socket');sockets.push(ws);await once(ws,'open');const ready=new Promise(resolve=>ws.addEventListener('message',event=>{if(JSON.parse(event.data).type==='ready')resolve();}));ws.send(JSON.stringify({type:'hello',token:account.token,focused:true}));await ready;}
  assert.equal((await req('/profile',a.token,{username:'BOBBY',name:'Changed'},'PUT')).status,409);assert.equal((await req('/state',a.token)).data.me.name,'alice');
  assert.equal((await req('/profile',a.token,{username:'alice_new',name:'Alice',bio:'x'.repeat(200)},'PUT')).data.user.bio.length,150);
  await req('/settings',b.token,{visibility:'nobody',messages:false,gameInvites:false,friendRequests:false,language:'fr'},'PUT');assert.equal((await req('/people/by-username?username=bobby',a.token)).status,404);assert.equal((await req('/state',a.token)).data.discover.some(u=>u.id===b.user.id),false);
  assert.equal((await req('/settings',b.token)).data.settings.language,'fr');assert.equal((await req('/settings',b.token)).data.settings.gameInvites,false);
  await req('/settings',b.token,{visibility:'everyone',strangerChats:true},'PUT');await req('/settings',a.token,{strangerChats:true},'PUT');
  assert.equal((await req('/rooms',a.token,{user:b.user.id})).status,403);assert.equal((await req('/calls',a.token,{user:b.user.id})).status,403);
  const invite=await req('/discover/invite',a.token,{user:b.user.id,mode:'chat',consent:true});assert.equal(invite.status,200);const id=invite.data.meeting.id;
  assert.equal((await req('/discover/'+id+'/accept',c.token,{consent:true})).status,404);assert.equal((await req('/discover/'+id+'/accept',b.token,{consent:true})).data.meeting.status,'connecting');
  await req('/discover/'+id+'/ready',a.token,{});const active=await req('/discover/'+id+'/ready',b.token,{});assert.equal(active.data.meeting.status,'active');
  assert.equal((await req('/friends/request',c.token,{user:a.user.id})).status,409);
  const room=active.data.meeting.room;assert.equal((await req('/rooms/'+room+'/messages',a.token,{text:'Hello'})).status,200);assert.equal((await req('/rooms/'+room+'/messages',c.token)).status,403);
  await req('/discover/'+id+'/end',b.token,{});assert.equal((await req('/discover/'+id+'/end',a.token,{})).data.balance.daily,9);assert.equal((await req('/state',b.token)).data.discovery.balance.daily,9);assert.equal((await req('/rooms/'+room+'/messages',a.token,{text:'Not allowed'})).status,403);
  await req('/friends/request',a.token,{user:b.user.id});const incoming=(await req('/state',b.token)).data.requests[0];await req('/friends/respond',b.token,{request:incoming.id,accept:true});
  await req('/settings',a.token,{ghost:false},'PUT');await req('/location',a.token,{lat:10,lon:20,expires:now+600000,audience:[b.user.id]},'PUT');now+=61000;assert.equal((await req('/state',b.token)).data.locations.length,1);assert.equal((await req('/state',c.token)).data.locations.length,0);
  await req('/settings',a.token,{ghost:true},'PUT');assert.equal((await req('/state',b.token)).data.locations.length,0);assert.equal((await req('/location',a.token,{lat:10,lon:20,expires:now+600000,audience:[b.user.id]},'PUT')).status,403);
  await req('/block',a.token,{user:c.user.id});assert.equal((await req('/settings',a.token)).data.blocked.length,1);await req('/block',a.token,{user:c.user.id},'DELETE');assert.equal((await req('/settings',a.token)).data.blocked.length,0);
  const friendRoom=(await req('/rooms',a.token,{user:b.user.id})).data.room;await req('/rooms/'+friendRoom.id+'/messages',b.token,{text:'Keep my message'});
  assert.equal((await req('/account',a.token,{confirm:'wrong'},'DELETE')).status,400);assert.equal((await req('/account',a.token,{confirm:'DELETE'},'DELETE')).status,200);assert.equal((await req('/state',a.token)).status,401);
  const db=JSON.parse(await readFile(file));assert.equal(db.users.some(u=>u.id===a.user.id),false);assert.ok(db.messages.some(x=>x.text==='Keep my message'));assert.equal(db.sessions.some(x=>x.user===a.user.id),false);
 }finally{await Promise.all(sockets.map(ws=>new Promise(resolve=>{if(ws.readyState===3)return resolve();ws.addEventListener('close',resolve,{once:true});ws.close();setTimeout(resolve,1000).unref();})));server.closeAllConnections();await new Promise(r=>server.close(r));await rm(dir,{recursive:true,force:true})}
});
