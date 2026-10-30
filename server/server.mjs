import http from 'node:http';
import {configurePublicOrigin} from './public-origin.mjs';
import {createDurableMedia} from './durable-media.mjs';
import {attachChat} from './chat-network.mjs';
import {createMessaging,MAX_MEDIA_BYTES,MAX_VOICE_MS} from './messaging.mjs';
import {promisify} from 'node:util';
import {createEncounters,defaults} from './encounters.mjs';
import {createBilling} from './billing.mjs';
import {createSocial} from './social.mjs';
import {socialReturnPage} from './social-return.mjs';
const derive=promisify(scrypt);
const usernameKey=v=>typeof v==='string'?v.trim().toLowerCase():'';
const usernameValid=v=>/^[a-z][a-z0-9_]{2,23}$/.test(v)&&!['admin','administrator','artii','support','moderator','system'].includes(v);
import { randomUUID, randomBytes, scrypt, timingSafeEqual, createHash } from 'node:crypto';
import { readFileSync, writeFileSync, mkdirSync, renameSync, existsSync, createReadStream } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { attachGames } from './game-network.mjs';

export function createArtiiServer({ storage = null, file = resolve(process.env.DATA_DIR||'data','artii.json'), clock = () => Date.now(), socialOptions = {}, billingOptions = {} } = {}) {
  const empty = () => ({users:[],sessions:[],requests:[],friends:[],rooms:[],messages:[],shares:[],blocks:[],reports:[],calls:[],signals:[],encounters:[],purchases:[],invites:[]});
  if(process.env.NODE_ENV==='production'&&!storage)throw Error('Production requires managed PostgreSQL storage');
  let db = storage ? {...empty(),...storage.initial} : existsSync(file) ? {...empty(), ...JSON.parse(readFileSync(file,'utf8'))} : empty();
  // Case-insensitive username is the public friend identifier. UUIDs keep relationships stable.
  const taken=new Set();let migrated=false;
  for(const u of db.users){let n=usernameKey(u.username);if(!usernameValid(n)||taken.has(n)){const stem=(String(u.name||'friend').toLowerCase().replace(/[^a-z0-9_]/g,'').replace(/^[^a-z]+/,'')||'friend').slice(0,16);n=stem.length>=3?stem:'friend';let i=1;while(!usernameValid(n)||taken.has(n))n=stem+'_'+i++;u.username=n;migrated=true;}if(u.username!==n){u.username=n;migrated=true;}taken.add(n);}
  const limits = new Map();
  const social=createSocial({clock,...socialOptions});
  const fail=(status,message)=>{throw Object.assign(new Error(message),{status});};
  const clean=(v,max=100)=>String(v??'').trim().slice(0,max);
  const hash=v=>createHash('sha256').update(v).digest('hex');
  const blocked=(a,b)=>db.blocks.some(x=>(x.by===a&&x.user===b)||(x.by===b&&x.user===a));
  const friends=(a,b)=>a!==b&&!blocked(a,b)&&db.friends.some(x=>x.includes(a)&&x.includes(b));
  const user=id=>db.users.find(x=>x.id===id);
  const visible=(u,viewer)=>u.id===viewer||(u.settings?.visibility||'everyone')==='everyone'||u.settings?.visibility==='friends'&&friends(u.id,viewer);
  let chat;
  const publicUser=(u,viewer)=>({id:u.id,name:u.name,username:u.username,country:u.country,bio:visible(u,viewer)?u.bio:"",badgeColor:u.badgeColor||"",interests:u.interests,avatar:u.avatar||'',language:u.language||'English',discoverOnline:visible(u,viewer)&&!blocked(u.id,viewer)&&!!chat?.online(u.id)&&!!(u.settings?.strangerChats||u.settings?.strangerCalls)&&!encounters.occupied(u.id),presence:visible(u,viewer)&&!blocked(u.id,viewer)?{online:chat?.online(u.id)||false,lastSeen:u.presenceLastSeen||null}:null});
  const save=()=>{if(storage){storage.save(db);return;}mkdirSync(dirname(file),{recursive:true});writeFileSync(file+'.tmp',JSON.stringify(db));renameSync(file+'.tmp',file);};
  const encounters=createEncounters({db,clock,save,blocked});
  const billing=createBilling({db,clock,save,encounters,...billingOptions});
  if(migrated)save();
  const claimUsername=(value,except)=>{const n=usernameKey(value);if(!usernameValid(n))fail(400,'Use 3–24 letters, numbers or underscores, starting with a letter');if(db.users.some(u=>u.username===n&&u.id!==except))fail(409,'Username already taken');return n;};
  const issue=u=>{const token=randomBytes(32).toString('hex');db.sessions=db.sessions.filter(x=>x.expires>clock());db.sessions.push({user:u.id,hash:hash(token),expires:clock()+30*86400000});save();return {token,user:publicUser(u,u.id)};};
  const requireUser=id=>{const u=user(id);if(!u)fail(404,'Person not found');return u;};
  const safeRoom=(r,id)=>r&&r.members.includes(id)&&!r.members.some(m=>blocked(id,m))&&(!r.encounter||db.encounters.some(e=>e.id===r.encounter&&['connecting','active'].includes(e.status)));
  const roomFor=(a,b)=>{let r=db.rooms.find(x=>!x.group&&!x.encounter&&x.members.includes(a)&&x.members.includes(b));if(!r){r={id:randomUUID(),name:'',group:false,members:[a,b],owner:a};db.rooms.push(r);}return r;};
  const roomView=(r,id)=>({...r,name:r.group?r.name:requireUser(r.members.find(x=>x!==id)).name,last:(()=>{const m=db.messages.filter(m=>m.room===r.id).at(-1);return m?.deleted?'This message was deleted':m?.text||(m?.attachment?'Media':'Say hello');})(),people:r.members.map(x=>publicUser(requireUser(x),id))});
  const nowCall=c=>c.status!=='ended'&&(c.status==='accepted'||clock()-c.created<60000);
  async function body(req){let size=0,parts=[];for await(const part of req){size+=part.length;if(size>(new RegExp("^/rooms/[^/]+/messages$").test(req.url)?11500000:450000))fail(413,'Request too large');parts.push(part);}try{const value=JSON.parse(Buffer.concat(parts).toString()||'{}');if(!value||typeof value!=='object'||Array.isArray(value))fail(400,'Expected a JSON object');return value;}catch{fail(400,'Invalid JSON object');}}
  function profile(b,old={}) {
    const name=clean(b.name??old.name,40);if(name.length<2)fail(400,'Use a name of at least two characters');
    const avatar=b.avatar??old.avatar??'';if(typeof avatar!=='string'||avatar.length>350000||avatar&&!/^data:image\/(jpeg|png);base64,[A-Za-z0-9+/=]+$/.test(avatar))fail(400,'Choose a smaller JPEG or PNG profile picture');
    return {name,country:clean(b.country??old.country,60),bio:clean(b.bio??old.bio,150),badgeColor:/^#[0-9a-fA-F]{6}$/.test(b.badgeColor||'')?b.badgeColor:(old.badgeColor||''),language:clean(b.language??old.language??'English',40),interests:(Array.isArray(b.interests)?b.interests:old.interests||[]).slice(0,6).map(x=>clean(x,24)),avatar};
  }
  const durableMedia=storage?createDurableMedia():null;
  const messaging=createMessaging({db,clock,save,fail,safeRoom,mediaDir:resolve(dirname(file),'media'),publish:()=>chat?.publish(),durable:durableMedia});
  const server=http.createServer(async(req,res)=>{
    const send=(status,obj)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(obj));if(status<300&&req.method!=='GET'&&!req.url.endsWith('/receipts'))chat?.publish();};
    try{
      const url=new URL(req.url,'http://localhost'),path=url.pathname;
      if(req.method==='GET'&&path==='/health'){storage?.ping();return send(200,{ok:true,app:'Artii',version:'0.7.5',revision:process.env.RENDER_GIT_COMMIT||null,storage:storage?'postgres':'local-test',features:{social:social.providers(),callsRelay:!!(process.env.TURN_URL&&process.env.TURN_USERNAME&&process.env.TURN_PASSWORD),purchases:!!process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE}});}
      if(req.method==='GET'&&['/play/','/play/index.html','/play/style.css','/play/app.js'].includes(path)){
        const name=path.endsWith('/')?'index.html':path.split('/').at(-1);
        const content=readFileSync(new URL('../app/src/main/assets/games/'+name,import.meta.url));
        res.writeHead(200,{'Content-Type':name.endsWith('.css')?'text/css':name.endsWith('.js')?'text/javascript':'text/html','X-Content-Type-Options':'nosniff'});res.end(content);return;
      }
      const ip=process.env.TRUST_PROXY==='1'?String(req.headers['x-forwarded-for']||req.socket.remoteAddress).split(',').at(-1).trim():req.socket.remoteAddress;const slot=Math.floor(clock()/60000);const key=ip+':'+slot;
      if(limits.size>10000)limits.clear();limits.set(key,(limits.get(key)||0)+1);if(limits.get(key)>400)fail(429,'Too many requests. Please wait a minute.');
      const isMediaUpload=req.method==='POST'&&new RegExp("^/rooms/[^/]+/media$").test(path);
      const b=!isMediaUpload&&['POST','PUT','PATCH','DELETE'].includes(req.method)?await body(req):{};
      if(path==='/auth/config'&&req.method==='GET')return send(200,{providers:social.providers()});
      if(path==='/auth/username'&&req.method==='GET'){const n=usernameKey(url.searchParams.get('username'));return send(200,{username:n,valid:usernameValid(n),available:usernameValid(n)&&!db.users.some(u=>u.username===n)});}
      if(path==='/auth/social/start'&&req.method==='POST'){const k='social:'+key;limits.set(k,(limits.get(k)||0)+1);if(limits.get(k)>10)fail(429,'Please wait before trying again');return send(200,social.start(b.provider,b.challenge));}
      if(path==='/auth/social/callback'&&req.method==='GET'){
        const status=await social.callback(url.searchParams);
        const page=socialReturnPage(status);
        res.writeHead(200,page.headers);res.end(page.html);return;
      }
      if(['/auth/social/result','/auth/social/complete'].includes(path)&&req.method==='POST'){
        const f=social.get(b.flowId,b.proof);if(f.status==='failed')fail(401,f.error);if(f.status!=='verified')return send(200,{status:'waiting'});
        let u=db.users.find(u=>[u.identity,...(u.identities||[])].some(identity=>identity?.issuer===f.identity.issuer&&identity?.subject===f.identity.subject));
        const existing=!u&&f.identity.email?db.users.find(account=>account.email?.toLowerCase()===f.identity.email):null;
        if(existing){
          // Only Google's verified Gmail identity is authoritative for automatic email linking.
          // Other domains must prove ownership of the existing Artii account once.
          const googleGmail=f.provider==='google'&&f.identity.subject.startsWith('google-oauth2|')&&/^[^@\s]+@gmail\.com$/.test(f.identity.email);
          if(!googleGmail){
            if(!existing.password)fail(409,'Use the sign-in method already linked to this profile.');
            if(!b.linkPassword)return send(200,{status:'link_required'});
            const lk='link:'+key;limits.set(lk,(limits.get(lk)||0)+1);if(limits.get(lk)>10)fail(429,'Too many attempts. Please try again later.');
            if(typeof b.linkPassword!=='string'||b.linkPassword.length>128)fail(400,'Enter your existing Artii password');
            const derived=await derive(b.linkPassword,existing.salt,64);
            if(!timingSafeEqual(Buffer.from(existing.password,'hex'),derived))fail(401,'Artii password is incorrect');
          }
          social.get(b.flowId,b.proof);
          u=existing;u.identities||=[];if(!u.identities.some(identity=>identity.issuer===f.identity.issuer&&identity.subject===f.identity.subject))u.identities.push(f.identity);
        }
        if(!u&&path.endsWith('/complete')){
          if(b.adult!==true)fail(400,'This alpha is for adults 18 and over');
          const username=claimUsername(b.username);
          // Never automatically link accounts on email alone: require the original method for existing accounts.
          if(f.identity.email&&db.users.some(u=>u.email===f.identity.email))fail(409,'An account already uses this email. Log in using your original method.');
          u={id:randomUUID(),email:f.identity.email,identity:f.identity,username,...profile({name:username})};db.users.push(u);
        }
        if(!u)return send(200,{status:'username_required'});
        social.consume(f.id);return send(200,{status:'authenticated',...issue(u)});
      }
      if(req.method==='POST'&&['/auth/register','/auth/login'].includes(path)){
        const ak='auth:'+key;limits.set(ak,(limits.get(ak)||0)+1);if(limits.get(ak)>20)fail(429,'Too many sign-in attempts');
        const email=clean(b.email,200).toLowerCase();const password=String(b.password||'');
        if(!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)||password.length<10||password.length>128)fail(400,'Use a valid email and a password of 10–128 characters');
        const ek='email:'+hash(email)+':'+slot;limits.set(ek,(limits.get(ek)||0)+1);if(limits.get(ek)>8)fail(429,'Too many attempts. Please wait a minute.');
        let u=db.users.find(x=>x.email===email);
        if(path==='/auth/register'){
          if(u)fail(409,'An account already uses that email');
          if(b.adult!==true)fail(400,'This alpha is for adults 18 and over');
          claimUsername(b.username);const p=profile({...b,name:b.username});const salt=randomBytes(16).toString('hex'),passwordHash=(await derive(password,salt,64)).toString('hex');
          // Recheck after asynchronous hashing. Only one concurrent request can claim each username/email.
          const username=claimUsername(b.username);if(db.users.some(x=>x.email===email))fail(409,'An account already uses that email');
          u={id:randomUUID(),email,username,salt,password:passwordHash,...p};db.users.push(u);
        }else{
          const derived=await derive(password,u?.salt||'artii-nonexistent-account-salt',64);
          if(!u?.password||!timingSafeEqual(Buffer.from(u.password,'hex'),derived))fail(401,'Email or password is incorrect');
        }
        return send(200,issue(u));
      }
      const token=(req.headers.authorization||'').replace(/^Bearer /,'');const session=db.sessions.find(x=>x.hash===hash(token)&&x.expires>clock());if(!session)fail(401,'Please sign in again');const me=requireUser(session.user),id=me.id;encounters.sweep();
      if(req.method==='POST'&&path==='/auth/logout'){encounters.end(encounters.active(id),'signed out');db.sessions=db.sessions.filter(x=>x!==session);db.shares=db.shares.filter(x=>x.user!==id);save();return send(200,{ok:true});}
      if(req.method==='PUT'&&path==='/profile'){const p=profile(b,me);if(b.username!==undefined)p.username=claimUsername(b.username,id);Object.assign(me,p);save();return send(200,{user:publicUser(me,id)});}
      if(path==='/settings'&&req.method==='GET')return send(200,{settings:encounters.settings(me),blocked:db.blocks.filter(x=>x.by===id).map(x=>publicUser(requireUser(x.user),id))});
      if(path==='/settings'&&req.method==='PUT'){
        const next=encounters.settings(me);for(const k of ['ghost','messages','gameInvites','friendRequests','strangerChats','strangerCalls'])if(typeof b[k]==='boolean')next[k]=b[k];
        if(['everyone','friends','nobody'].includes(b.visibility))next.visibility=b.visibility;if(['en','fr'].includes(b.language))next.language=b.language;
        me.settings=next;if(!next.strangerChats&&!next.strangerCalls)for(const e of db.encounters.filter(e=>e.status==='pending'&&e.members.includes(id)))encounters.end(e,'private mode');if(next.ghost)db.shares=db.shares.filter(x=>x.user!==id);save();return send(200,{settings:next});
      }
      if(path==='/block'&&req.method==='DELETE'){db.blocks=db.blocks.filter(x=>!(x.by===id&&x.user===b.user));save();return send(200,{ok:true});}
      if(path==='/account'&&req.method==='DELETE'){
        if(b.confirm!=='DELETE')fail(400,'Type DELETE to confirm');encounters.end(encounters.active(id),'account deleted');
        await messaging.purgeUser(id);const roomIds=db.rooms.filter(r=>r.members.includes(id)).map(r=>r.id),callIds=db.calls.filter(c=>[c.from,c.to].includes(id)).map(c=>c.id);
        db.users=db.users.filter(u=>u.id!==id);db.sessions=db.sessions.filter(x=>x.user!==id);db.requests=db.requests.filter(x=>x.from!==id&&x.to!==id);db.friends=db.friends.filter(x=>!x.includes(id));
        db.messages=db.messages.filter(x=>x.from!==id);db.rooms=db.rooms.map(r=>{if(!r.members.includes(id))return r;const members=r.members.filter(p=>p!==id);return {...r,members,owner:r.owner===id?members[0]:r.owner,group:true,name:r.group?r.name:'Deleted account',encounter:undefined};}).filter(r=>r.members.length);const remainingRoomIds=new Set(db.rooms.map(r=>r.id));db.messages=db.messages.filter(x=>remainingRoomIds.has(x.room));db.shares=db.shares.filter(x=>x.user!==id).map(x=>({...x,audience:x.audience.filter(p=>p!==id)}));db.blocks=db.blocks.filter(x=>x.by!==id&&x.user!==id);db.reports=db.reports.filter(x=>x.by!==id&&x.user!==id);db.calls=db.calls.filter(c=>!callIds.includes(c.id));db.signals=db.signals.filter(x=>!callIds.includes(x.call));db.encounters=db.encounters.filter(e=>!e.members.includes(id));db.purchases=db.purchases.filter(p=>p.user!==id);db.invites=db.invites.filter(x=>x.from!==id&&x.to!==id);if(db.games)db.games=db.games.filter(g=>!g.players.some(p=>p.id===id));save();return send(200,{ok:true});
      }
      if(path==='/store'&&req.method==='GET')return send(200,billing.catalog(me));
      if(path==='/store/verify'&&req.method==='POST'){const result=await billing.verifyPurchase(me,b);return send(200,result);}
      if(path==='/discover/invite'&&req.method==='POST'){if(!chat.online(id)||!chat.online(b.user))fail(409,'Both people must be online to meet');if(b.consent!==true)fail(400,'Accept the meeting terms to continue');return send(200,{meeting:encounters.view(encounters.invite(id,b.user,b.mode),id)});}
      if(path==='/discover/random'&&req.method==='POST'){
        if(b.consent!==true)fail(400,'Accept the meeting terms to continue');const candidates=db.users.filter(u=>u.id!==id&&!blocked(id,u.id)&&visible(u,id)&&!encounters.occupied(u.id)&&encounters.settings(u)[b.mode==='video'?'strangerCalls':'strangerChats']&&encounters.balance(u).total>0&&chat.online(u.id));
        if(!candidates.length)fail(404,'No available people right now. Try again soon.');const target=candidates[Math.floor(Math.random()*candidates.length)];return send(200,{meeting:encounters.view(encounters.invite(id,target.id,b.mode),id)});
      }
      const em=path.match(new RegExp("^/discover/([^/]+)/(accept|decline|end|heartbeat|ready)$"));
      if(em&&req.method==='POST'){const e=db.encounters.find(e=>e.id===em[1]&&e.members.includes(id));if(!e)fail(404,'Meeting not found');if(em[2]==='accept'){if(e.members.some(pid=>!chat.online(pid)))fail(409,'Both people must be online to meet');if(b.consent!==true)fail(400,'Accept the meeting terms to continue');encounters.accept(e,id);}else if(em[2]==='ready'){encounters.open(e,id);}else if(em[2]==='heartbeat'){if(['connecting','active'].includes(e.status))e.seen[id]=clock();}else encounters.end(e,em[2]);save();return send(200,{meeting:encounters.view(e,id),balance:encounters.balance(me)});}
      if(path==='/games/invite'&&req.method==='POST'){if(!friends(id,b.user))fail(403,'Invite accepted friends only');const game=db.games?.find(g=>g.code===String(b.code).toUpperCase()&&g.phase==='lobby'&&g.players.some(p=>p.id===id));if(!game)fail(404,'Create a game lobby first');if(!db.invites.some(x=>x.to===b.user&&x.code===game.code&&clock()-x.time<60000))db.invites.push({id:randomUUID(),from:id,to:b.user,code:game.code,type:game.type,time:clock()});save();return send(200,{ok:true});}
      if(req.method==='GET'&&path==='/state'){me.lastSeen=clock();const meeting=encounters.active(id);if(meeting)meeting.seen[id]=clock();save();
        return send(200,{mediaLimits:{maxBytes:MAX_MEDIA_BYTES,voiceMs:MAX_VOICE_MS},me:publicUser(me,id),settings:encounters.settings(me),discovery:encounters.state(id),gameInvites:db.invites.filter(x=>x.to===id&&clock()-x.time<3600000),discover:db.users.filter(x=>x.id!==id&&!blocked(id,x.id)&&visible(x,id)).slice(0,200).map(u=>publicUser(u,id)),friends:db.users.filter(x=>friends(id,x.id)).map(u=>publicUser(u,id)),requests:db.requests.filter(x=>x.to===id&&!blocked(id,x.from)).map(x=>({...x,person:publicUser(requireUser(x.from),id)})),sent:db.requests.filter(x=>x.from===id).map(x=>x.to),rooms:db.rooms.filter(r=>safeRoom(r,id)).map(r=>roomView(r,id)),locations:db.shares.filter(x=>x.expires>clock()&&x.audience.includes(id)&&friends(id,x.user)).map(x=>({...x,person:publicUser(requireUser(x.user),id),audience:undefined})),calls:db.calls.filter(c=>c.to===id&&c.status==='ringing'&&nowCall(c)&&(friends(id,c.from)||c.encounter&&encounters.active(id)?.id===c.encounter)).map(c=>({...c,person:publicUser(requireUser(c.from),id)}))});
      }
      // Friend finder hook: query an exact canonical username, then use the returned UUID in /friends/request.
      if(req.method==='GET'&&path==='/people/by-username'){const found=db.users.find(u=>u.username===usernameKey(url.searchParams.get('username'))&&u.id!==id&&!blocked(id,u.id)&&visible(u,id));if(!found)fail(404,'No person found with that username');return send(200,{person:publicUser(found,id)});}
      if(req.method==='POST'&&path==='/friends/request'){
        const to=clean(b.user);requireUser(to);if(encounters.occupied(to)&&!encounters.active(to)?.members.includes(id))fail(409,'This person is busy. Please try later');if(to===id||blocked(id,to))fail(403,'Cannot send this request');if(friends(id,to))return send(200,{ok:true});
        if(!db.requests.some(x=>x.from===id&&x.to===to))db.requests.push({id:randomUUID(),from:id,to});save();return send(200,{ok:true});
      }
      if(req.method==='POST'&&path==='/friends/respond'){
        const request=db.requests.find(x=>x.id===b.request&&x.to===id);if(!request||blocked(id,request.from))fail(404,'Request not found');
        if(b.accept===true){if(!friends(id,request.from))db.friends.push([id,request.from]);roomFor(id,request.from);}
        db.requests=db.requests.filter(x=>x!==request&&!(x.from===id&&x.to===request.from));save();return send(200,{ok:true});
      }
      if(req.method==='POST'&&path==='/rooms'){
        if(b.group){const members=[...new Set([id,...(Array.isArray(b.members)?b.members:[])])];if(members.length<2||members.length>30||members.some(x=>x!==id&&!friends(id,x)))fail(403,'Choose between 1 and 29 accepted friends');const name=clean(b.name,50);if(!name)fail(400,'Add a group name');const r={id:randomUUID(),group:true,name,members,owner:id};db.rooms.push(r);save();return send(200,{room:roomView(r,id)});}
        if(!friends(id,b.user))fail(403,'Accept a friend request before chatting');const r=roomFor(id,b.user);save();return send(200,{room:roomView(r,id)});
      }
      const uploadRoute=path.match(new RegExp("^/rooms/([^/]+)/media$"));
      if(uploadRoute&&req.method==='POST')return send(200,await messaging.upload(req,uploadRoute[1],id));
      const msg=path.match(new RegExp("^/rooms/([^/]+)/messages$"));
      if(msg){const r=db.rooms.find(x=>x.id===msg[1]);if(!safeRoom(r,id))fail(403,'This conversation is unavailable');if(req.method==='GET')return send(200,{messages:messaging.list(r.id,id)});if(req.method==='POST'){if(r.encounter&&db.encounters.find(e=>e.id===r.encounter)?.status!=='active')fail(409,'Waiting for both people to open the meeting');if(encounters.active(id)&&encounters.active(id).room!==r.id||r.members.some(pid=>encounters.active(pid)&&encounters.active(pid).room!==r.id))fail(409,'This person is busy in another connection');return send(200,{message:await messaging.create(r,id,me.name,b)});}}
      if(path==='/inbox'&&req.method==='GET')return send(200,{messages:db.messages.filter(m=>m.from!==id&&!(m.delivered||[]).includes(id)&&safeRoom(db.rooms.find(r=>r.id===m.room),id)).slice(-200).map(m=>messaging.list(m.room,id).find(x=>x.id===m.id)).filter(Boolean)});
      const receipt=path.match(new RegExp("^/rooms/([^/]+)/receipts$"));
      if(receipt&&req.method==='POST')return send(200,messaging.receipts(receipt[1],id,b.ids,b.read===true));
      const action=path.match(new RegExp("^/messages/([^/]+)/(delete|reaction)$"));
      if(action&&req.method==='POST')return send(200,{message:action[2]==='delete'?await messaging.remove(action[1],id):messaging.react(action[1],id,b.emoji)});
      const media=path.match(new RegExp("^/media/([a-f0-9-]+)$"));
      if(media&&req.method==='GET'){const item=await messaging.media(media[1],id);res.writeHead(200,{'Content-Type':item.mime,'Content-Length':item.size,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});createReadStream(item.path).on('error',()=>res.destroy()).pipe(res);return;}
      if(path==='/location'&&req.method==='DELETE'){me.settings={...encounters.settings(me),ghost:true};db.shares=db.shares.filter(x=>x.user!==id);save();return send(200,{ok:true});}
      if(path==='/location'&&req.method==='PUT'){if(encounters.settings(me).ghost)fail(403,'Turn off Ghost Mode before sharing');
        const lat=Number(b.lat),lon=Number(b.lon),expires=Number(b.expires),audience=[...new Set(Array.isArray(b.audience)?b.audience:[])];
        if(!Number.isFinite(lat)||Math.abs(lat)>85||!Number.isFinite(lon)||Math.abs(lon)>180||!Number.isFinite(expires)||expires<=clock()||expires>clock()+4*3600000+10000)fail(400,'Invalid location or duration');
        if(!audience.length||audience.length>30||audience.some(x=>!friends(id,x)))fail(403,'Share only with selected accepted friends');
        db.shares=db.shares.filter(x=>x.user!==id);db.shares.push({user:id,lat,lon,heading:Number.isFinite(Number(b.heading))?((Number(b.heading)%360)+360)%360:0,expires,audience,updated:clock()});save();return send(200,{ok:true});
      }
      if(path==='/block'&&req.method==='POST'){
        const who=clean(b.user);requireUser(who);if(who===id)fail(400,'Cannot block yourself');const connection=encounters.active(id);if(connection?.members.includes(who))encounters.end(connection,'blocked');if(!db.blocks.some(x=>x.by===id&&x.user===who))db.blocks.push({by:id,user:who});db.requests=db.requests.filter(x=>!([x.from,x.to].includes(id)&&[x.from,x.to].includes(who)));db.calls.filter(c=>[c.from,c.to].includes(id)&&[c.from,c.to].includes(who)).forEach(c=>c.status='ended');save();return send(200,{ok:true});
      }
      if(path==='/report'&&req.method==='POST'){requireUser(b.user);db.reports.push({id:randomUUID(),by:id,user:b.user,reason:clean(b.reason,500),time:clock()});save();return send(200,{ok:true});}
      if(path==='/calls'&&req.method==='POST'){
        if(!friends(id,b.user))fail(403,'A mutual Discover meeting is required to call a non-friend');if(encounters.active(id)||encounters.active(b.user))fail(409,'One of you is busy in a Discover connection');if(db.calls.some(c=>nowCall(c)&&([c.from,c.to].includes(id)||[c.from,c.to].includes(b.user))))fail(409,'One of you is already in a call');const c={id:randomUUID(),from:id,to:b.user,status:'ringing',created:clock()};db.calls.push(c);save();return send(200,{call:c});
      }
      const cm=path.match(/^\/calls\/([^/]+)(?:\/(accept|end|signals))?$/);
      if(cm){const c=db.calls.find(x=>x.id===cm[1]);if(!c||![c.from,c.to].includes(id)||blocked(c.from,c.to)||(!friends(c.from,c.to)&&!(c.encounter&&encounters.active(id)?.id===c.encounter)))fail(403,'Call unavailable');if(!nowCall(c))c.status='ended';
        if(req.method==='GET'&&!cm[2])return send(200,{call:c,iceServers:[{urls:['stun:stun.l.google.com:19302']},...(process.env.TURN_URL?[{urls:[process.env.TURN_URL],username:process.env.TURN_USERNAME||'',credential:process.env.TURN_PASSWORD||''}]:[])]});
        if(req.method==='POST'&&cm[2]==='accept'){if(c.to!==id||c.status!=='ringing')fail(409,'Call cannot be accepted');c.status='accepted';save();return send(200,{ok:true});}
        if(req.method==='POST'&&cm[2]==='end'){if(c.encounter)encounters.end(db.encounters.find(e=>e.id===c.encounter),'call ended');c.status='ended';db.signals=db.signals.filter(x=>x.call!==c.id);save();return send(200,{ok:true});}
        if(cm[2]==='signals'){if(c.status!=='accepted')fail(409,'Call is not active');if(req.method==='GET'){const after=Number(new URL(req.url,'http://localhost').searchParams.get('after')||0);return send(200,{signals:db.signals.filter(x=>x.call===c.id&&x.from!==id&&x.seq>after)});}if(req.method==='POST'){if(!['offer','answer','ice'].includes(b.type)||typeof b.data!=='string'||b.data.length>30000)fail(400,'Invalid call signal');if(db.signals.filter(x=>x.call===c.id).length>300)fail(429,'Too many call signals');const signal={call:c.id,from:id,seq:(db.signals.at(-1)?.seq||0)+1,type:b.type,data:b.data};db.signals.push(signal);return send(200,{ok:true});}}
      }
      fail(404,'Not found');
    }catch(e){send(e.status||500,{error:e.status?e.message:'Server error'});}
  });
  const sweeper=setInterval(()=>{encounters.sweep();encounters.reloadDaily();messaging.sweepUploads()},3000);sweeper.unref();const billingTimer=setInterval(()=>billing.reconcile().catch(()=>{}),900000);billingTimer.unref();server.on('close',()=>{clearInterval(sweeper);clearInterval(billingTimer);void durableMedia?.close()});
  server.requestTimeout=30*60*1000;
  chat=attachChat(server,{user,save,clock,authenticate:token=>{const s=db.sessions.find(x=>x.hash===hash(token)&&x.expires>clock());return s?user(s.user):null;}});
  attachGames(server,{db,save,blocked,clock,authenticate:token=>{const s=db.sessions.find(x=>x.hash===hash(token)&&x.expires>clock());return s?user(s.user):null;}});
  return server;
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  configurePublicOrigin();
  let storage=null;
  if(process.env.DATABASE_URL){const {openPostgres}=await import('./postgres.mjs');storage=openPostgres();}
  const server=createArtiiServer({storage});const port=Number(process.env.PORT||8787);
  server.listen(port,process.env.HOST||'127.0.0.1',()=>console.log('Artii API ready on port '+port));
  let stopping=false;const sockets=new Set();server.on('connection',socket=>{sockets.add(socket);socket.on('close',()=>sockets.delete(socket));});
  function shutdown(){if(stopping)return;stopping=true;server.close(()=>{storage?.close();process.exit(0);});for(const socket of sockets)socket.end();setTimeout(()=>{for(const socket of sockets)socket.destroy();},5000).unref();setTimeout(()=>process.exit(1),10000).unref();}
  process.on('SIGTERM',shutdown);process.on('SIGINT',shutdown);
}
