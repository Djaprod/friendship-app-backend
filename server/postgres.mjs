import {Worker,MessageChannel,receiveMessageOnPort} from 'node:worker_threads';
// The existing domain engine performs synchronous atomic mutations. A dedicated
// DB worker commits them before any successful response or broadcast is emitted.
// This is deliberately ONE writer, enforced by a PostgreSQL session advisory lock.
// Do not horizontally scale until replacing this coordinator with distributed locks.
export function openPostgres(){
 const signal=new SharedArrayBuffer(4),flag=new Int32Array(signal);
 const {port1,port2}=new MessageChannel();
 const worker=new Worker(new URL('./postgres-worker.mjs',import.meta.url),{workerData:{signal,port:port2},transferList:[port2]});
 let failed=false,closed=false;
 const abort=()=>{if(closed)return;failed=true;console.error('Artii database unavailable; exiting to prevent uncommitted state from being served.');process.exit(1);};
 worker.on('error',abort);worker.on('message',m=>{if(m.fatal)abort();});
 worker.on('exit',()=>{if(!closed)abort();});
 function receive(){
  if(Atomics.load(flag,0)===0)Atomics.wait(flag,0,0,60000);
  const message=receiveMessageOnPort(port1)?.message;
  if(!message?.ok){failed=true;throw Error(message?.error||'Database operation timed out');}
  return message;
 }
 let initial;
 try{initial=receive().db;}catch(e){closed=true;void worker.terminate();port1.close();throw e;}
 function request(op,db){if(failed||closed)throw Error('Storage unavailable');Atomics.store(flag,0,0);port1.postMessage({op,db});try{return receive();}catch(e){queueMicrotask(abort);throw e;}}
 return {initial,save:db=>request('save',db),ping:()=>request('ping'),close(){if(closed)return;request('close');closed=true;port1.close();void worker.terminate();}};
}
