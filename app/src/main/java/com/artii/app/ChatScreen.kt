package com.artii.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Base64
import android.view.MotionEvent
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.ComponentActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LiveGreen=Color(0xFF81DDB1)
private val ReactionEmoji=listOf("👍","❤️","😂","😮","😢","🙏")
private fun presenceText(p:Person?):String{
 if(p==null)return ""
 if(p.online)return "online"
 if(p.lastSeen<=0)return ""
 val day=SimpleDateFormat("dd/MM/yyyy",Locale.getDefault())
 val time=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(p.lastSeen))
 return "last seen "+(if(day.format(Date())==day.format(Date(p.lastSeen)))"today" else day.format(Date(p.lastSeen)))+" at "+time
}
private suspend fun mediaFile(m:ArtiiModel,a:JSONObject,context:android.content.Context):File=withContext(Dispatchers.IO){
 val id=a.getString("id");require(Regex("^[a-f0-9-]+$").matches(id))
 val target=File(context.cacheDir,"chat-"+m.me.id+"-"+id)
 // Reauthorize every open; never expose a public media URL or credentials to another player.
 val url=URL(m.api.base.trimEnd('/')+"/media/"+id);require(BuildConfig.DEBUG||url.protocol=="https")
 val connection=url.openConnection() as HttpURLConnection
 try{connection.connectTimeout=5000;connection.readTimeout=15000;connection.setRequestProperty("Authorization","Bearer "+m.api.token)
  check(connection.responseCode==200){"Media is no longer available"}
  connection.inputStream.use{input->target.outputStream().use{output->val buffer=ByteArray(8192);var total=0;while(true){val n=input.read(buffer);if(n<0)break;total+=n;check(total.toLong()<=m.mediaMaxBytes){"Media too large"};output.write(buffer,0,n)}}};target
 }catch(e:Exception){target.delete();throw e}finally{connection.disconnect()}
}
@OptIn(ExperimentalFoundationApi::class,ExperimentalComposeUiApi::class,ExperimentalMaterial3Api::class)
@Composable fun ChatScreen(m:ArtiiModel,onBack:()->Unit,onCall:()->Unit,onMap:()->Unit){
 val room=m.selectedRoom?:return
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 val keyboard=LocalSoftwareKeyboardController.current
 var text by rememberSaveable(room.id){mutableStateOf("")}
 var actionMessage by remember{mutableStateOf<Message?>(null)}
 var deleteConfirm by remember{mutableStateOf<Message?>(null)}
 var attachments by remember{mutableStateOf(false)}
 var emojiTray by remember{mutableStateOf(false)}
 var emojiCategory by remember{mutableStateOf("Faces")}
 var recorder by remember{mutableStateOf<MediaRecorder?>(null)}
 var recordingFile by remember{mutableStateOf<File?>(null)}
 var started by remember{mutableLongStateOf(0)}
 var elapsed by remember{mutableLongStateOf(0)}
 val amplitudes=remember{mutableListOf<Float>()}
 val list=rememberLazyListState()
 val friendId=room.members.firstOrNull{it!=m.me.id}
 val person=(m.friends+m.people).find{it.id==friendId}
 fun finishRecording(send:Boolean){
  val rec=recorder?:return;recorder=null
  val file=recordingFile;recordingFile=null
  val duration=SystemClock.elapsedRealtime()-started
  val stopped=runCatching{rec.stop()}.isSuccess;rec.release()
  if(send&&stopped&&duration>=300&&file!=null){
   val wave=if(amplitudes.isEmpty())emptyList() else List(minOf(64,amplitudes.size)){amplitudes[it*amplitudes.size/minOf(64,amplitudes.size)]}
   m.sendMediaFile(room.id,file,"audio/mp4",duration,wave)
  }else file?.delete()
 }
 fun startRecording(){
  if(recorder!=null||m.busy)return
  if(m.demo){m.notice="Sign in to send voice messages";return}
  runCatching{
   val file=File.createTempFile("voice-",".m4a",context.cacheDir)
   val rec=MediaRecorder()
   try{rec.setAudioSource(MediaRecorder.AudioSource.MIC);rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);rec.setAudioEncodingBitRate(64000);rec.setAudioSamplingRate(44100);rec.setOutputFile(file.absolutePath);rec.prepare();rec.start()}
   catch(e:Exception){rec.release();file.delete();throw e}
   recordingFile=file;amplitudes.clear();started=SystemClock.elapsedRealtime();elapsed=0;recorder=rec
  }.onFailure{m.error="Microphone unavailable. Try again."}
 }
 val micPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)m.notice="Microphone ready. Hold to record." else m.error="Microphone permission is required"}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
  if(uri!=null)scope.launch{
   var copied:File?=null
   try{
    val mime=context.contentResolver.getType(uri)?:""
    require(mime in listOf("image/jpeg","image/png","image/webp","video/mp4")){"Choose JPEG, PNG, WebP or MP4"}
    val target=withContext(Dispatchers.IO){
     val file=File.createTempFile("attachment-",".media",context.cacheDir);copied=file
     context.contentResolver.openInputStream(uri)?.use{input->file.outputStream().use{out->val chunk=ByteArray(32768);var size=0L;while(true){val n=input.read(chunk);if(n<0)break;size+=n;require(size<=m.mediaMaxBytes){"Attachment limit is "+m.mediaMaxBytes/1024/1024+" MB"};out.write(chunk,0,n)}}}?:error("Cannot open file")
     file
    }
    m.sendMediaFile(room.id,target,mime);copied=null
   }catch(e:Exception){copied?.delete();m.error=e.message}
  }
 }
 LaunchedEffect(recorder){while(recorder!=null){elapsed=SystemClock.elapsedRealtime()-started;amplitudes.add(runCatching{recorder!!.maxAmplitude/32767f}.getOrDefault(0f));if(elapsed>=3600000){finishRecording(true);break};delay(50)}}
 DisposableEffect(room.id){val activity=context as ComponentActivity;val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)finishRecording(false)};activity.lifecycle.addObserver(observer);onDispose{activity.lifecycle.removeObserver(observer);finishRecording(false)}}
 LaunchedEffect(m.messages.lastOrNull()?.id){if(m.messages.isNotEmpty())list.animateScrollToItem(0)}
 Column(Modifier.fillMaxSize().artiiBackdrop().imePadding()){
  Row(Modifier.statusBarsPadding().fillMaxWidth().background(Color(0xFF101A34)).padding(horizontal=6.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,"Back")}
   Avatar(person?:Person(room.id,room.name),40)
   Column(Modifier.weight(1f).padding(horizontal=12.dp)){
    Text(if(room.group)room.name else "@"+(person?.username?.ifBlank{person.name}?:room.name),fontWeight=FontWeight.SemiBold,maxLines=1)
    val status=if(room.group)room.members.size.toString()+" members" else if(!m.chatConnected&&!m.demo)"Connecting…" else presenceText(person)
    if(status.isNotBlank())Text(status,fontSize=11.sp,color=if(status=="online")LiveGreen else Muted,maxLines=1)
   }
   if(!room.group)TextButton(onClick=onCall){Text("Call",color=LiveGreen)}
  }
  LazyColumn(state=list,modifier=Modifier.weight(1f).padding(horizontal=12.dp),contentPadding=PaddingValues(vertical=14.dp),reverseLayout=true,verticalArrangement=Arrangement.spacedBy(10.dp)){
   items(m.messages.reversed(),key={it.id}){message->
    val own=message.from==m.me.id
    val peers=room.members.filter{it!=m.me.id}
    val read=peers.isNotEmpty()&&peers.all{it in message.read}
    val delivered=peers.isNotEmpty()&&peers.all{it in message.delivered}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=if(own)Arrangement.End else Arrangement.Start){
     Surface(color=if(own)Color(0xFF19483F)else Color(0xFF25294C),border=BorderStroke(1.dp,Color(0xFF454373)),shape=RoundedCornerShape(20.dp),modifier=Modifier.widthIn(max=290.dp).combinedClickable(onClick={},onLongClick={actionMessage=message}).pointerInteropFilter{event->if(event.action==MotionEvent.ACTION_DOWN&&event.buttonState and MotionEvent.BUTTON_SECONDARY!=0){actionMessage=message;true}else false}){
      Column(Modifier.padding(horizontal=12.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
       if(room.group&&!own)Text(message.name,fontSize=11.sp,color=LiveGreen)
       if(message.deleted)Text("🚫 This message was deleted.",fontStyle=FontStyle.Italic,color=Muted,fontSize=14.sp)
       else{message.attachment?.let{ChatAttachment(m,it)};if(message.text.isNotBlank())Text(message.text,fontSize=15.sp)}
       Row(Modifier.align(Alignment.End),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
        Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date(message.time)),fontSize=10.sp,color=Muted)
        if(own)Text(if(delivered||read)"✓✓" else "✓",fontSize=13.sp,color=if(read)Color(0xFF60BBFF)else Muted,modifier=Modifier.semantics{contentDescription=if(read)"Read" else if(delivered)"Delivered" else "Sent"})
       }
       if(message.reactions.isNotEmpty())Row(Modifier.align(Alignment.End).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){message.reactions.forEach{r->Surface(onClick={m.messageAction(message,"reaction",r.emoji)},shape=CircleShape,color=if(r.mine)Color(0xFF486252)else Color(0xFF17232D)){Text(r.emoji+" "+r.count,Modifier.padding(horizontal=7.dp,vertical=3.dp),fontSize=12.sp)}}}
      }
     }
    }
   }
  }
  if(room.id==m.discovery.optJSONObject("active")?.optString("room")&&friendId!=null&&m.friends.none{it.id==friendId})TextButton(onClick={m.requestFriend(person?:Person(friendId,room.name))},enabled=friendId !in m.sent,modifier=Modifier.align(Alignment.CenterHorizontally)){Text(if(friendId in m.sent)"Request sent" else "Add friend")}
  if(m.uploadProgress>=0f){LinearProgressIndicator(progress=m.uploadProgress,modifier=Modifier.fillMaxWidth(),color=LiveGreen);Text("Uploading "+(m.uploadProgress*100).toInt()+"%",Modifier.padding(horizontal=16.dp),fontSize=11.sp,color=Muted)}
  Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){
   if(recorder!=null){
    IconButton(onClick={finishRecording(false)}){Icon(Icons.Default.Close,"Cancel recording")}
    Text("● %02d:%02d.%03d".format(elapsed/60000,elapsed/1000%60,elapsed%1000),Modifier.weight(1f),color=LiveGreen)
   }else{
    IconButton(onClick={emojiTray=!emojiTray;if(emojiTray)keyboard?.hide()}){OutlineSmiley(Modifier.semantics{contentDescription="Emoji picker"})}
    OutlinedTextField(text,{text=it.take(2000)},placeholder={Text("Message")},modifier=Modifier.weight(1f),shape=RoundedCornerShape(24.dp),maxLines=4,enabled=!m.busy)
    IconButton(onClick={attachments=true},enabled=!m.busy){Icon(Icons.Default.Add,"Attachments")}
   }
   if(text.isNotBlank()&&recorder==null)FilledIconButton(onClick={m.send(text){text=""}},enabled=!m.busy){Icon(Icons.Default.Send,"Send")}
   else Box(Modifier.size(48.dp).background(if(recorder!=null)LiveGreen else Color(0xFF793BED),CircleShape).semantics{contentDescription="Hold to record voice message";onClick{if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)micPermission.launch(Manifest.permission.RECORD_AUDIO)else if(recorder==null)startRecording()else finishRecording(true);true}}.pointerInteropFilter{event->
    when(event.action){MotionEvent.ACTION_DOWN->{if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)micPermission.launch(Manifest.permission.RECORD_AUDIO)else startRecording();true};MotionEvent.ACTION_UP->{finishRecording(true);true};MotionEvent.ACTION_CANCEL->{finishRecording(false);true};else->true}
   },contentAlignment=Alignment.Center){Text("🎙",fontSize=22.sp)}
  }
  if(emojiTray&&recorder==null)EmojiTray{emoji->if(text.length+emoji.length<=2000)text+=emoji}

 }
 if(attachments)ModalBottomSheet(onDismissRequest={attachments=false},containerColor=Color(0xFF202B3B)){
  Column(Modifier.padding(24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(12.dp)){Title("Attach");TextButton(onClick={attachments=false;picker.launch(arrayOf("image/jpeg","image/png","image/webp","video/mp4"))}){Text("Photos / videos")};TextButton(onClick={attachments=false;onMap()}){Text("Share location")};Text("Up to "+m.mediaMaxBytes/1024/1024+" MB per file",color=Muted,fontSize=12.sp)}
 }
 actionMessage?.let{message->ModalBottomSheet(onDismissRequest={actionMessage=null},containerColor=Color(0xFF202B3B)){Column(Modifier.padding(20.dp).navigationBarsPadding()){
  if(!message.deleted)Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.SpaceEvenly){ReactionEmoji.forEach{emoji->TextButton(onClick={m.messageAction(message,"reaction",emoji);actionMessage=null}){Text(emoji,fontSize=24.sp)}}}
  TextButton(onClick={m.deleteForMe(message);actionMessage=null}){Text("Delete for Me")}
  if(message.from==m.me.id&&!message.deleted)TextButton(onClick={deleteConfirm=message;actionMessage=null}){Text("Delete for Everyone",color=Coral)}
 }}}
 deleteConfirm?.let{message->AlertDialog(onDismissRequest={deleteConfirm=null},title={Text("Delete for everyone?")},text={Text("The message will be replaced with a deletion notice.")},confirmButton={TextButton(onClick={m.messageAction(message,"delete");deleteConfirm=null}){Text("Delete")}},dismissButton={TextButton(onClick={deleteConfirm=null}){Text("Cancel")}})}
}

