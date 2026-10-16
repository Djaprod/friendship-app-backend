import test from 'node:test';
import assert from 'node:assert/strict';
import {PGlite} from '@electric-sql/pglite';
import {readFile,mkdtemp,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {persistDatabase,loadDatabase,rows,tables} from './postgres-state.mjs';
const sql=await readFile(new URL('./migrations/001-initial.sql',import.meta.url),'utf8');
const wrap=db=>({query:async(sql,params)=>params?db.query(sql,params):(await db.exec(sql)).at(-1)});
const fixture=()=>({users:[{id:'a',username:'alice',email:'alice@example.com',salt:'salt',password:'hash',quota:{epoch:1,used:2,wallet:50}},{id:'b',username:'bob',email:'bob@example.com'}],friends:[['b','a']],rooms:[{id:'r',members:['a','b']}],messages:[{id:'m',room:'r',from:'a',text:'hello',read:['b'],delivered:['b'],reactions:{b:'❤️'}}],shares:[{user:'a',lat:-20,lon:57,heading:90,audience:['b'],expires:99999}],games:[{id:'g',code:'ABCD',players:[{id:'a',role:'Werewolf'}],phase:'night',version:4}],sessions:[{hash:'hashed-session',user:'a',expires:99999}]});
test('SQL schema, uniqueness, references, transactions, privacy and restart persistence',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'artii-postgres-'));let pg=new PGlite(dir);let client=wrap(pg);
 try{
  await client.query(sql);await client.query(sql);let state=fixture();let previous=await persistDatabase(client,state);
  let loaded=await loadDatabase(client);assert.equal(loaded.messages[0].reactions.b,'❤️');assert.equal(loaded.games[0].players[0].role,'Werewolf');assert.deepEqual(loaded.friends,[['a','b']]);
  for(const field of ['username','email']){const bad=structuredClone(state);bad.users.push({...bad.users[0],id:'collision',username:'other',email:'other@example.com',[field]:state.users[0][field].toUpperCase()});await assert.rejects(persistDatabase(client,bad,previous),e=>e.code==='23505');}
  const invalid=structuredClone(state);invalid.messages.push({id:'bad',room:'missing',from:'a',text:'must roll back'});invalid.users[0].quota.wallet=9999;await assert.rejects(persistDatabase(client,invalid,previous),e=>e.code==='23503');assert.equal((await loadDatabase(client)).users[0].quota.wallet,50);
  const location=structuredClone(state);location.shares[0].lat=999;await assert.rejects(persistDatabase(client,location,previous),e=>e.code==='23514');
  await client.query('CREATE ROLE artii_untrusted; SET ROLE artii_untrusted');await assert.rejects(client.query('SELECT payload FROM artii.users'),e=>e.code==='42501');await client.query('RESET ROLE');
  state.messages[0].text='';state.messages[0].deleted=true;state.messages[0].reactions={};previous=await persistDatabase(client,state,previous);
  await pg.close();pg=new PGlite(dir);client=wrap(pg);loaded=await loadDatabase(client);assert.equal(loaded.users[0].quota.wallet,50);assert.equal(loaded.messages[0].deleted,true);assert.equal(loaded.games[0].version,4);assert.equal(loaded.sessions[0].hash,'hashed-session');
  previous=rows(loaded);loaded.friends=[];loaded.shares=[];loaded.messages=[];loaded.rooms=[];loaded.users=[];loaded.sessions=[];loaded.games=[];await persistDatabase(client,loaded,previous);assert.equal((await loadDatabase(client)).users.length,0);
 }finally{await pg.close();await rm(dir,{recursive:true,force:true});}
});
test('unknown collections and duplicate identifiers fail instead of losing data',()=>{
 assert.throws(()=>rows({unmapped:[]}));assert.throws(()=>rows({users:[{id:'a'},{id:'a'}]}));assert.ok(Object.keys(tables).includes('uploads'));
});
