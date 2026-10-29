package com.artii.app

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Person(val id:String,val name:String,val country:String="",val bio:String="",val interests:List<String> = emptyList(),val avatar:String="",val language:String="English",val username:String="",val badgeColor:String="",val online:Boolean=false,val lastSeen:Long=0,val discoverOnline:Boolean=false)
data class Room(val id:String,val name:String,val group:Boolean=false,val members:List<String> = emptyList(),val last:String="Say a little hello")
data class Reaction(val emoji:String,val count:Int,val mine:Boolean)
data class Message(val id:String,val from:String,val name:String,val text:String,val time:Long=System.currentTimeMillis(),val deleted:Boolean=false,val reactions:List<Reaction> = emptyList(),val delivered:List<String> = emptyList(),val read:List<String> = emptyList(),val attachment:JSONObject?=null,val failed:Boolean=false,val clientId:String="")
fun JSONObject.message()=Message(getString("id"),getString("from"),getString("name"),optString("text"),getLong("time"),optBoolean("deleted"),optJSONArray("reactions")?.objects()?.map{Reaction(it.getString("emoji"),it.getInt("count"),it.optBoolean("mine"))}?:emptyList(),optJSONArray("delivered")?.strings()?:emptyList(),optJSONArray("read")?.strings()?:emptyList(),optJSONObject("attachment"),clientId=optString("clientId"))
data class FriendRequest(val id:String,val person:Person)
data class Place(val person:Person,val lat:Double,val lon:Double,val updated:Long,val expires:Long=Long.MAX_VALUE)
data class CallInfo(val id:String,val from:String,val to:String,val status:String)
fun JSONArray.objects()=(0 until length()).map {getJSONObject(it)}
fun JSONArray.strings()=(0 until length()).map {getString(it)}
fun JSONObject.person()=Person(getString("id"),getString("name"),optString("country"),optString("bio"),optJSONArray("interests")?.strings()?:emptyList(),optString("avatar"),optString("language","English"),optString("username"),optString("badgeColor"),optJSONObject("presence")?.optBoolean("online")?:false,optJSONObject("presence")?.optLong("lastSeen")?:0,optBoolean("discoverOnline"))
fun JSONObject.room()=Room(getString("id"),getString("name"),optBoolean("group"),getJSONArray("members").strings(),optString("last"))
fun JSONObject.call()=CallInfo(getString("id"),getString("from"),getString("to"),getString("status"))
fun json(vararg pairs:Pair<String,Any?>)=JSONObject().apply {pairs.forEach {(k,v)->put(k,when(v){is List<*>->JSONArray(v);else->v})}}

class Api(var base:String,var token:String="") {
    suspend fun uploadMedia(path:String,file:java.io.File,mime:String,onProgress:(Float)->Unit):JSONObject=withContext(Dispatchers.IO){
        val url=URL(base.trimEnd('/')+path);require(BuildConfig.DEBUG||url.protocol=="https")
        val connection=url.openConnection() as HttpURLConnection
        try{
            connection.requestMethod="POST";connection.connectTimeout=10000;connection.readTimeout=120000;connection.doOutput=true
            connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("Content-Type","application/octet-stream");connection.setRequestProperty("X-Media-Type",mime);connection.setFixedLengthStreamingMode(file.length())
            connection.outputStream.use{out->file.inputStream().use{input->val chunk=ByteArray(32768);var sent=0L;var last=0L;while(true){val n=input.read(chunk);if(n<0)break;out.write(chunk,0,n);sent+=n;val now=android.os.SystemClock.elapsedRealtime();if(now-last>150){onProgress(sent.toFloat()/file.length());last=now}}}}
            val status=connection.responseCode;val body=(if(status in 200..299)connection.inputStream else connection.errorStream)?.bufferedReader()?.use{it.readText()}?:"{}";val result=JSONObject(body)
            check(status in 200..299){result.optString("error","Upload failed")};onProgress(1f);result
        }finally{connection.disconnect()}
    }
    suspend fun request(path:String,method:String="GET",body:JSONObject?=null):JSONObject=withContext(Dispatchers.IO){
        require(base.isNotBlank()){"Artii's online service has not been set up yet. Use the phone test setup or configure hosting."}
        val url=URL(base.trimEnd('/')+path)
        require(url.protocol in listOf("https","http")){"Use an HTTP or HTTPS server address"}
        if(!BuildConfig.DEBUG) require(url.protocol=="https"){"A secure HTTPS server is required"}
        val conn=url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod=method;conn.connectTimeout=15000;conn.readTimeout=30000
            conn.setRequestProperty("Content-Type","application/json")
            if(token.isNotBlank())conn.setRequestProperty("Authorization","Bearer $token")
            if(body!=null){conn.doOutput=true;conn.outputStream.use{it.write(body.toString().toByteArray())}}
            val status=conn.responseCode
            val text=(if(status in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}?:"{}"
            val result=JSONObject(text)
            if(status !in 200..299)error(result.optString("error","Request failed ($status)"))
            result
        }catch(e:java.net.SocketTimeoutException){throw java.io.IOException("Artii is taking longer to respond. Check your internet connection and try again.",e)}catch(e:java.io.IOException){throw java.io.IOException("Cannot reach Artii right now. Check your internet connection and try again.",e)}finally{conn.disconnect()}
    }
}

