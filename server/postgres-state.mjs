import {createHash} from 'node:crypto';
// All durable collections are explicit; unknown state must never be silently lost.
export const tables={users:'users',sessions:'sessions',requests:'friend_requests',friends:'friends',rooms:'rooms',messages:'messages',shares:'locations',blocks:'blocks',reports:'reports',calls:'calls',signals:'signals',encounters:'encounters',purchases:'purchases',invites:'game_invites',games:'game_sessions',uploads:'uploads'};
export function rows(db){
 const result={};
 for(const key of Object.keys(db))if(!(key in tables))throw Error('Unmapped durable collection: '+key);
 for(const key of Object.keys(tables)){
  const entries=db[key]||[];if(!Array.isArray(entries))throw Error('Invalid durable collection');
  result[key]=new Map(entries.map(raw=>{const value=key==='friends'?[...raw].sort():raw;
   const id=key==='friends'?value.join(':'):String(value.id|| (key==='users'?value.id:key==='sessions'?value.hash:key==='shares'?value.user:'') ||createHash('sha256').update(JSON.stringify(value)).digest('hex'));
   return [id,JSON.stringify(value)];}));
  if(result[key].size!==entries.length)throw Error('Duplicate durable row in '+key);
 }
 return result;
}
export async function loadDatabase(client){const db={};for(const [key,table]of Object.entries(tables)){db[key]=(await client.query(`SELECT payload FROM artii.${table} ORDER BY position,id`)).rows.map(r=>r.payload);}return db;}
export async function persistDatabase(client,db,previous={}){
 const next=rows(db);await client.query('BEGIN');
 try{
  await client.query('SET CONSTRAINTS ALL DEFERRED');
  for(const [key,table]of Object.entries(tables)){
   const oldPositions=new Map([...(previous[key]?.keys()||[])].map((id,index)=>[id,index]));
   let position=0;for(const [id,payload]of next[key]){if(previous[key]?.get(id)!==payload||oldPositions.get(id)!==position)await client.query(`INSERT INTO artii.${table}(id,payload,position) VALUES($1,$2::jsonb,$3) ON CONFLICT(id) DO UPDATE SET payload=EXCLUDED.payload,position=EXCLUDED.position`,[id,payload,position]);position++;}
   for(const id of previous[key]?.keys()||[])if(!next[key].has(id))await client.query(`DELETE FROM artii.${table} WHERE id=$1`,[id]);
  }
  await client.query('COMMIT');return next;
 }catch(e){await client.query('ROLLBACK');throw e;}
}
