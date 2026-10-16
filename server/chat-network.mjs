import {createHash} from 'node:crypto';
export function attachChat(server,{authenticate,user,save,clock=Date.now}){
 const peers=new Set();
   const frame=(opcode,payload)=>{const b=Buffer.from(payload);if(b.length<126)return Buffer.concat([Buffer.from([128|opcode,b.length]),b]);if(b.length<65536){const h=Buffer.alloc(4);h[0]=128|opcode;h[1]=126;h.writeUInt16BE(b.length,2);return Buffer.concat([h,b])}const h=Buffer.alloc(10);h[0]=128|opcode;h[1]=127;h.writeBigUInt64BE(BigInt(b.length),2);return Buffer.concat([h,b]);};
  const send=(p,value)=>{if(!p.socket.destroyed){if(p.socket.writableLength>2000000){p.socket.destroy();return}p.socket.write(frame(1,JSON.stringify(value)))}};

 const online=id=>[...peers].some(p=>p.user?.id===id&&p.focused&&clock()-p.seen<15000);
 const status=new Map();
 const offline=id=>{const active=online(id),was=status.get(id)||false;status.set(id,active);const u=user(id);if(u&&was&&!active){u.presenceLastSeen=clock();save();}};
 let queued=null;
 function publish(){if(queued)return;queued=setTimeout(()=>{queued=null;for(const p of peers){if(p.user&&authenticate(p.authToken))send(p,{type:'sync'});else if(p.user)p.socket.destroy();}},20);queued.unref();}
 function message(p,data){try{
  const now=clock();if(now-p.rateAt>1000){p.rateAt=now;p.rate=0;}if(++p.rate>20)throw Error('Too many events');
  const b=JSON.parse(data);
  if(b.type==='hello'){if(p.user)throw Error('Already authenticated');p.authToken=String(b.token||'');p.user=authenticate(p.authToken);if(!p.user)throw Error('Session expired');p.focused=b.focused===true;p.seen=clock();offline(p.user.id);send(p,{type:'ready'});publish();return;}
  if(!p.user||!authenticate(p.authToken))throw Error('Session expired');
  p.seen=clock();
  if(b.type==='focus'){p.focused=b.focused===true;offline(p.user.id);publish();}
  else if(b.type==='ping')send(p,{type:'pong'});
  else throw Error('Unknown event');
 }catch{send(p,{type:'error'});p.socket.destroy();}}
   server.on('upgrade',(req,socket,head)=>{
    if(req.url!=='/chat/socket')return;
    if(req.headers.upgrade?.toLowerCase()!=='websocket'||req.headers['sec-websocket-version']!=='13'){socket.destroy();return;}
    const key=req.headers['sec-websocket-key'];if(typeof key!=='string'||Buffer.from(key,'base64').length!==16){socket.destroy();return}
    if(peers.size>=200){socket.destroy();return}
    socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: '+createHash('sha1').update(key+'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64')+'\r\n\r\n');
    const p={socket,buffer:Buffer.alloc(0),parts:[],partSize:0,user:null,focused:false,rate:0,rateAt:clock(),authToken:null,seen:clock()};peers.add(p);
    const authTimer=setTimeout(()=>{if(!p.user)socket.destroy()},10000);authTimer.unref();
    socket.on('close',()=>{peers.delete(p);clearTimeout(authTimer);if(p.user){offline(p.user.id);publish()}});socket.on('error',()=>socket.destroy());
    function consume(chunk){p.buffer=Buffer.concat([p.buffer,chunk]);if(p.buffer.length>1000000){socket.destroy();return}
      while(p.buffer.length>=2){const b=p.buffer;const fin=!!(b[0]&128),op=b[0]&15;let length=b[1]&127,offset=2;if(b[0]&112||!(b[1]&128)){socket.destroy();return}if(length===126){if(b.length<4)return;length=b.readUInt16BE(2);offset=4}else if(length===127){if(b.length<10)return;const l=b.readBigUInt64BE(2);if(l>65536n){socket.destroy();return}length=Number(l);offset=10}if(length>65536||op>=8&&(!fin||length>125)){socket.destroy();return}if(b.length<offset+4+length)return;const mask=b.subarray(offset,offset+4);const payload=Buffer.from(b.subarray(offset+4,offset+4+length));for(let i=0;i<length;i++)payload[i]^=mask[i%4];p.buffer=b.subarray(offset+4+length);
        if(op===8){socket.end(frame(8,Buffer.alloc(0)));return}if(op===9){socket.write(frame(10,payload));continue}if(op===10)continue;
        if(op!==0&&op!==1||op===0&&!p.parts.length||op===1&&p.parts.length){socket.destroy();return}
        p.parts.push(payload);p.partSize+=length;if(p.partSize>65536){socket.destroy();return}if(fin){const text=Buffer.concat(p.parts).toString('utf8');p.parts=[];p.partSize=0;message(p,text)}
      }
    }
    socket.on('data',consume);if(head.length)consume(head);
  });

 const timer=setInterval(()=>{for(const p of peers)if(clock()-p.seen>=15000||p.user&&!authenticate(p.authToken))p.socket.destroy();},1000);timer.unref();
 server.on('close',()=>{clearInterval(timer);clearTimeout(queued);for(const p of peers)p.socket.destroy();});
 return {online,publish};
}
