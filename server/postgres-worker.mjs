import {parentPort,workerData} from 'node:worker_threads';
import {readFileSync} from 'node:fs';
import pg from 'pg';
import {loadDatabase,persistDatabase,rows} from './postgres-state.mjs';
const {port,signal}=workerData;const flag=new Int32Array(signal);
let client,previous,db,broken=false;
function reply(value){port.postMessage(value);Atomics.store(flag,0,1);Atomics.notify(flag,0);}
try{
 const url=new URL(process.env.DATABASE_URL);
 // URL sslmode parameters can replace the driver's certificate-verifying TLS object.
 for(const key of ['sslmode','sslcert','sslkey','sslrootcert'])url.searchParams.delete(key);
 const local=['localhost','127.0.0.1','::1','[::1]'].includes(url.hostname);
 const insecure=process.env.DATABASE_TLS==='off';
 if(insecure&&(!local||process.env.NODE_ENV==='production'))throw Error('TLS required');
 client=new pg.Client({connectionString:url.toString(),ssl:insecure?false:{rejectUnauthorized:true,...(process.env.DATABASE_CA_FILE?{ca:readFileSync(process.env.DATABASE_CA_FILE,'utf8')}:{})},connectionTimeoutMillis:15000,statement_timeout:15000});
 client.on('error',()=>{broken=true;parentPort.postMessage({fatal:true});});
 await client.connect();
 const lock=await client.query('SELECT pg_try_advisory_lock(714821,1) AS acquired');
 if(!lock.rows[0].acquired)throw Error('An Artii writer is already running');
 await client.query('BEGIN');
 try{await client.query(readFileSync(new URL('./migrations/001-initial.sql',import.meta.url),'utf8'));await client.query('COMMIT');}catch(e){await client.query('ROLLBACK');throw e;}
 db=await loadDatabase(client);previous=rows(db);reply({ok:true,db});
}catch{reply({ok:false,error:'Database startup failed. Verify TLS, migration privileges, and the single-server configuration.'});await client?.end().catch(()=>{});port.close();}
port.on('message',async message=>{
 try{
  if(broken)throw Error('Database disconnected');
  if(message.op==='save'){previous=await persistDatabase(client,message.db,previous);reply({ok:true});}
  else if(message.op==='ping'){await client.query('SELECT 1');reply({ok:true});}
  else if(message.op==='close'){await client.end();reply({ok:true});port.close();}
  else throw Error('Unknown storage operation');
 }catch{broken=true;reply({ok:false,error:'Database transaction failed; the service must restart from durable state.'});}
});
