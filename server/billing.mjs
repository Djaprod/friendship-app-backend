import {createHash,sign,timingSafeEqual} from 'node:crypto';
import {readFileSync} from 'node:fs';
const hash=s=>createHash('sha256').update(s).digest('hex');
const fail=(status,message)=>{throw Object.assign(new Error(message),{status})};
export const PREMIUM='artii_premium_monthly',BOOST='artii_boost_50';
function googleVerifier(env,clock){
 let access='',expires=0;
 async function token(){if(access&&expires>clock())return access;const key=JSON.parse(readFileSync(env.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE,'utf8'));const now=Math.floor(clock()/1000),encode=v=>Buffer.from(JSON.stringify(v)).toString('base64url');const data=encode({alg:'RS256',typ:'JWT'})+'.'+encode({iss:key.client_email,scope:'https://www.googleapis.com/auth/androidpublisher',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600});const assertion=data+'.'+sign('RSA-SHA256',Buffer.from(data),key.private_key).toString('base64url');const r=await fetch('https://oauth2.googleapis.com/token',{method:'POST',body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion}),signal:AbortSignal.timeout(10000)});if(!r.ok)fail(503,'Purchase verification is temporarily unavailable');const j=await r.json();access=j.access_token;expires=clock()+3000000;return access;}
 return async(product,purchaseToken)=>{
  const root='https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'+encodeURIComponent(env.GOOGLE_PLAY_PACKAGE||'com.artii.app');
  const url=product===PREMIUM?root+'/purchases/subscriptionsv2/tokens/'+encodeURIComponent(purchaseToken):root+'/purchases/products/'+encodeURIComponent(product)+'/tokens/'+encodeURIComponent(purchaseToken);
  const r=await fetch(url,{headers:{Authorization:'Bearer '+await token()},signal:AbortSignal.timeout(10000)});if(!r.ok)fail(400,'Google Play could not verify this purchase');const p=await r.json();
  if(product===BOOST)return {valid:p.purchaseState===0,account:p.obfuscatedExternalAccountId,quantity:p.quantity||1};
  const line=p.lineItems?.find(x=>x.productId===PREMIUM);const until=Date.parse(line?.expiryTime||'');const valid=['SUBSCRIPTION_STATE_ACTIVE','SUBSCRIPTION_STATE_IN_GRACE_PERIOD','SUBSCRIPTION_STATE_CANCELED'].includes(p.subscriptionState)&&until>clock();
  const trial=line?.offerDetails?.offerId===(env.GOOGLE_PLAY_TRIAL_OFFER||'trial-3days')?Math.min(until,Date.parse(p.startTime)+3*86400000):0;
  return {valid,account:p.externalAccountIdentifiers?.obfuscatedExternalAccountId,until:Number.isFinite(until)?until:0,trialUntil:Number.isFinite(trial)?trial:0};
 };
}
export function createBilling({db,clock,save,encounters,env=process.env,verify:override}){
 const enabled=!!override||!!env.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE,verify=override||googleVerifier(env,clock),locks=new Map();
 function catalog(u){return {enabled,accountId:hash(u.id),balance:encounters.balance(u),products:[{id:PREMIUM,type:'subs',usd:'4.00',trialDays:3,daily:50},{id:BOOST,type:'inapp',usd:'7.99',credits:50}]};}
 async function verifyPurchase(u,b){
  if(!enabled)fail(503,'Purchases are not activated yet. No payment has been taken.');
  const product=b.product,token=b.purchaseToken;if(![PREMIUM,BOOST].includes(product)||typeof token!=='string'||token.length<10||token.length>4096)fail(400,'Invalid purchase');const key=hash(token);
  if(locks.has(key)){await locks.get(key);return verifyPurchase(u,b)}
  let finish;const lock=new Promise(r=>finish=r);locks.set(key,lock);
  try{
   const receipt=await verify(product,token);if(receipt.account!==hash(u.id))fail(403,'This purchase belongs to a different Artii account');if(!db.users.some(x=>x.id===u.id))fail(401,'Account no longer exists');
   const existing=db.purchases.find(p=>p.hash===key);if(existing&&(existing.user!==u.id||existing.product!==product))fail(409,'Purchase already claimed');
   encounters.balance(u);
   if(!receipt.valid){if(existing&&product===PREMIUM)u.quota.premiumUntil=0;save();fail(400,'Purchase is pending, expired or revoked');}
   if(product===BOOST){if(receipt.quantity!==1)fail(400,'Unsupported purchase quantity');if(!existing){u.quota.wallet+=50;db.purchases.push({hash:key,user:u.id,product,granted:50,time:clock()});}}
   else{u.quota.premiumUntil=receipt.until;u.quota.trialUntil=receipt.trialUntil;const record={hash:key,user:u.id,product,token,time:clock(),until:receipt.until};if(existing)Object.assign(existing,record);else db.purchases.push(record);}
   save();return {verified:true,consume:product===BOOST,balance:encounters.balance(u)};
  }finally{locks.delete(key);finish();}
 }
 // Call from a trusted scheduler / RTDN worker. Entitlements are re-fetched from Google, never accepted from webhook fields.
 async function reconcile(){for(const p of db.purchases.filter(p=>p.product===PREMIUM&&p.token)){const u=db.users.find(u=>u.id===p.user);if(!u)continue;try{const r=await verify(p.product,p.token);if(r.account!==hash(u.id))continue;encounters.balance(u);u.quota.premiumUntil=r.valid?r.until:0;u.quota.trialUntil=r.valid?r.trialUntil:0;}catch{/* Fail closed at the last verified expiry; retry on the next run. */}}save();}
 return {catalog,verifyPurchase,reconcile};
}
