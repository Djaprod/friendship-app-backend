import { randomInt, randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
export const WORDS=JSON.parse(readFileSync(new URL('./words.json',import.meta.url),'utf8'));
export const COLORS=['blue','yellow','green','red'];
export const SAFE=new Set([0,8,13,21,26,34,39,47]);
const limits={ludo:[2,4],undercover:[3,10],werewolf:[5,15]};
const fail=m=>{throw Object.assign(new Error(m),{status:400})};
const alive=g=>g.players.filter(p=>p.alive);
const shuffle=(a,pick)=>{a=[...a];for(let i=a.length-1;i>0;i--){const j=pick(i+1);[a[i],a[j]]=[a[j],a[i]];}return a;};
function phase(g,name,seconds,now){g.phase=name;g.deadline=now+seconds*1000;g.votes={};g.actions={};}
function log(g,text){g.chat.push({id:randomUUID(),name:'Artii',from:'system',text,channel:'public'});g.chat=g.chat.slice(-150);}
export function createGame(type,host,code,now=Date.now()){
  if(!Object.hasOwn(limits,type))fail('Choose Ludo, Werewolf, or Undercover');
  return {id:randomUUID(),code,type,host:host.id,phase:'lobby',players:[{id:host.id,name:host.username||host.name,avatar:host.avatar||'',color:'blue',alive:true}],config:{category:'1_animals',undercover:1,white:0,wolves:1},chat:[],version:1,created:now,deadline:0,votes:{},actions:{},roles:{},words:{},revealed:{},inspections:{},rank:[],round:0};
}
export function joinGame(g,p){if(g.players.some(x=>x.id===p.id))return;if(g.phase!=='lobby')fail('This game has already started');if(g.players.length>=limits[g.type][1])fail('Room is full');g.players.push({id:p.id,name:p.username||p.name,avatar:p.avatar||'',color:COLORS.find(c=>!g.players.some(x=>x.color===c))||'blue',alive:true});}
export function legalTokens(g,id=g.turn){const p=g.players.find(x=>x.id===id);if(!p||!g.dice)return [];return p.tokens.map((step,i)=>step<0?(g.dice===6?i:-1):(step<56&&step+g.dice<=56?i:-1)).filter(i=>i>=0);}
function nextLudo(g,extra,now){g.dice=0;const current=g.players.find(x=>x.id===g.turn);if(!extra||current.tokens.every(x=>x===56)){let i=g.players.indexOf(current);do{i=(i+1)%g.players.length}while(g.rank.includes(g.players[i].id));g.turn=g.players[i].id;}g.deadline=now+20000;g.botAt=now+1000;}
function moveLudo(g,index,now){
  if(!legalTokens(g).includes(index))fail('That token cannot move');const p=g.players.find(x=>x.id===g.turn);const previous=p.tokens[index];const dice=g.dice;const step=previous<0?0:previous+dice;p.tokens[index]=step;let capture=false;
  const position=(COLORS.indexOf(p.color)*13+step)%52;
  if(step<=50&&!SAFE.has(position))for(const other of g.players.filter(x=>x!==p))other.tokens=other.tokens.map(t=>{if(t>=0&&t<=50&&(COLORS.indexOf(other.color)*13+t)%52===position){capture=true;return -1}return t;});
  g.lastMove={player:p.id,token:index,from:previous,to:step,id:randomUUID()};
  if(capture)log(g,`${p.name} captured a token and gets another turn.`);
  if(p.tokens.every(x=>x===56)&&!g.rank.includes(p.id)){g.rank.push(p.id);log(g,`${p.name} finished in position ${g.rank.length}.`);}
  if(g.rank.length>=g.players.length-1){g.rank.push(...g.players.filter(x=>!g.rank.includes(x.id)).map(x=>x.id));g.phase='over';g.winner=g.players.find(x=>x.id===g.rank[0]).name;g.deadline=0;return;}
  nextLudo(g,dice===6||capture,now);
}
function rollLudo(g,now,pick){if(g.dice)fail('Move a highlighted token first');g.dice=pick(6)+1;g.lastDice=g.dice;g.rollId=randomUUID();g.deadline=now+20000;if(!legalTokens(g).length){log(g,`${g.players.find(p=>p.id===g.turn).name} rolled ${g.dice}; no legal moves.`);nextLudo(g,false,now)}}
function checkWinner(g){
  const bad=alive(g).filter(p=>g.type==='werewolf'?g.roles[p.id]==='Werewolf':g.roles[p.id]!=='Civilian').length;const good=alive(g).length-bad;
  if(bad===0||bad>=good){g.phase='over';g.winner=bad===0?(g.type==='werewolf'?'Villagers':'Civilians'):(g.type==='werewolf'?'Werewolves':'Undercover team');g.deadline=0;log(g,`${g.winner} win!`);return true}return false;
}
function descriptions(g,now){g.round++;g.order=alive(g).map(p=>p.id);g.speaker=0;phase(g,'describe',45,now);log(g,`Round ${g.round}: give one-word hints.`);}
function startNight(g,now){g.round++;g.nightTarget=null;g.protected=null;phase(g,'night_wolves',30,now);log(g,`Night ${g.round}. The village sleeps.`);}
function start(g,now,pick){
  if(g.players.length<limits[g.type][0])fail(`Invite at least ${limits[g.type][0]} players`);
  if(g.type==='ludo'){g.players.sort((a,b)=>COLORS.indexOf(a.color)-COLORS.indexOf(b.color));g.players.forEach(p=>p.tokens=[-1,-1,-1,-1]);g.turn=g.players[0].id;g.dice=0;phase(g,'playing',20,now);g.botAt=now+1000;return;}
  const ids=shuffle(g.players.map(p=>p.id),pick);
  if(g.type==='undercover'){
    const {undercover,white,category}=g.config;if(![1,2].includes(undercover)||![0,1].includes(white)||2*(undercover+white)>=ids.length)fail('The civilian team must start with a majority');
    const pairs=WORDS[category];if(!pairs)fail('Choose a word category');const pair=pairs[pick(pairs.length)];
    ids.forEach((id,i)=>{g.roles[id]=i<undercover?'Undercover':i<undercover+white?'Mr. White':'Civilian';g.words[id]=g.roles[id]==='Mr. White'?'':g.roles[id]==='Undercover'?pair.word_b:pair.word_a});descriptions(g,now);
  }else{const wolves=g.config.wolves;if(!Number.isInteger(wolves)||wolves<1||wolves>Math.floor((ids.length-3)/2))fail('Too many werewolves for this village');ids.forEach((id,i)=>g.roles[id]=i<wolves?'Werewolf':i===wolves?'Seer':i===wolves+1?'Doctor':'Villager');startNight(g,now);}
}
function tally(votes){const counts={};Object.values(votes).forEach(id=>counts[id]=(counts[id]||0)+1);const max=Math.max(0,...Object.values(counts));return {counts,tied:Object.keys(counts).filter(k=>counts[k]===max)};}
function voteResult(g,now){
  const {counts,tied}=tally(g.votes);g.breakdown=counts;
  if(tied.length===1){const p=g.players.find(p=>p.id===tied[0]);p.alive=false;g.revealed[p.id]=g.roles[p.id];g.eliminated=p.id;log(g,`${p.name} was eliminated. Role: ${g.roles[p.id]}.`);}else{g.eliminated=null;log(g,'The vote tied or no votes were cast. Nobody is eliminated.');}
  if(!checkWinner(g))phase(g,'reveal',8,now);
}
function nextNight(g,now){
  if(g.phase==='night_wolves'){const {tied}=tally(g.actions);g.nightTarget=tied.length===1?tied[0]:null;phase(g,'night_seer',20,now);}
  else if(g.phase==='night_seer')phase(g,'night_doctor',20,now);
  else if(g.phase==='night_doctor'){
    const p=g.players.find(x=>x.id===g.nightTarget);if(p&&p.id!==g.protected){p.alive=false;g.revealed[p.id]=g.roles[p.id];log(g,`${p.name} did not survive the night. Role: ${g.roles[p.id]}.`);}else log(g,'Everyone survived the night.');
    if(!checkWinner(g))phase(g,'discussion',60,now);
  }
}
export function advanceGame(g,now=Date.now(),pick=randomInt){
  if(['lobby','over'].includes(g.phase))return false;
  if(g.type==='ludo'){
    const p=g.players.find(x=>x.id===g.turn);if((p.bot&&now>=g.botAt)||now>=g.deadline){if(!g.dice)rollLudo(g,now,pick);if(g.turn===p.id&&g.dice){const legal=legalTokens(g);if(legal.length)moveLudo(g,legal[0],now);else nextLudo(g,false,now)}return true;}return false;
  }
  if(g.phase.startsWith('night_')){
    const role={night_wolves:'Werewolf',night_seer:'Seer',night_doctor:'Doctor'}[g.phase];const actors=alive(g).filter(p=>g.roles[p.id]===role);
    if(!actors.length||actors.every(p=>g.actions[p.id])||now>=g.deadline){nextNight(g,now);return true}return false;
  }
  if(g.phase==='vote'&&(Object.keys(g.votes).length>=alive(g).length||now>=g.deadline)){voteResult(g,now);return true;}
  if(now<g.deadline)return false;
  if(g.phase==='describe'){g.speaker++;if(g.speaker>=g.order.length)phase(g,'discussion',30,now);else g.deadline=now+45000;}
  else if(g.phase==='discussion')phase(g,'vote',60,now);
  else if(g.phase==='reveal'){if(g.type==='undercover')descriptions(g,now);else startNight(g,now);}
  return true;
}
export function actGame(g,id,b,now=Date.now(),pick=randomInt){
  const p=g.players.find(x=>x.id===id);if(!p)fail('Join this room first');const a=b.action;
  if(a==='chat'){
    const text=String(b.text||'').trim().slice(0,500);if(!text)fail('Write a message');let channel='public';
    if(!p.alive)channel='ghost';else if(g.phase.startsWith('night_')){if(g.roles[id]!=='Werewolf')fail('The village chat is locked at night');channel='wolves';}
    else if(!['lobby','discussion','over','playing'].includes(g.phase))fail('Chat opens during discussion');
    g.chat.push({id:randomUUID(),from:id,name:p.name,text,channel});g.chat=g.chat.slice(-150);return;
  }
  if(g.phase==='lobby'){
    if(id!==g.host)fail('Only the host can change the lobby');
    if(a==='config'){
      if(b.category!==undefined&&!Object.hasOwn(WORDS,b.category))fail('Unknown word category');
      if(b.category!==undefined)g.config.category=b.category;
      for(const k of ['undercover','white','wolves'])if(b[k]!==undefined){if(!Number.isInteger(b[k]))fail('Invalid role count');g.config[k]=b[k]};return;
    }
    if(a==='bot'){if(g.type!=='ludo'||g.players.length>=4)fail('No bot slot available');const color=COLORS.find(c=>!g.players.some(x=>x.color===c));g.players.push({id:'bot-'+randomUUID(),name:`${color[0].toUpperCase()+color.slice(1)} bot`,avatar:'',color,bot:true,alive:true});return;}
    if(a==='color'){if(g.type!=='ludo'||!COLORS.includes(b.color))fail('Invalid color');const target=g.players.find(x=>x.id===b.player);if(!target)fail('Unknown player');const other=g.players.find(x=>x.color===b.color);if(other)other.color=target.color;target.color=b.color;return;}
    if(a==='start'){start(g,now,pick);return;}
    fail('Invalid lobby action');
  }
  if(!p.alive||g.phase==='over')fail('You cannot act in this phase');
  if(g.type==='ludo'){
    if(g.turn!==id)fail('Wait for your turn');if(a==='roll'){rollLudo(g,now,pick);return}if(a==='move'){moveLudo(g,Number(b.token),now);return}fail('Choose roll or move');
  }
  if(a==='hint'){
    if(g.phase!=='describe'||g.order[g.speaker]!==id)fail('Wait for your hint turn');const text=String(b.text||'').trim();if(!text||text.length>40||/\s/.test(text))fail('Your hint must be exactly one word');
    if(Object.values(g.words).some(w=>w.toLowerCase()===text.toLowerCase()))fail('Do not use a secret word as your hint');
    g.chat.push({id:randomUUID(),from:id,name:p.name,text,channel:'public',hint:true});g.speaker++;if(g.speaker>=g.order.length)phase(g,'discussion',30,now);else g.deadline=now+45000;return;
  }
  const target=g.players.find(x=>x.id===b.target&&x.alive);
  if(!target)fail('Choose a living player');
  if(a==='vote'){if(g.phase!=='vote'||target.id===id)fail('Choose another player during voting');if(g.votes[id])fail('Your vote is already cast');g.votes[id]=target.id;advanceGame(g,now,pick);return;}
  if(a==='night'){
    const role=g.roles[id];if(g.actions[id])fail('You already acted this night');
    if(g.phase==='night_wolves'&&role==='Werewolf'){if(g.roles[target.id]==='Werewolf')fail('Choose a villager');g.actions[id]=target.id;}
    else if(g.phase==='night_seer'&&role==='Seer'){if(target.id===id)fail('Inspect someone else');g.inspections[id]={name:target.name,alignment:g.roles[target.id]==='Werewolf'?'Werewolf':'Villager'};g.actions[id]=target.id;}
    else if(g.phase==='night_doctor'&&role==='Doctor'){g.protected=target.id;g.actions[id]=target.id;}
    else fail('Your role is asleep');advanceGame(g,now,pick);return;
  }
  fail('Action unavailable');
}
export function gameView(g,id,now=Date.now()){
  const p=g.players.find(x=>x.id===id);if(!p)fail('Room is private');
  const role=g.roles[id];const out={id:g.id,code:g.code,type:g.type,host:g.host,phase:g.phase,version:g.version,now,deadline:g.deadline,config:g.config,round:g.round,winner:g.winner,rank:g.rank,players:g.players.map(x=>({...x,role:g.phase==='over'?g.roles[x.id]:g.revealed[x.id]})),chat:g.chat.filter(m=>m.channel==='public'||m.channel==='ghost'&&!p.alive||m.channel==='wolves'&&role==='Werewolf'&&p.alive),my:{id,alive:p.alive,role,word:g.words[id],voted:!!g.votes[id],acted:!!g.actions[id]}};
  if(g.phase==='reveal'||g.phase==='over')out.breakdown=g.breakdown;
  if(g.type==='ludo')Object.assign(out,{turn:g.turn,dice:g.dice,lastDice:g.lastDice,rollId:g.rollId,legal:g.turn===id?legalTokens(g):[],lastMove:g.lastMove});
  if(g.phase==='describe')out.speaker=g.order[g.speaker];
  if(role==='Werewolf'){out.my.team=g.players.filter(x=>g.roles[x.id]==='Werewolf').map(x=>x.id);if(g.phase==='night_wolves')out.my.packVotes=g.actions;}
  if(role==='Seer')out.my.inspection=g.inspections[id];
  return out;
}
