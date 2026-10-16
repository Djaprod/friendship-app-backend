import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,readFile,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {once} from 'node:events';
import {createArtiiServer} from './server.mjs';

test('live chat presence, privacy, receipt ordering, mutations and protected media',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'artii-messaging-'));const file=join(dir,'db.json');
 let now=1800000000000;const server=createArtiiServer({file,clock:()=>now});server.listen(0,'127.0.0.1');await once(server,'listening');
 const base='http://127.0.0.1:'+server.address().port,sockets=[];
 async function request(token,path,method='GET',body){const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:body?JSON.stringify(body):undefined});return {status:r.status,...await r.json()};}
 async function account(name){return request('', '/auth/register','POST',{email:name+'@example.com',username:name,password:'ExamplePassword123!',adult:true});}
 async function live(account){
  const ws=new WebSocket(base.replace('http','ws')+'/chat/socket');sockets.push(ws);const events=[];ws.addEventListener('message',e=>events.push(JSON.parse(e.data)));await once(ws,'open');ws.send(JSON.stringify({type:'hello',token:account.token,focused:true}));await wait(()=>events.some(x=>x.type==='ready'));return {ws,events};
 }
 async function wait(fn){for(let i=0;i<100;i++){if(await fn())return;await new Promise(r=>setTimeout(r,10));}throw Error('Live update timed out');}
 try{
  const a=await account('chat_alice'),b=await account('chat_bob'),c=await account('chat_carol');
  await request(a.token,'/friends/request','POST',{user:b.user.id});const pending=await request(b.token,'/state');await request(b.token,'/friends/respond','POST',{request:pending.requests[0].id,accept:true});
  const state=await request(a.token,'/state'),room=state.rooms[0].id;
  const al=await live(a),bl=await live(b);
  await wait(async()=>(await request(a.token,'/state')).friends[0].presence.online);
  now+=1234;bl.ws.send(JSON.stringify({type:'focus',focused:false}));
  await wait(async()=>!(await request(a.token,'/state')).friends[0].presence.online);
  assert.equal((await request(a.token,'/state')).friends[0].presence.lastSeen,now);
  await request(b.token,'/settings','PUT',{visibility:'nobody'});
  assert.equal((await request(a.token,'/state')).friends[0].presence,null);
  await request(b.token,'/settings','PUT',{visibility:'friends'});
  bl.ws.send(JSON.stringify({type:'focus',focused:true}));
  await wait(async()=>(await request(a.token,'/state')).friends[0].presence.online);
  const second=await live(b);
  bl.ws.send(JSON.stringify({type:'focus',focused:false}));
  await new Promise(r=>setTimeout(r,40));
  assert.equal((await request(a.token,'/state')).friends[0].presence.online,true,'Another focused device keeps user online');
  now+=1000;second.ws.send(JSON.stringify({type:'focus',focused:false}));
  await wait(async()=>!(await request(a.token,'/state')).friends[0].presence.online);
  const focusLost=now;now+=1000;second.ws.close();await once(second.ws,'close');
  assert.equal((await request(a.token,'/state')).friends[0].presence.lastSeen,focusLost,'Socket close must not replace earlier focus-loss time');
  bl.ws.send(JSON.stringify({type:'focus',focused:true}));
  await wait(async()=>(await request(a.token,'/state')).friends[0].presence.online);
  const before=bl.events.length;const started=Date.now();
  const sent=await request(a.token,'/rooms/'+room+'/messages','POST',{text:'Secret message'});
  assert.equal(sent.status,200);const id=sent.message.id;
  await wait(()=>bl.events.slice(before).some(x=>x.type==='sync'));
  assert.ok(Date.now()-started<1000,'Event publication should be sub-second in local test');
  let messages=(await request(b.token,'/rooms/'+room+'/messages')).messages;
  assert.deepEqual(messages[0].delivered,[],'Fetching must not fabricate delivery');
  assert.equal((await request(b.token,'/inbox')).messages[0].id,id);
  await request(b.token,'/rooms/'+room+'/receipts','POST',{ids:[id],read:false});
  messages=(await request(a.token,'/rooms/'+room+'/messages')).messages;
  assert.deepEqual(messages[0].delivered,[b.user.id]);assert.deepEqual(messages[0].read,[]);
  await request(b.token,'/rooms/'+room+'/receipts','POST',{ids:[id],read:true});
  await request(b.token,'/rooms/'+room+'/receipts','POST',{ids:[id],read:false});
  assert.deepEqual((await request(a.token,'/rooms/'+room+'/messages')).messages[0].read,[b.user.id]);
  assert.equal((await request(c.token,'/rooms/'+room+'/receipts','POST',{ids:[id],read:true})).status,403);
  assert.equal((await request(c.token,'/messages/'+id+'/reaction','POST',{emoji:'❤️'})).status,403);
  assert.equal((await request(b.token,'/messages/'+id+'/delete','POST')).status,403);
  await request(a.token,'/messages/'+id+'/reaction','POST',{emoji:'❤️'});
  await request(b.token,'/messages/'+id+'/reaction','POST',{emoji:'❤️'});
  assert.equal((await request(a.token,'/rooms/'+room+'/messages')).messages[0].reactions[0].count,2);
  await request(b.token,'/messages/'+id+'/reaction','POST',{emoji:'❤️'});
  assert.equal((await request(a.token,'/rooms/'+room+'/messages')).messages[0].reactions[0].count,1);
  assert.equal((await request(a.token,'/messages/'+id+'/reaction','POST',{emoji:'bad'})).status,400);
  const image=Buffer.from([137,80,78,71,13,10,26,10,0,0,0,0]).toString('base64');
  const media=await request(a.token,'/rooms/'+room+'/messages','POST',{attachment:{mime:'image/png',data:image}});
  assert.equal(media.status,200);
  const mediaPath='/media/'+media.message.attachment.id;
  assert.equal((await fetch(base+mediaPath,{headers:{Authorization:'Bearer '+b.token}})).status,200);
  assert.equal((await fetch(base+mediaPath,{headers:{Authorization:'Bearer '+c.token}})).status,403);
  assert.equal((await request(a.token,'/rooms/'+room+'/messages','POST',{attachment:{mime:'image/png',data:Buffer.from('not png').toString('base64')}})).status,400);
  await request(a.token,'/messages/'+media.message.id+'/delete','POST');
  assert.equal((await fetch(base+mediaPath,{headers:{Authorization:'Bearer '+b.token}})).status,404);
  await request(a.token,'/messages/'+id+'/delete','POST');
  await request(a.token,'/messages/'+id+'/delete','POST');
  const tombstone=(await request(b.token,'/rooms/'+room+'/messages')).messages.find(m=>m.id===id);
  assert.equal(tombstone.deleted,true);assert.equal(tombstone.text,'');assert.deepEqual(tombstone.reactions,[]);
  assert.equal((await request(b.token,'/messages/'+id+'/reaction','POST',{emoji:'👍'})).status,409);
  assert.ok(!(await readFile(file,'utf8')).includes('Secret message'),'Deleted text is erased from persisted record');

  await request(a.token,'/settings','PUT',{strangerChats:true,strangerCalls:true});
  await request(b.token,'/settings','PUT',{strangerChats:true,strangerCalls:true});
  assert.equal((await request(a.token,'/state')).friends[0].discoverOnline,true);
  const meetingRequest=await request(a.token,'/discover/invite','POST',{user:b.user.id,mode:'chat',consent:true});assert.equal(meetingRequest.status,200);
  await request(b.token,'/settings','PUT',{strangerChats:false,strangerCalls:false});
  assert.equal((await request(a.token,'/state')).friends[0].discoverOnline,false);
  assert.equal((await request(a.token,'/state')).discovery.outgoing.length,0);
  assert.equal((await request(a.token,'/state')).discovery.balance.daily,10);
  await request(b.token,'/settings','PUT',{strangerChats:true});
  bl.ws.send(JSON.stringify({type:'focus',focused:false}));
  await wait(async()=>!(await request(a.token,'/state')).friends[0].discoverOnline);
  assert.equal((await request(a.token,'/discover/invite','POST',{user:b.user.id,mode:'chat',consent:true})).status,409);
  bl.ws.send(JSON.stringify({type:'focus',focused:true}));
  const large=Buffer.alloc(9*1024*1024);Buffer.from([0,0,0,24,102,116,121,112]).copy(large);
  const upload=await fetch(base+'/rooms/'+room+'/media',{method:'POST',headers:{Authorization:'Bearer '+a.token,'X-Media-Type':'audio/mp4'},body:large});
  assert.equal(upload.status,200);const uploaded=await upload.json();
  assert.equal((await request(b.token,'/rooms/'+room+'/messages','POST',{attachment:{uploadId:uploaded.uploadId}})).status,403,'Staged upload belongs to its owner');
  const voice=await request(a.token,'/rooms/'+room+'/messages','POST',{attachment:{uploadId:uploaded.uploadId,duration:3600000,waveform:[0,.5,1]}});
  assert.equal(voice.status,200);assert.equal(voice.message.attachment.size,large.length);assert.equal(voice.message.attachment.duration,3600000);
  assert.equal((await request(a.token,'/rooms/'+room+'/messages','POST',{attachment:{uploadId:uploaded.uploadId}})).status,403,'Upload can be attached once');
  const forbidden=await fetch(base+'/rooms/'+room+'/media',{method:'POST',headers:{Authorization:'Bearer '+c.token,'X-Media-Type':'image/png'},body:Buffer.from('bad')});assert.equal(forbidden.status,403);
  await request(a.token,'/block','POST',{user:b.user.id});
  assert.equal((await request(b.token,'/rooms/'+room+'/messages')).status,403);
 }finally{
  await Promise.all(sockets.map(ws=>new Promise(resolve=>{if(ws.readyState===3)return resolve();ws.addEventListener('close',resolve,{once:true});ws.close();setTimeout(resolve,1000).unref();})));
  server.closeAllConnections();await new Promise(r=>server.close(r));await rm(dir,{recursive:true,force:true});
 }
});
