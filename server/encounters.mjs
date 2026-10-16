import {randomUUID} from 'node:crypto';
const DAY=86400000;
const fail=(status,message)=>{throw Object.assign(new Error(message),{status})};
export const defaults=()=>({ghost:true,visibility:'everyone',messages:true,gameInvites:true,friendRequests:true,language:'en',strangerChats:false,strangerCalls:false});
export function createEncounters({db,clock,save,blocked}){
 db.encounters??=[];db.purchases??=[];
 const person=id=>db.users.find(u=>u.id===id);
 const settings=u=>({...defaults(),...u.settings});
 function balance(u){
  u.quota??={epoch:clock(),used:0,wallet:0,premiumUntil:0,trialUntil:0};const q=u.quota;
  if(clock()>=q.epoch+DAY){q.epoch+=Math.floor((clock()-q.epoch)/DAY)*DAY;q.used=0;}
  const cap=q.premiumUntil>clock()?50:10;return {daily:Math.max(0,cap-q.used),cap,wallet:q.wallet,total:Math.max(0,cap-q.used)+q.wallet,resetAt:q.epoch+DAY,premiumUntil:q.premiumUntil,trialUntil:q.trialUntil,serverTime:clock()};
 }
 const active=id=>db.encounters.find(e=>['connecting','active'].includes(e.status)&&e.members.includes(id));
 const occupied=id=>!!active(id)||db.calls.some(c=>c.status!=='ended'&&[c.from,c.to].includes(id)&&(c.status==='accepted'||clock()-c.created<60000));
 function end(e,reason='ended'){
  if(!e||!['active','connecting','pending'].includes(e.status))return;
  const charged=e.status==='active';for(const id of e.members){const u=person(id);if(u?.busySession===e.id)delete u.busySession;}e.status='ended';e.ended=clock();e.reason=reason;
  if(charged&&!e.charged){for(const id of e.members){const u=person(id);if(!u)continue;const b=balance(u);if(b.daily>0)u.quota.used++;else if(b.wallet>0)u.quota.wallet--;else u.quota.used++;}e.charged=true;}
  db.calls.filter(c=>c.encounter===e.id).forEach(c=>c.status='ended');save();
 }
 function sweep(){for(const e of db.encounters){if(e.status==='pending'&&clock()-e.created>60000)end(e,'expired before acceptance');else if(e.status==='connecting'&&clock()-e.started>30000)end(e,'setup failed');else if(e.status==='active'&&e.members.some(id=>clock()-(e.seen[id]||e.started)>90000))end(e,'connection lost');}}
 function invite(from,to,mode){sweep();const a=person(from),b=person(to);if(!a||!b||from===to||blocked(from,to))fail(403,'This meeting is unavailable');if(!['chat','video'].includes(mode))fail(400,'Choose chat or video');
  if(!settings(a)[mode==='chat'?'strangerChats':'strangerCalls']||!settings(b)[mode==='chat'?'strangerChats':'strangerCalls'])fail(403,'Both people must enable this kind of Discover connection');
  if(occupied(from)||occupied(to))fail(409,'One of you is busy in another connection');
  if(balance(a).total<1||balance(b).total<1)fail(402,'No chances available. Wait for your daily reload or visit the store.');
  if(db.encounters.some(e=>e.status==='pending'&&(e.from===from||e.to===to)))fail(409,'There is already a pending meeting request');
  const e={id:randomUUID(),from,to,members:[from,to],mode,status:'pending',created:clock(),accepted:[from],seen:{}};db.encounters.push(e);save();return e;
 }
 function accept(e,id){sweep();if(!e||e.status!=='pending'||e.to!==id)fail(409,'Meeting request is no longer available');
  for(const pid of e.members){const u=person(pid);if(!u||!settings(u)[e.mode==='chat'?'strangerChats':'strangerCalls']||occupied(pid)||balance(u).total<1)fail(409,'One of you is no longer available');}
  if(blocked(e.from,e.to))fail(403,'This connection is unavailable');
  e.status='connecting';for(const pid of e.members)person(pid).busySession=e.id;e.ready=[];e.accepted=[...e.members];e.started=clock();e.seen=Object.fromEntries(e.members.map(pid=>[pid,clock()]));
  const room={id:randomUUID(),name:'Discover connection',members:e.members,owner:e.from,group:false,encounter:e.id};db.rooms.push(room);e.room=room.id;
  if(e.mode==='video'){const call={id:randomUUID(),from:e.from,to:e.to,status:'accepted',created:clock(),encounter:e.id};db.calls.push(call);e.call=call.id;}
  // These mutations run synchronously in one server event loop, locking both users atomically.
  for(const other of db.encounters)if(other.id!==e.id&&other.status==='pending'&&other.members.some(p=>e.members.includes(p)))end(other,'busy');save();return e;
 }
 const view=(e,id)=>e?{id:e.id,from:e.from,to:e.to,mode:e.mode,status:e.status,room:e.room,call:e.call,other:person(e.members.find(p=>p!==id))?.username||'Friend',charged:!!e.charged}:null;
 function state(id){sweep();const u=person(id);balance(u);return {balance:balance(u),active:view(active(id),id),incoming:db.encounters.filter(e=>e.status==='pending'&&e.to===id).map(e=>view(e,id)),outgoing:db.encounters.filter(e=>e.status==='pending'&&e.from===id).map(e=>view(e,id))};}
 function open(e,id){if(!e||!e.members.includes(id)||e.status!=='connecting')return;e.ready=[...new Set([...e.ready,id])];if(e.ready.length===2){e.status='active';e.opened=clock();}save();}
 function reloadDaily(){let changed=false;for(const u of db.users){const before=JSON.stringify(u.quota);balance(u);if(JSON.stringify(u.quota)!==before)changed=true;}if(changed)save();}
 return {reloadDaily,balance,settings,active,occupied,end,sweep,invite,accept,open,state,view};
}
