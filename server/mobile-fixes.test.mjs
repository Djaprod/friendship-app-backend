import test from 'node:test';
import assert from 'node:assert/strict';
import {once} from 'node:events';
import {createServer} from 'node:http';
import {attachGames} from './game-network.mjs';
import {createMessaging} from './messaging.mjs';

test('message retries are idempotent and scoped to sender and room',()=>{
 const db={messages:[],rooms:[{id:'r'},{id:'other'}]};let published=0;
 const messaging=createMessaging({db,clock:()=>1,save(){},fail(code,message){throw Error(message)},safeRoom:()=>true,mediaDir:'.',publish(){published++}});
 const first=messaging.create(db.rooms[0],'a','Alice',{text:'Hello',clientId:'retry-1'});
 assert.equal(messaging.create(db.rooms[0],'a','Alice',{text:'Hello',clientId:'retry-1'}).id,first.id);
 assert.equal(db.messages.length,1);assert.equal(published,1);
 assert.notEqual(messaging.create(db.rooms[0],'b','Bob',{text:'Hello',clientId:'retry-1'}).id,first.id);
 assert.notEqual(messaging.create(db.rooms[1],'a','Alice',{text:'Hello',clientId:'retry-1'}).id,first.id);
});

test('leaving rooms releases capacity, transfers host, and rejects nonmembers',async()=>{
 const server=createServer();const db={games:[]};const sockets=[];
 attachGames(server,{db,save(){},authenticate:token=>['alice','bob','outsider'].includes(token)?{id:token,name:token}:null,blocked:()=>false});
 server.listen(0,'127.0.0.1');await once(server,'listening');
 async function connect(token){const ws=new WebSocket('ws://127.0.0.1:'+server.address().port+'/games/socket');sockets.push(ws);const queue=[];ws.addEventListener('message',e=>queue.push(JSON.parse(e.data)));await once(ws,'open');return {async request(data,type){queue.length=0;ws.send(JSON.stringify(data));for(let i=0;i<200;i++){const value=queue.find(m=>m.type===type||m.type==='error');if(value)return value;await new Promise(r=>setTimeout(r,5))}throw Error('No response')},ws};}
 try{
 const a=await connect('alice'),b=await connect('bob'),c=await connect('outsider');
 for(const [p,token] of [[a,'alice'],[b,'bob'],[c,'outsider']])await p.request({type:'hello',token},'ready');
 for(let i=0;i<5;i++)assert.equal((await a.request({type:'create',game:'ludo'},'state')).type,'state');
 assert.equal((await a.request({type:'create',game:'ludo'},'state')).type,'error');
 const code=db.games[0].code;await b.request({type:'join',code},'state');
 assert.equal((await c.request({type:'leave',code},'left')).type,'error');
 const result=await a.request({type:'leave',code},'left');assert.equal(result.games.length,4);assert.equal(db.games[0].host,'bob');
 assert.equal((await a.request({type:'create',game:'ludo'},'state')).type,'state');
 await b.request({type:'leave',code},'left');assert.ok(!db.games.some(g=>g.code===code));
 }finally{for(const ws of sockets)ws.close();server.close();}
});
