import {parentPort,workerData} from 'node:worker_threads';
import {readFileSync} from 'node:fs';
import pg from 'pg';
import {databaseConfig,databaseStartupError} from './database-config.mjs';
import {loadDatabase,persistDatabase,rows} from './postgres-state.mjs';
const {port,signal}=workerData;const flag=new Int32Array(signal);
let client,previous,db,broken=false;
let stage='configuration';
function reply(value){port.postMessage(value);Atomics.store(flag,0,1);Atomics.notify(flag,0);}
try{
 client=new pg.Client(databaseConfig());
 client.on('error',()=>{broken=true;parentPort.postMessage({fatal:true});});
 stage='connection';await client.connect();
 stage='writer lock';
 const lock=await client.query('SELECT pg_try_advisory_lock(714821,1) AS acquired');
 if(!lock.rows[0].acquired)throw Object.assign(new Error(),{code:'ARTII_WRITER_ACTIVE'});
 stage='migration';await client.query('BEGIN');
 try{await client.query(readFileSync(new URL('./migrations/001-initial.sql',import.meta.url),'utf8'));await client.query('COMMIT');}catch(e){await client.query('ROLLBACK');throw e;}
 stage='load';db=await loadDatabase(client);previous=rows(db);reply({ok:true,db});
}catch(error){reply({ok:false,error:databaseStartupError(error,stage)});await client?.end().catch(()=>{});port.close();}
port.on('message',async message=>{
 try{
  if(broken)throw Error('Database disconnected');
  if(message.op==='save'){previous=await persistDatabase(client,message.db,previous);reply({ok:true});}
  else if(message.op==='ping'){await client.query('SELECT 1');reply({ok:true});}
  else if(message.op==='close'){await client.end();reply({ok:true});port.close();}
  else throw Error('Unknown storage operation');
 }catch{broken=true;reply({ok:false,error:'Database transaction failed; the service must restart from durable state.'});}
});
