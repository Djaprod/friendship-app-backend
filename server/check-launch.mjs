import {launchConfiguration} from './launch-config.mjs';
const result=launchConfiguration();
if(!result.configured){console.error('Launch configuration incomplete:\n'+result.missing.map(k=>' - '+k).join('\n'));process.exitCode=1;}
else {
 try{
  const issuer=process.env.AUTH_ISSUER.replace(/\/$/,'')+'/';
  const response=await fetch(issuer+'.well-known/openid-configuration',{signal:AbortSignal.timeout(15000),redirect:'error'});
  if(!response.ok)throw Error();const info=await response.json();
  if(info.issuer!==issuer||!String(info.jwks_uri).startsWith(issuer))throw Error();
  console.log('Required settings supplied; identity-provider discovery reached. Complete real Google, Facebook, Apple, Play and two-phone call tests before public distribution. Credentials and provider approvals have not been verified by this check.');
 }catch{console.error('Identity-provider discovery check failed. Verify AUTH_ISSUER and network connectivity.');process.exitCode=1;}
}