@Composable private fun ChatAttachment(m:ArtiiModel,a:JSONObject){
 val context=LocalContext.current;val scope=rememberCoroutineScope()
 var file by remember(a.getString("id")){mutableStateOf<File?>(null)}
 var image by remember{mutableStateOf<ImageBitmap?>(null)}
 var loading by remember{mutableStateOf(false)}
 var failed by remember{mutableStateOf(false)}
 var video by remember{mutableStateOf(false)}
 var player by remember{mutableStateOf<MediaPlayer?>(null)}
 var playing by remember{mutableStateOf(false)}
 val mime=a.getString("mime")
 suspend fun download(){loading=true;try{file=mediaFile(m,a,context);if(mime.startsWith("image/"))image=withContext(Dispatchers.IO){val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file!!.absolutePath,bounds);val options=BitmapFactory.Options().apply{inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/800).coerceAtLeast(1)};BitmapFactory.decodeFile(file!!.absolutePath,options)?.asImageBitmap()};failed=false}catch(e:Exception){failed=true;m.error=e.message}finally{loading=false}}
 LaunchedEffect(a.getString("id")){if(mime.startsWith("image/"))download()}
 DisposableEffect(a.getString("id")){val activity=context as ComponentActivity;val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){player?.pause();playing=false;video=false}};activity.lifecycle.addObserver(observer);onDispose{activity.lifecycle.removeObserver(observer);player?.release();file?.delete()}}
 if(image!=null)Image(image!!,"Photo",Modifier.fillMaxWidth().heightIn(max=240.dp),contentScale=ContentScale.Fit)
 else Column{
  TextButton(enabled=!loading,onClick={scope.launch{
   if(mime.startsWith("image/")){download();return@launch}
   if(playing){player?.pause();playing=false;return@launch}
   download();if(failed)return@launch
   if(mime.startsWith("video/"))video=true
   else{player?.release();player=MediaPlayer().apply{setDataSource(file!!.absolutePath);setOnPreparedListener{it.start();playing=true};setOnCompletionListener{playing=false};setOnErrorListener{_,_,_->playing=false;failed=true;true};prepareAsync()}}
  }}){Text(if(loading)"Loading…" else if(failed)"Retry media" else if(mime.startsWith("video/"))"▶ Video" else if(mime.startsWith("audio/"))if(playing)"Ⅱ Pause" else "▶ Voice message" else "Load photo",color=LiveGreen)}
  if(mime.startsWith("audio/")){
   val values=a.optJSONArray("waveform")?.let{array->(0 until array.length()).map{array.optDouble(it).toFloat()}}?:emptyList()
   Canvas(Modifier.fillMaxWidth().height(28.dp)){if(values.isNotEmpty())values.forEachIndexed{i,value->val x=(i+.5f)*size.width/values.size;val h=(value.coerceIn(0f,1f)*size.height).coerceAtLeast(3.dp.toPx());drawLine(LiveGreen,Offset(x,(size.height-h)/2),Offset(x,(size.height+h)/2),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)}}
   Text("%d:%02d".format(a.optLong("duration")/60000,a.optLong("duration")/1000%60),fontSize=11.sp,color=Muted)
  }
 }
 if(video&&file!=null)Dialog(onDismissRequest={video=false}){Surface{Column{AndroidView(factory={ctx->VideoView(ctx).apply{setVideoPath(file!!.absolutePath);setMediaController(MediaController(ctx).also{it.setAnchorView(this)});setOnPreparedListener{start()}}},onRelease={it.stopPlayback()},modifier=Modifier.fillMaxWidth().height(300.dp));TextButton(onClick={video=false}){Text("Close")}}}}
}
