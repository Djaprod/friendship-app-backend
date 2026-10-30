import test from 'node:test';
import assert from 'node:assert/strict';
import {PGlite} from '@electric-sql/pglite';
import {mkdtemp,writeFile,readFile,unlink,rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createDurableMedia} from './durable-media.mjs';
import {createMessaging} from './messaging.mjs';

test('private media survives local file loss and database reopen, and deletion removes backup',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'artii-durable-'));let db=new PGlite(join(dir,'pg'));
 const client=()=>({query:async(sql,params)=>params?db.query(sql,params):(await db.exec(sql)).at(-1),release(){}});
 const pool={connect:async()=>client(),query:(...args)=>client().query(...args),end:async()=>{}};
 let durable=createDurableMedia(pool);const source=join(dir,'source'),restored=join(dir,'restored');const bytes=Buffer.alloc(4*1024*1024+123,91);
 try{
  await db.exec(await readFile(new URL('./migrations/001-initial.sql',import.meta.url),'utf8'));
  await writeFile(source,bytes);await durable.put('test-media',source);await unlink(source);
  await db.close();db=new PGlite(join(dir,'pg'));durable=createDurableMedia(pool);
  assert.equal(await durable.restore('test-media',restored,bytes.length),true);assert.deepEqual(await readFile(restored),bytes);
  await db.exec('CREATE ROLE media_untrusted; SET ROLE media_untrusted');await assert.rejects(db.query('SELECT bytes FROM artii.media_chunks'),e=>e.code==='42501');await db.exec('RESET ROLE');
  await durable.remove('test-media');await unlink(restored);assert.equal(await durable.restore('test-media',restored,bytes.length),false);
 }finally{await db.close();await rm(dir,{recursive:true,force:true});}
});

test('missing legacy attachments return 410 and private media stays authorization protected',async()=>{
 const dir=await mkdtemp(join(tmpdir(),'artii-missing-'));
 const db={messages:[{id:'message',room:'room',from:'alice',attachment:{id:'missing',mime:'image/png',size:8}}],rooms:[{id:'room',members:['alice']}]};
 const messaging=createMessaging({db,clock:Date.now,save(){},fail(status,message){throw Object.assign(Error(message),{status})},safeRoom:(r,id)=>r?.members.includes(id),mediaDir:dir,publish(){}});
 try{await assert.rejects(messaging.media('missing','alice'),e=>e.status===410);await assert.rejects(messaging.media('missing','outsider'),e=>e.status===403);}finally{await rm(dir,{recursive:true,force:true});}
});
