package com.artii.app
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Socket events invalidate the authenticated view; explicit device receipts follow payload processing.
class ChatLive(private val m:ArtiiModel){
 private val client=OkHttpClient.Builder().pingInterval(5,TimeUnit.SECONDS).build()
 private var socket:WebSocket?=null
 private var task:Job?=null
 private var ready=false
 fun start(){if(task?.isActive==true||m.demo)return
  task=m.scope.launch{while(isActive&&m.foreground&&!m.demo){
   if(socket==null){val token=m.api.token;val url=m.api.base.replaceFirst("https://","wss://").replaceFirst("http://","ws://")+"/chat/socket"
    socket=client.newWebSocket(Request.Builder().url(url).build(),object:WebSocketListener(){
     override fun onOpen(ws:WebSocket,response:Response){ws.send(json("type" to "hello","token" to token,"focused" to m.focused).toString())}
     override fun onMessage(ws:WebSocket,text:String){m.scope.launch{if(ws!==socket)return@launch;when(runCatching{JSONObject(text).optString("type")}.getOrDefault("")){
      "ready"->{ready=true;m.requestChatSync()}
      "sync"->m.requestChatSync()
      "error"->{ws.cancel();socket=null;ready=false}
     }}}
     override fun onFailure(ws:WebSocket,t:Throwable,response:Response?){m.scope.launch{if(ws===socket){socket=null;ready=false;m.chatConnected=false}}}
     override fun onClosed(ws:WebSocket,code:Int,reason:String){m.scope.launch{if(ws===socket){socket=null;ready=false;m.chatConnected=false}}}
    })
   }
   if(ready){m.chatConnected=true;socket?.send(json("type" to "ping").toString())};delay(4000)
  }}
 }
 fun focus(value:Boolean){socket?.send(json("type" to "focus","focused" to value).toString())}
 fun stop(){socket?.send(json("type" to "focus","focused" to false).toString());socket?.close(1000,"Background");socket=null;ready=false;task?.cancel();task=null;m.chatConnected=false}
}