class ArtiiModel(context:Context) {
    private val prefs=context.getSharedPreferences("artii",Context.MODE_PRIVATE)
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val tokens=SessionVault(context)
    val defaultServer=BuildConfig.API_URL.ifBlank{if(BuildConfig.DEBUG){if(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.MODEL.contains("sdk"))"http://10.0.2.2:8787" else "http://127.0.0.1:8787"}else ""}
    val api=Api(BuildConfig.API_URL.ifBlank{prefs.getString("server-v2",defaultServer)!!},tokens.read())
    var demo by mutableStateOf(api.token.isBlank())
    var me by mutableStateOf(Person("", ""))
    var people by mutableStateOf(emptyList<Person>())
    var friends by mutableStateOf(emptyList<Person>())
    var requests by mutableStateOf(emptyList<FriendRequest>())
    var sent by mutableStateOf(emptyList<String>())
    var rooms by mutableStateOf(emptyList<Room>())
    var places by mutableStateOf(emptyList<Place>())
    var incoming by mutableStateOf<List<CallInfo>>(emptyList())
    var selectedRoom by mutableStateOf<Room?>(null)
    var messages by mutableStateOf(emptyList<Message>())
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var connected by mutableStateOf(false)
    var foreground by mutableStateOf(true)
    var focused by mutableStateOf(false)
    var chatConnected by mutableStateOf(false)
    var mediaMaxBytes by mutableLongStateOf(250L*1024*1024)
    var uploadProgress by mutableFloatStateOf(-1f)
    private val live=ChatLive(this)
    private val chatEvents=kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    var settings by mutableStateOf(JSONObject(prefs.getString("settings","{}")!!))
    var discovery by mutableStateOf(JSONObject())
    var banner by mutableStateOf<String?>(null)
    var storeOpen by mutableStateOf(false)
    private var notificationsReady=false
    private var seenInvites=emptySet<String>()
    private var oldRequestIds=emptySet<String>()
    private var oldLast=emptyMap<String,String>()
    fun tr(en:String,fr:String)=if(settings.optString("language","en")=="fr")fr else en
    fun preference(key:String,default:Boolean=true)=settings.optBoolean(key,default)
    var lastSync by mutableStateOf(0L)
    var poll:Job?=null
    init { scope.launch{for(event in chatEvents){if(!demo&&foreground)runCatching{syncChat()}.onFailure{chatConnected=false}}};prefs.edit().remove("token").apply();if(!demo){people=emptyList();friends=emptyList();rooms=emptyList();places=emptyList()} }
    fun action(block:suspend()->Unit) {scope.launch {busy=true;try{block()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Something went wrong"}finally{busy=false}}}
    fun setFocus(value:Boolean){focused=value;live.focus(value);if(value)requestChatSync()}
    fun requestChatSync(){chatEvents.trySend(Unit)}
    private suspend fun syncChat(){selectedRoom?.let{loadMessages(it)};refresh();val inbox=api.request("/inbox").getJSONArray("messages").objects();inbox.groupBy{it.getString("room")}.forEach{(room,list)->api.request("/rooms/"+room+"/receipts","POST",json("ids" to list.map{it.getString("id")},"read" to false))};selectedRoom?.let{loadMessages(it)}}
    fun startPolling(){live.start();if(poll?.isActive==true)return;poll=scope.launch{while(isActive){if(!demo)try{refresh();if(!chatConnected)selectedRoom?.let{loadMessages(it)}}catch(e:CancellationException){throw e}catch(e:Exception){connected=false;places=places.filter{System.currentTimeMillis()<it.expires}};delay(3000)}}}
    fun stopPolling(){live.stop();poll?.cancel();poll=null}
    suspend fun refresh(){
        if(demo)return
        val s=api.request("/state");mediaMaxBytes=s.optJSONObject("mediaLimits")?.optLong("maxBytes",250L*1024*1024)?:250L*1024*1024;settings=s.optJSONObject("settings")?:settings;discovery=s.optJSONObject("discovery")?:JSONObject();prefs.edit().putString("settings",settings.toString()).apply();me=s.getJSONObject("me").person();people=s.getJSONArray("discover").objects().map{it.person()};friends=s.getJSONArray("friends").objects().map{it.person()}
        requests=s.getJSONArray("requests").objects().map{FriendRequest(it.getString("id"),it.getJSONObject("person").person())};sent=s.getJSONArray("sent").strings()
        rooms=s.getJSONArray("rooms").objects().map{it.room()};places=s.getJSONArray("locations").objects().map{Place(it.getJSONObject("person").person(),it.getDouble("lat"),it.getDouble("lon"),it.getLong("updated"),it.optLong("expires",Long.MAX_VALUE))}
        incoming=s.getJSONArray("calls").objects().map{it.call()};connected=true;lastSync=System.currentTimeMillis()
        val newInvites=s.optJSONArray("gameInvites")?.objects()?:emptyList()
        if(notificationsReady&&foreground){
            val alert=when{preference("friendRequests")&&requests.any{it.id !in oldRequestIds}->tr("New friend request","Nouvelle demande d'amitié");preference("gameInvites")&&newInvites.any{it.getString("id") !in seenInvites}->tr("Game invitation: ","Invitation de jeu : ")+newInvites.last().getString("code");preference("messages")&&rooms.any{it.id!=selectedRoom?.id&&oldLast.containsKey(it.id)&&oldLast[it.id]!=it.last}->tr("New message","Nouveau message");else->null}
            if(alert!=null)banner=alert
        }
        notificationsReady=true;oldRequestIds=requests.map{it.id}.toSet();oldLast=rooms.associate{it.id to it.last};seenInvites=newInvites.map{it.getString("id")}.toSet()
        if(selectedRoom!=null&&rooms.none{it.id==selectedRoom?.id}){selectedRoom=null;messages=emptyList()}
    }
    fun configureServer(value:String):Api {
        val base=value.trim().trimEnd('/');val url=URL(base)
        require(url.protocol in listOf("https","http")&&url.host.isNotBlank()&&url.userInfo==null&&url.query==null&&url.ref==null&&url.path.isBlank()){"Use a server origin such as https://your-domain.com"}
        require(BuildConfig.DEBUG||url.protocol=="https"){"Use a secure HTTPS connection"}
        return Api(base)
    }
    suspend fun authenticate(server:String,email:String,password:String,username:String,register:Boolean,adult:Boolean){
        val candidate=configureServer(server)
        val r=candidate.request(if(register)"/auth/register" else "/auth/login","POST",json("email" to email.trim(),"password" to password,"username" to username.trim(),"adult" to adult))
        acceptSession(candidate,r)
    }
    fun acceptSession(candidate:Api,r:JSONObject){
        val token=r.getString("token");val person=r.getJSONObject("user").person()
        tokens.save(token);prefs.edit().putString("server-v2",candidate.base).remove("token").apply()
        outbox.clear();retries.clear();messages=emptyList();messageRevision++;live.stop();api.base=candidate.base;api.token=token;demo=false;me=person;live.start()
        people=emptyList();friends=emptyList();rooms=emptyList();places=emptyList();requests=emptyList();sent=emptyList();selectedRoom=null;connected=true
        // Authentication succeeds independently of the first feed request. Polling can retry a slow feed.
        scope.launch{runCatching{refresh()}.onFailure{connected=false}}
    }
    suspend fun findFriendByUsername(username:String):Person = api.request("/people/by-username?username="+java.net.URLEncoder.encode(username.trim(),"UTF-8")).getJSONObject("person").person()
    fun logout(onDone:()->Unit={}){action{if(!demo)api.request("/auth/logout","POST");outbox.clear();retries.clear();messages=emptyList();messageRevision++;live.stop();api.token="";tokens.clear();prefs.edit().remove("token").apply();demo=true;people=emptyList();friends=emptyList();rooms=emptyList();places=emptyList();requests=emptyList();incoming=emptyList();sent=emptyList();selectedRoom=null;me=Person("you","You");discovery=JSONObject();notificationsReady=false;onDone()}}
    fun requestFriend(p:Person)=action{require(!demo){"Sign in first"};run{api.request("/friends/request","POST",json("user" to p.id));refresh();notice="Friend request sent to ${p.name}"}}
    fun respond(request:FriendRequest,accept:Boolean)=action{api.request("/friends/respond","POST",json("request" to request.id,"accept" to accept));refresh()}
    fun openChat(p:Person)=action{
        require(!demo){"Sign in first"};if(friends.none{it.id==p.id}){notice="Send a friend request first. Chat opens after they accept."}
        else {selectedRoom=api.request("/rooms","POST",json("user" to p.id)).getJSONObject("room").room();loadMessages(selectedRoom!!)}
    }
    fun selectRoom(r:Room)=action{selectedRoom=r;loadMessages(r)}
    private var messageRevision=0L
    private suspend fun loadMessages(r:Room){val revision=++messageRevision;require(!demo){"Sign in first"}
        val list=api.request("/rooms/${r.id}/messages").getJSONArray("messages").objects().map{it.message()}
        if(revision!=messageRevision)return
        list.map{it.clientId}.filter{it.isNotBlank()}.forEach{outbox.remove(it);retries.remove(it)}
        if(selectedRoom?.id==r.id)messages=(list.filterNot{hidden().contains(it.id)}+outbox.values.filter{it.first==r.id}.map{it.second}).distinctBy{it.id}
        val ids=list.filter{it.from!=me.id&&((focused&&!it.read.contains(me.id))||!it.delivered.contains(me.id))}.map{it.id}
        if(ids.isNotEmpty())api.request("/rooms/"+r.id+"/receipts","POST",json("ids" to ids,"read" to (focused&&foreground&&selectedRoom?.id==r.id)))

    }
    private val outbox=mutableMapOf<String,Pair<String,Message>>()
    private val retries=mutableMapOf<String,()->Unit>()
    fun retryMessage(id:String){retries[id]?.invoke()}
    private fun enqueue(roomId:String,text:String="",attachment:JSONObject?=null,send:suspend(String)->JSONObject){
        val sessionToken=api.token
        val id="local-"+java.util.UUID.randomUUID()
        val pending=Message(id,me.id,me.name,text,attachment=attachment)
        outbox[id]=roomId to pending
        if(selectedRoom?.id==roomId)messages=messages+pending
        fun attempt(){
            outbox[id]=roomId to pending
            messages=messages.map{if(it.id==id)pending else it}
            retries.remove(id)
            scope.launch{try{
                val result=send(id).getJSONObject("message").message()
                if(api.token!=sessionToken)return@launch
                outbox.remove(id);messageRevision++
                if(selectedRoom?.id==roomId)messages=(messages.filter{it.id!=id&&it.id!=result.id}+result).sortedBy{it.time}
            }catch(e:Exception){
                if(api.token!=sessionToken||!outbox.containsKey(id))return@launch
                val failed=pending.copy(failed=true);outbox[id]=roomId to failed
                messages=messages.map{if(it.id==id)failed else it};retries[id]={attempt()}
                error=e.message?:"Message was not sent. Tap to retry."
            }}
        }
        attempt()
    }
    fun send(text:String,onSent:()->Unit={}){
        val r=selectedRoom?:return;if(text.isBlank()||demo)return
        enqueue(r.id,text){clientId->api.request("/rooms/"+r.id+"/messages","POST",json("text" to text,"clientId" to clientId))};onSent()
    }
    private fun hidden()=prefs.getStringSet("hidden-"+me.id,emptySet())?:emptySet()
    fun deleteForMe(message:Message){val ids=hidden()+message.id;prefs.edit().putStringSet("hidden-"+me.id,ids).apply();messages=messages.filter{it.id!=message.id}}
    fun messageAction(message:Message,operation:String,emoji:String="")=action{
        require(!demo){"Sign in first"};api.request("/messages/"+message.id+"/"+operation,"POST",json("emoji" to emoji));selectedRoom?.let{loadMessages(it)}
    }
    fun sendMediaFile(roomId:String,file:java.io.File,mime:String,duration:Long=0,waveform:List<Float> = emptyList()){
        if(demo||selectedRoom?.id!=roomId){file.delete();return}
        if(file.length()>mediaMaxBytes){file.delete();error="Attachment too large";return}
        val preview=json("id" to java.util.UUID.randomUUID().toString(),"mime" to mime,"localPath" to file.absolutePath,"duration" to duration,"waveform" to waveform)
        enqueue(roomId,attachment=preview){clientId->
            val uploaded=api.uploadMedia("/rooms/"+roomId+"/media",file,mime){}
            val result=api.request("/rooms/"+roomId+"/messages","POST",json("clientId" to clientId,"attachment" to json("uploadId" to uploaded.getString("uploadId"),"duration" to duration,"waveform" to waveform)))
            file.delete();result
        }
    }
    fun sendAttachment(attachment:JSONObject,roomId:String?=selectedRoom?.id,onSent:()->Unit={})=action{
        val room=selectedRoom?:return@action
        require(room.id==roomId){"Conversation changed. Select the attachment again."}
        if(demo){notice="Sign in to send media";return@action}
        api.request("/rooms/"+room.id+"/messages","POST",json("attachment" to attachment));loadMessages(room);onSent()
    }
    fun createGroup(name:String,members:List<String>,onDone:()->Unit)=action{
        if(name.isBlank()||members.isEmpty())error("Add a group name and choose at least one friend")
        require(!demo){"Sign in first"};val r=api.request("/rooms","POST",json("group" to true,"name" to name,"members" to members)).getJSONObject("room").room()
        rooms=rooms+r;selectedRoom=r;loadMessages(r);onDone()
    }
    fun saveProfile(name:String,country:String,bio:String,interests:String,avatar:String)=action{
        val p=me.copy(name=name.trim(),country=country.trim(),bio=bio.trim(),interests=interests.split(',').map{it.trim()}.filter{it.isNotEmpty()}.take(6),avatar=avatar)
        require(p.name.length>=2){"Add a name of at least two characters"}
        require(!demo){"Sign in first"};me=api.request("/profile","PUT",json("name" to p.name,"country" to p.country,"bio" to p.bio,"interests" to p.interests,"avatar" to p.avatar)).getJSONObject("user").person()
        notice="Profile saved"
    }
    fun block(p:Person)=action{require(!demo){"Sign in first"};run{api.request("/block","POST",json("user" to p.id));refresh()};notice="${p.name} has been blocked"}
    fun report(p:Person,reason:String)=action{require(!demo){"Sign in first"};api.request("/report","POST",json("user" to p.id,"reason" to reason));notice= "Report recorded for review by the server operator"}
    suspend fun updateSettings(key:String,value:Any){
        require(!demo){"Sign in first"}
        val old=settings;settings=JSONObject(settings.toString()).put(key,value)
        try{if(!demo)settings=api.request("/settings","PUT",json(key to value)).getJSONObject("settings");prefs.edit().putString("settings",settings.toString()).apply()}catch(e:Exception){settings=old;throw e}
    }
    suspend fun setDiscoverPublic(value:Boolean){
        require(!demo){"Sign in first"}
        val old=settings;settings=JSONObject(settings.toString()).put("strangerChats",value).put("strangerCalls",value)
        try{if(!demo)settings=api.request("/settings","PUT",json("strangerChats" to value,"strangerCalls" to value)).getJSONObject("settings");prefs.edit().putString("settings",settings.toString()).apply();requestChatSync()}catch(e:Exception){settings=old;throw e}
    }
    suspend fun savePersonal(name:String,username:String,bio:String,avatar:String,color:String){
        require(name.trim().length>=2){"Use at least two characters for your display name"};require(bio.length<=150){"Keep your bio within 150 characters"}
        require(!demo){"Sign in first"};me=api.request("/profile","PUT",json("name" to name.trim(),"username" to username.trim(),"bio" to bio,"avatar" to avatar,"badgeColor" to color)).getJSONObject("user").person()
    }
    fun meetingRequest(person:String?,mode:String)=action{
        if(demo){notice="Sign in to meet real people";return@action}
        if(discovery.optJSONObject("balance")?.optInt("total",0)==0){storeOpen=true;return@action}
        api.request(if(person==null)"/discover/random" else "/discover/invite","POST",json("user" to person,"mode" to mode,"consent" to true));refresh()
    }
    fun endMeeting(){val id=discovery.optJSONObject("active")?.optString("id")?:discovery.optJSONArray("outgoing")?.objects()?.firstOrNull()?.optString("id")?:return;selectedRoom=null;scope.launch{runCatching{api.request("/discover/"+id+"/end","POST");refresh()}}}
    fun closeChat(){if(selectedRoom?.id==discovery.optJSONObject("active")?.optString("room"))endMeeting()else selectedRoom=null}
    suspend fun deleteAccount(){api.request("/account","DELETE",json("confirm" to "DELETE"));live.stop();tokens.clear();api.token="";demo=true;people=emptyList();friends=emptyList();rooms=emptyList();places=emptyList();requests=emptyList();incoming=emptyList();discovery=JSONObject();selectedRoom=null;me=Person("you","You");notificationsReady=false}

}
