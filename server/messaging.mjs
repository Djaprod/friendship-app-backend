import {randomUUID} from 'node:crypto';
import {mkdirSync,writeFileSync,unlinkSync,createWriteStream,renameSync,existsSync,statSync} from 'node:fs';
import {Transform} from 'node:stream';
import {pipeline} from 'node:stream/promises';
import {join} from 'node:path';
export const MAX_MEDIA_BYTES=250*1024*1024;
export const MAX_VOICE_MS=3600000;
export const REACTIONS=['👍','❤️','😂','😮','😢','🙏'];
export function createMessaging({db,clock,save,fail,safeRoom,mediaDir,publish,durable=null}){
 db.uploads||=[];let reserved=0;let uploading=0;const QUOTA=5*1024*1024*1024;
 const used=()=>db.messages.reduce((n,m)=>n+(m.attachment?.size||0),0)+db.uploads.reduce((n,u)=>n+u.size,0);
 const room=(rid,id)=>{const r=db.rooms.find(x=>x.id===rid);if(!safeRoom(r,id))fail(403,'Conversation unavailable');return r;};
 const find=(mid,id)=>{const m=db.messages.find(x=>x.id===mid);if(!m)fail(404,'Message unavailable');room(m.room,id);return m;};
 const view=(m,id)=>({id:m.id,clientId:m.clientId||'',room:m.room,from:m.from,name:m.name,text:m.deleted?'':m.text,time:m.time,deleted:!!m.deleted,attachment:m.deleted?null:m.attachment||null,reactions:m.deleted?[]:REACTIONS.map(emoji=>({emoji,count:Object.values(m.reactions||{}).filter(e=>e===emoji).length,mine:m.reactions?.[id]===emoji})).filter(x=>x.count),delivered:m.delivered||[],read:m.read||[]});
 const list=(rid,id)=>{room(rid,id);return db.messages.filter(m=>m.room===rid).slice(-200).map(m=>view(m,id));};
 async function attachment(a,rid,id){
  if(!a)return null;
  if(a.uploadId){const upload=db.uploads.find(u=>u.id===a.uploadId&&u.owner===id&&u.room===rid&&u.expires>clock());if(!upload)fail(403,'Upload unavailable');db.uploads=db.uploads.filter(u=>u!==upload);return {id:upload.id,mime:upload.mime,size:upload.size,duration:Math.max(0,Math.min(MAX_VOICE_MS,Number(a.duration)||0)),waveform:Array.isArray(a.waveform)?a.waveform.slice(0,64).map(n=>Math.max(0,Math.min(1,Number(n)||0))):[]};}
  if(!['image/jpeg','image/png','image/webp','video/mp4','audio/mp4'].includes(a.mime)||typeof a.data!=='string'||a.data.length>11200000||! /^[A-Za-z0-9+/]*={0,2}$/.test(a.data))fail(400,'Choose a JPEG, PNG, WebP or MP4 file under 8 MB');
  const bytes=Buffer.from(a.data,'base64');if(!bytes.length||bytes.length>8*1024*1024)fail(413,'Attachment limit is 8 MB');
  const valid=a.mime==='image/jpeg'?bytes[0]===255&&bytes[1]===216&&bytes[2]===255:a.mime==='image/png'?bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])):a.mime==='image/webp'?bytes.toString('ascii',0,4)==='RIFF'&&bytes.toString('ascii',8,12)==='WEBP':bytes.toString('ascii',4,8)==='ftyp';
  if(!valid)fail(400,'File contents do not match its media type');
  if(db.messages.filter(m=>m.attachment&&!m.deleted).reduce((n,m)=>n+(m.attachment.size||0),0)+bytes.length+reserved>QUOTA)fail(507,'Media storage is full');
  const media={id:randomUUID(),mime:a.mime,size:bytes.length,duration:Math.max(0,Math.min(MAX_VOICE_MS,Number(a.duration)||0)),waveform:Array.isArray(a.waveform)?a.waveform.slice(0,64).map(n=>Math.max(0,Math.min(1,Number(n)||0))):[]};
  mkdirSync(mediaDir,{recursive:true});writeFileSync(join(mediaDir,media.id),bytes);await durable?.put(media.id,join(mediaDir,media.id));return media;
 }
 async function create(r,id,name,b){const clientId=typeof b.clientId==='string'?b.clientId:'';if(clientId&&!/^[a-zA-Z0-9-]{1,80}$/.test(clientId))fail(400,'Invalid message identifier');const existing=clientId&&db.messages.find(m=>m.room===r.id&&m.from===id&&m.clientId===clientId);if(existing)return view(existing,id);const text=String(b.text||'').trim().slice(0,2000);if(!text&&!b.attachment)fail(400,'Write a message or add media');const media=await attachment(b.attachment,r.id,id);room(r.id,id);const duplicate=clientId&&db.messages.find(m=>m.room===r.id&&m.from===id&&m.clientId===clientId);if(duplicate)return view(duplicate,id);const m={id:randomUUID(),clientId,room:r.id,from:id,name,text,time:clock(),attachment:media,delivered:[],read:[],reactions:{}};db.messages.push(m);save();publish();return view(m,id);}
 async function remove(mid,id){const m=find(mid,id);if(m.from!==id)fail(403,'Only the sender can delete for everyone');if(m.deleted)return view(m,id);if(m.attachment)await durable?.remove(m.attachment.id);if(m.attachment)try{unlinkSync(join(mediaDir,m.attachment.id));}catch{}m.deleted=true;m.text='';m.attachment=null;m.reactions={};save();publish();return view(m,id);}
 function react(mid,id,emoji){const m=find(mid,id);if(m.deleted)fail(409,'Message was deleted');if(!REACTIONS.includes(emoji))fail(400,'Unsupported reaction');m.reactions||={};if(m.reactions[id]===emoji)delete m.reactions[id];else m.reactions[id]=emoji;save();publish();return view(m,id);}
 function receipts(rid,id,ids,read){room(rid,id);if(!Array.isArray(ids)||ids.length>200)fail(400,'Invalid receipt list');let changed=false;
  for(const m of db.messages.filter(m=>m.room===rid&&m.from!==id&&ids.includes(m.id))){m.delivered||=[];m.read||=[];if(!m.delivered.includes(id)){m.delivered.push(id);changed=true;}if(read&&!m.read.includes(id)){m.read.push(id);changed=true;}}
  if(changed){save();publish();}return {ok:true};
 }
 async function media(mediaId,id){const m=db.messages.find(m=>!m.deleted&&m.attachment?.id===mediaId);if(!m)fail(404,'Media unavailable');room(m.room,id);const path=join(mediaDir,mediaId);if(!existsSync(path)||statSync(path).size!==m.attachment.size){if(!durable||!await durable.restore(mediaId,path,m.attachment.size))fail(410,'This attachment is unavailable. Ask the sender to send it again.');}room(m.room,id);if(m.deleted)fail(410,'Media unavailable');return {mime:m.attachment.mime,size:m.attachment.size,path};}
 async function purgeUser(id){for(const mediaId of [...db.uploads.filter(u=>u.owner===id).map(u=>u.id),...db.messages.filter(m=>m.from===id&&m.attachment).map(m=>m.attachment.id)])await durable?.remove(mediaId);for(const u of db.uploads.filter(u=>u.owner===id)){try{unlinkSync(join(mediaDir,u.id));}catch{}}db.uploads=db.uploads.filter(u=>u.owner!==id);for(const m of db.messages.filter(m=>m.from===id&&m.attachment))try{unlinkSync(join(mediaDir,m.attachment.id));}catch{}}

 function sweepUploads(){let changed=false;db.uploads=db.uploads.filter(u=>{if(u.expires>clock())return true;void durable?.remove(u.id).catch(()=>{});try{unlinkSync(join(mediaDir,u.id));}catch{}changed=true;return false;});if(changed)save();}
 async function upload(req,rid,id){
  room(rid,id);sweepUploads();
  const mime=req.headers['x-media-type'];
  if(!['image/jpeg','image/png','image/webp','video/mp4','audio/mp4'].includes(mime))fail(400,'Unsupported media type');
  if(uploading>=4)fail(429,'Uploads are busy. Try again shortly');
  const declared=Number(req.headers['content-length']||MAX_MEDIA_BYTES);
  if(!Number.isFinite(declared)||declared<1||declared>MAX_MEDIA_BYTES)fail(413,'Attachment limit is 250 MB');
  const reserve=declared;if(used()+reserved+reserve>QUOTA)fail(507,'Media storage is full');
  reserved+=reserve;uploading++;
  const mediaId=randomUUID(),tmp=join(mediaDir,mediaId+'.tmp'),target=join(mediaDir,mediaId);let size=0,header=Buffer.alloc(0);
  try{
   mkdirSync(mediaDir,{recursive:true});
   const limiter=new Transform({transform(chunk,encoding,done){size+=chunk.length;if(size>declared||size>MAX_MEDIA_BYTES)return done(Object.assign(Error('Attachment limit exceeded'),{status:413}));if(header.length<16)header=Buffer.concat([header,chunk.subarray(0,16-header.length)]);done(null,chunk);}});
   await pipeline(req,limiter,createWriteStream(tmp,{flags:'wx'}));
   if(!size)fail(400,'Empty media file');
   const valid=mime==='image/jpeg'?header[0]===255&&header[1]===216&&header[2]===255:mime==='image/png'?header.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])):mime==='image/webp'?header.toString('ascii',0,4)==='RIFF'&&header.toString('ascii',8,12)==='WEBP':header.toString('ascii',4,8)==='ftyp';
   if(!valid)fail(400,'File contents do not match its media type');
   room(rid,id);renameSync(tmp,target);await durable?.put(mediaId,target);room(rid,id);
   db.uploads.push({id:mediaId,owner:id,room:rid,mime,size,expires:clock()+3600000});save();
   return {uploadId:mediaId,mime,size};
  }catch(e){try{unlinkSync(tmp);}catch{}try{unlinkSync(target);}catch{}throw e;}finally{reserved-=reserve;uploading--;}
 }
 return {list,create,remove,react,receipts,media,purgeUser,upload,sweepUploads};

}
