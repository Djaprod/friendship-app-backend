import {existsSync} from 'node:fs';
export function launchConfiguration(env=process.env){
 const missing=[];
 const required=['DATABASE_URL','PUBLIC_ORIGIN','AUTH_ISSUER','AUTH_CLIENT_ID','AUTH_CLIENT_SECRET','AUTH_GOOGLE_CONNECTION','AUTH_FACEBOOK_CONNECTION','AUTH_APPLE_CONNECTION','TURN_URL','TURN_USERNAME','TURN_PASSWORD','GOOGLE_PLAY_SERVICE_ACCOUNT_FILE'];
 for(const key of required)if(!env[key]?.trim()||/replace-with|YOUR-|your-artii-service/.test(env[key]))missing.push(key);
 for(const key of ['PUBLIC_ORIGIN','AUTH_ISSUER'])if(env[key]){try{const url=new URL(env[key]);if(url.protocol!=='https:'||url.username||url.password||url.search||url.hash||url.pathname!=='/')missing.push(key+' (HTTPS origin required)');}catch{missing.push(key+' (invalid URL)');}}
 if(env.DATABASE_URL){try{const u=new URL(env.DATABASE_URL);if(!['postgres:','postgresql:'].includes(u.protocol)||u.port==='6543')missing.push('DATABASE_URL (direct/session connection required)');}catch{missing.push('DATABASE_URL (invalid URI)');}}
 if(env.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE&&!existsSync(env.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE))missing.push('GOOGLE_PLAY_SERVICE_ACCOUNT_FILE (file not found)');
 return {configured:missing.length===0,missing};
}
