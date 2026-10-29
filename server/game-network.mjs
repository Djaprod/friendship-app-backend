import { createHash } from 'node:crypto';
import { createGame,joinGame,actGame,advanceGame,gameView } from './games.mjs';
import { randomInt } from 'node:crypto';

// Dependency-free RFC 6455 transport. Clients authenticate before subscribing;
// every snapshot is serialized separately for the authenticated player.
export function attachGames(server,{db,save,authenticate,blocked,clock=Date.now}){
  db.games ||= [];const peers=new Set();
  const frame=(opcode,payload)=>{const b=Buffer.from(payload);if(b.length<126)return Buffer.concat([Buffer.from([128|opcode,b.length]),b]);if(b.length<65536){const h=Buffer.alloc(4);h[0]=128|opcode;h[1]=126;h.writeUInt16BE(b.length,2);return Buffer.concat([h,b])}const h=Buffer.alloc(10);h[0]=128|opcode;h[1]=127;h.writeBigUInt64BE(BigInt(b.length),2);return Buffer.concat([h,b]);};
  const send=(p,value)=>{if(!p.socket.destroyed){if(p.socket.writableLength>2000000){p.socket.destroy();return}p.socket.write(frame(1,JSON.stringify(value)))}};
  const snapshot=p=>{if(!p.user)return;if(p.game){const g=db.games.find(x=>x.id===p.game);if(g){const view=gameView(g,p.user.id,clock());const connected=new Set([...peers].filter(x=>x.game===g.id&&x.user).map(x=>x.user.id));view.players=view.players.map(x=>({...x,online:!!x.bot||connected.has(x.id)}));send(p,{type:'state',game:view})}}else send(p,{type:'ready',games:db.games.filter(g=>g.players.some(x=>x.id===p.user.id)).slice(-20).map(g=>({code:g.code,type:g.type,phase:g.phase}))})};
  const broadcast=g=>{for(const p of peers)if(p.game===g.id)snapshot(p)};
  const mutate=g=>{g.version++;save();broadcast(g)};
  function message(p,data){
    try{
      const time=clock();if(time-p.rateAt>1000){p.rateAt=time;p.rate=0}if(++p.rate>15)throw Error('Please slow down');
      const b=JSON.parse(data);
      if(b.type==='hello'){if(p.user)throw Error('This connection is already signed in');const token=String(b.token||'');const account=authenticate(token);if(!account)throw Error('Please sign in to play');p.user=account;p.authToken=token;snapshot(p);return;}
      if(!p.user||!p.authToken||!authenticate(p.authToken))throw Error('Session expired; sign in again');
      if(b.type==='create'){
        if(db.games.filter(g=>g.host===p.user.id&&g.phase!=='over').length>=5)throw Error('You already host five active rooms');
        let code;do{code=Array.from({length:4},()=>String.fromCharCode(65+randomInt(26))).join('')}while(db.games.some(g=>g.code===code));
        const g=createGame(b.game,p.user,code,time);db.games.push(g);p.game=g.id;save();snapshot(p);return;
      }
      if(b.type==='join'){
        const code=String(b.code||'').trim().toUpperCase();const g=db.games.find(x=>x.code===code);if(!g)throw Error('Room code not found');if(g.players.some(x=>blocked(p.user.id,x.id)))throw Error('This room is unavailable');joinGame(g,p.user);p.game=g.id;mutate(g);return;
      }
      if(b.type==='leave'){
        const g=db.games.find(x=>x.code===String(b.code||'').trim().toUpperCase());
        if(!g||!g.players.some(x=>x.id===p.user.id))throw Error('You are not in this room');
        if(g.phase!=='lobby'&&g.phase!=='over'){g.phase='over';g.deadline=0;g.winner='Game ended: a player left';}
        g.players=g.players.filter(x=>x.id!==p.user.id);
        if(g.host===p.user.id)g.host=g.players.find(x=>!x.bot)?.id||'';
        if(!g.players.some(x=>!x.bot))db.games=db.games.filter(x=>x!==g);
        g.version++;save();
        for(const peer of peers){if(peer.user?.id===p.user.id){if(peer.game===g.id)peer.game=null;send(peer,{type:'left',games:db.games.filter(x=>x.players.some(y=>y.id===p.user.id)).map(x=>({code:x.code,type:x.type,phase:x.phase}))})}else if(peer.game===g.id)snapshot(peer);}
        return;
      }
      if(b.type==='home'){p.game=null;snapshot(p);return;}
      if(b.type==='action'){
        const g=db.games.find(x=>x.id===p.game);if(!g)throw Error('Join a game first');
        if(advanceGame(g,time)){mutate(g);throw Error('The turn changed. Try again.');}
        if(b.version!==g.version){snapshot(p);throw Error('The room updated. Try again.');}
        actGame(g,p.user.id,b,time);mutate(g);return;
      }
      throw Error('Unknown game request');
    }catch(e){send(p,{type:'error',message:e.message||'Game request failed'})}
  }
  server.on('upgrade',(req,socket,head)=>{
    if(req.url!=='/games/socket')return;
    if(req.headers.upgrade?.toLowerCase()!=='websocket'||req.headers['sec-websocket-version']!=='13'){socket.destroy();return;}
    const key=req.headers['sec-websocket-key'];if(typeof key!=='string'||Buffer.from(key,'base64').length!==16){socket.destroy();return}
    if(peers.size>=200){socket.destroy();return}
    socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: '+createHash('sha1').update(key+'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64')+'\r\n\r\n');
    const p={socket,buffer:Buffer.alloc(0),parts:[],partSize:0,user:null,game:null,rate:0,rateAt:clock(),authToken:null};peers.add(p);
    const authTimer=setTimeout(()=>{if(!p.user)socket.destroy()},10000);authTimer.unref();
    socket.on('close',()=>{peers.delete(p);clearTimeout(authTimer);const g=db.games.find(x=>x.id===p.game);if(g)broadcast(g)});socket.on('error',()=>socket.destroy());
    function consume(chunk){p.buffer=Buffer.concat([p.buffer,chunk]);if(p.buffer.length>1000000){socket.destroy();return}
      while(p.buffer.length>=2){const b=p.buffer;const fin=!!(b[0]&128),op=b[0]&15;let length=b[1]&127,offset=2;if(b[0]&112||!(b[1]&128)){socket.destroy();return}if(length===126){if(b.length<4)return;length=b.readUInt16BE(2);offset=4}else if(length===127){if(b.length<10)return;const l=b.readBigUInt64BE(2);if(l>65536n){socket.destroy();return}length=Number(l);offset=10}if(length>65536||op>=8&&(!fin||length>125)){socket.destroy();return}if(b.length<offset+4+length)return;const mask=b.subarray(offset,offset+4);const payload=Buffer.from(b.subarray(offset+4,offset+4+length));for(let i=0;i<length;i++)payload[i]^=mask[i%4];p.buffer=b.subarray(offset+4+length);
        if(op===8){socket.end(frame(8,Buffer.alloc(0)));return}if(op===9){socket.write(frame(10,payload));continue}if(op===10)continue;
        if(op!==0&&op!==1||op===0&&!p.parts.length||op===1&&p.parts.length){socket.destroy();return}
        p.parts.push(payload);p.partSize+=length;if(p.partSize>65536){socket.destroy();return}if(fin){const text=Buffer.concat(p.parts).toString('utf8');p.parts=[];p.partSize=0;message(p,text)}
      }
    }
    socket.on('data',consume);if(head.length)consume(head);
  });
  const timer=setInterval(()=>{for(const g of db.games){if(advanceGame(g,clock()))mutate(g)}for(const p of peers){if(p.user&&!authenticate(p.authToken)){send(p,{type:'error',message:'Session expired'});p.socket.destroy()}else if(p.game)send(p,{type:'clock',now:clock()})}},1000);timer.unref();
  server.on('close',()=>{clearInterval(timer);for(const p of peers)p.socket.destroy()});
}
