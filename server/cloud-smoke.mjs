// Read-only hosted smoke check; credentials are supplied through environment, never printed.
const origin=process.env.ARTII_API_URL?.replace(/\/$/,'');
if(!origin?.startsWith('https://'))throw Error('Set ARTII_API_URL to the public HTTPS origin');
const health=await fetch(origin+'/health',{signal:AbortSignal.timeout(30000)}).then(r=>r.json());
if(!health.ok||health.storage!=='postgres')throw Error('Hosted PostgreSQL health check failed');
if(process.env.ARTII_TEST_TOKEN){
 const token=process.env.ARTII_TEST_TOKEN;
 const response=await fetch(origin+'/state',{headers:{Authorization:'Bearer '+token},signal:AbortSignal.timeout(30000)});
 if(!response.ok)throw Error('Authenticated state check failed');
 await new Promise((resolve,reject)=>{const ws=new WebSocket(origin.replace('https:','wss:')+'/chat/socket');const timer=setTimeout(()=>{ws.close();reject(Error('Socket handshake timed out'));},15000);ws.onopen=()=>ws.send(JSON.stringify({type:'hello',token,focused:false}));ws.onmessage=event=>{if(JSON.parse(event.data).type==='ready'){clearTimeout(timer);ws.close();resolve();}};ws.onerror=()=>{clearTimeout(timer);reject(Error('Secure socket failed'));};});
 console.log('PASS: HTTPS health, authenticated state, and WSS handshake');
}else console.log('PASS: HTTPS/PostgreSQL health. Set ARTII_TEST_TOKEN to also test authenticated state and WSS.');
