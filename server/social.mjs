import {randomBytes,createHash,createPublicKey,verify,timingSafeEqual} from 'node:crypto';
const digest=s=>createHash('sha256').update(s).digest('base64url');
const random=()=>randomBytes(32).toString('base64url');
const fail=(status,message)=>{throw Object.assign(new Error(message),{status})};
// Accept only signed RS256 ID tokens from the configured issuer; never trust browser-supplied profiles.
export function verifyIdentity(token,keys,{issuer,clientId,nonce,now=Date.now()}) {
 try {
  const parts=token.split('.');if(parts.length!==3)throw Error();
  const h=JSON.parse(Buffer.from(parts[0],'base64url')),c=JSON.parse(Buffer.from(parts[1],'base64url'));
  const key=keys.find(k=>k.kid===h.kid&&k.kty==='RSA'&&(!k.use||k.use==='sig')&&(!k.alg||k.alg==='RS256'));
  if(h.alg!=='RS256'||!key||!verify('RSA-SHA256',Buffer.from(parts[0]+'.'+parts[1]),createPublicKey({key,format:'jwk'}),Buffer.from(parts[2],'base64url')))throw Error();
  const aud=Array.isArray(c.aud)?c.aud:[c.aud];
  if(c.iss!==issuer||!aud.includes(clientId)||((aud.length>1||c.azp!==undefined)&&c.azp!==clientId)||c.nonce!==nonce||typeof c.exp!=='number'||c.exp<=now/1000||typeof c.iat!=='number'||c.iat>now/1000+60||c.nbf>now/1000+60||typeof c.sub!=='string'||!c.sub||c.sub.length>255)throw Error();
  return {subject:c.sub,issuer,email:typeof c.email==='string'&&c.email_verified===true?c.email.toLowerCase():null};
 }catch{fail(401,'Social sign-in could not be verified. Please try again.')}
}
export function createSocial({clock=()=>Date.now(),env=process.env,fetcher=fetch}={}) {
 const issuer=(env.AUTH_ISSUER||'').replace(/\/$/,'')+'/',clientId=env.AUTH_CLIENT_ID||'',secret=env.AUTH_CLIENT_SECRET||'',origin=(env.PUBLIC_ORIGIN||'').replace(/\/$/,'');
 const ready=!!(clientId&&secret&&/^https:\/\/[^/]+\/$/.test(issuer)&&/^https:\/\/[^/]+$/.test(origin));
 const connections={google:env.AUTH_GOOGLE_CONNECTION,facebook:env.AUTH_FACEBOOK_CONNECTION,apple:env.AUTH_APPLE_CONNECTION};
 const flows=new Map();const prune=()=>{for(const [k,f]of flows)if(f.expires<=clock())flows.delete(k)};
 const json=async(url,options={})=>{const r=await fetcher(url,{...options,signal:AbortSignal.timeout(10000),redirect:'error'});if(!r.ok)fail(502,'The sign-in provider is unavailable. Please try again.');return r.json()};
 const callback=origin+'/auth/social/callback';
 let keys=[],keysExpire=0;
 return {
  providers:()=>Object.fromEntries(Object.keys(connections).map(k=>[k,ready&&!!connections[k]])),
  start(provider,challenge){
   prune();if(!ready||!Object.hasOwn(connections,provider)||!connections[provider])fail(503,'This sign-in option has not been activated yet. Use email to continue.');
   if(!/^[A-Za-z0-9_-]{43}$/.test(challenge||''))fail(400,'Invalid sign-in request');if(flows.size>=1000)fail(429,'Please try again later');
   const id=random(),state=random(),nonce=random(),verifier=random();
   flows.set(id,{id,state,nonce,verifier,challenge,expires:clock()+600000,status:'waiting'});
   const url=new URL(issuer+'authorize');url.search=new URLSearchParams({client_id:clientId,redirect_uri:callback,response_type:'code',scope:'openid email',state,nonce,code_challenge:digest(verifier),code_challenge_method:'S256',connection:connections[provider]}).toString();
   return {flowId:id,authorizationUrl:url.href};
  },
  async callback(params){
   prune();const f=[...flows.values()].find(f=>f.state===params.get('state'));if(!f||f.used)fail(400,'Sign-in expired. Return to Artii and try again.');f.used=true;
   try{
    if(params.get('error')||!params.get('code'))fail(401,'Social sign-in was cancelled.');
    const t=await json(issuer+'oauth/token',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({grant_type:'authorization_code',client_id:clientId,client_secret:secret,code:params.get('code'),redirect_uri:callback,code_verifier:f.verifier})});
    if(keysExpire<=clock()){keys=(await json(issuer+'.well-known/jwks.json')).keys;keysExpire=clock()+3600000;}
    const header=JSON.parse(Buffer.from(String(t.id_token).split('.')[0],'base64url'));
    if(!keys.some(k=>k.kid===header.kid)){keys=(await json(issuer+'.well-known/jwks.json')).keys;}
    f.identity=verifyIdentity(t.id_token,keys,{issuer,clientId,nonce:f.nonce,now:clock()});f.status='verified';
   }catch(e){f.status='failed';f.error=e.status?e.message:'Social sign-in failed. Please try again.';}
   delete f.verifier;return f.status;
  },
  get(id,proof){prune();const f=flows.get(id);if(!f||typeof proof!=='string'||proof.length>128)fail(401,'Sign-in expired. Please start again.');const a=Buffer.from(digest(proof)),b=Buffer.from(f.challenge);if(a.length!==b.length||!timingSafeEqual(a,b))fail(401,'Invalid sign-in request');return f;},
  consume(id){flows.delete(id)}
 };
}
