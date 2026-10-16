package com.artii.app
import androidx.compose.runtime.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Info
import org.json.JSONObject

@Composable fun ChanceCounter(m:ArtiiModel){
 val balance=m.discovery.optJSONObject("balance")
 val total=(balance?.optInt("daily")?:10)+(balance?.optInt("wallet")?:0)
 LaunchedEffect(total,m.demo){if(!m.demo&&balance!=null&&total==0)m.storeOpen=true}
 Surface(onClick={m.storeOpen=true},shape=RoundedCornerShape(24.dp),color=Color(0xFF302347),border=BorderStroke(1.dp,Coral.copy(alpha=.45f))){
  Text("+"+total+" left",Modifier.padding(horizontal=16.dp,vertical=12.dp),color=Coral,fontSize=14.sp)
 }
}
@Composable fun DiscoverActions(m:ArtiiModel){
 var request by remember{mutableStateOf(false)}
 TextButton(onClick={if(!m.preference("strangerChats",false)&&!m.preference("strangerCalls",false)){m.notice="Switch to Public to meet new people";return@TextButton};val b=m.discovery.optJSONObject("balance");if(!m.demo&&b!=null&&b.optInt("daily")+b.optInt("wallet")==0)m.storeOpen=true else request=true},enabled=!m.busy){Text("Meet someone")}
 if(request)MeetingConsent(m,null){request=false}
}
@Composable fun MeetingConsent(m:ArtiiModel,person:Person?,onDismiss:()->Unit){var mode by remember{mutableStateOf("chat")};var consent by remember{mutableStateOf(false)};AlertDialog(onDismissRequest=onDismiss,title={Text(if(person==null)"Meet someone new" else "Meet @"+person.username.ifBlank{person.name})},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("You both choose whether to connect. An accepted meeting uses one chance from each person when either of you ends it. There is no conversation timer.");Row{FilterChip(mode=="chat",{mode="chat"},label={Text("Chat")});Spacer(Modifier.width(8.dp));FilterChip(mode=="video",{mode="video"},label={Text("Video")})};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(consent,{consent=it});Text("I allow this connection and accept the chance rules.",fontSize=12.sp)}}},confirmButton={TextButton(enabled=consent&&!m.busy,onClick={if(m.demo){m.notice="Log in to meet real people";onDismiss()}else m.action{m.meetingRequest(person?.id,mode);onDismiss()}}){Text("Request meeting")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})}
@Composable fun MeetingPrompts(m:ArtiiModel){
 val incoming=m.discovery.optJSONArray("incoming")?.objects()?.firstOrNull();val outgoing=m.discovery.optJSONArray("outgoing")?.objects()?.firstOrNull()
 if(incoming!=null){var consent by remember(incoming.getString("id")){mutableStateOf(false)};AlertDialog(onDismissRequest={},title={Text("@"+incoming.getString("other")+" wants to meet")},text={Column{Text("A "+incoming.getString("mode")+" connection, only if you agree. One chance is charged to each of you when the accepted meeting ends. No time limit.");Row(verticalAlignment=Alignment.CenterVertically){Checkbox(consent,{consent=it});Text("I agree to connect",fontSize=13.sp)}}},confirmButton={TextButton(enabled=consent&&!m.busy,onClick={m.action{m.api.request("/discover/"+incoming.getString("id")+"/accept","POST",json("consent" to true));m.refresh()}}){Text("Accept meeting")}},dismissButton={TextButton(onClick={m.action{m.api.request("/discover/"+incoming.getString("id")+"/decline","POST");m.refresh()}}){Text("Decline")}})}
 else if(outgoing!=null)AlertDialog(onDismissRequest={},title={Text("Waiting for their yes…")},text={Text("@"+outgoing.getString("other")+" can accept or decline. No chance is charged before acceptance.")},confirmButton={TextButton(onClick={m.action{m.api.request("/discover/"+outgoing.getString("id")+"/decline","POST");m.refresh()}}){Text("Cancel request")}})
}

@Composable fun DiscoverAvailability(m:ArtiiModel){
 var info by remember{mutableStateOf(false)}
 var changing by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope()
 val public=m.preference("strangerChats",false)||m.preference("strangerCalls",false)
 val tint by androidx.compose.animation.animateColorAsState(if(public)Color(0xFF11BA70)else Color(0xFFC63C5A),label="Public color")
 val position by androidx.compose.animation.core.animateDpAsState(if(public)86.dp else 4.dp,label="Switch position")
 Row(Modifier.fillMaxWidth().padding(horizontal=22.dp).artiiPanel(22).padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
  Box(Modifier.size(126.dp,44.dp).background(tint,RoundedCornerShape(24.dp)).toggleable(value=public,enabled=!changing,role=androidx.compose.ui.semantics.Role.Switch){value->changing=true;scope.launch{try{m.setDiscoverPublic(value)}catch(e:Exception){m.error=e.message}finally{changing=false}}}){
   Text(if(public)"Public" else "Private",Modifier.align(if(public)Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal=14.dp),color=Color.White,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,fontSize=13.sp)
   Box(Modifier.offset(x=position,y=4.dp).size(36.dp).background(Color.White,androidx.compose.foundation.shape.CircleShape))
  }
  Row(verticalAlignment=Alignment.CenterVertically){AvailabilityLight(public&&m.focused&&m.chatConnected&&m.settings.optString("visibility")!="nobody"&&m.discovery.optJSONObject("active")==null);IconButton(onClick={info=true}){Icon(androidx.compose.material.icons.Icons.Default.Info,"Public and private rules",tint=Muted)}}
 }
 if(info)AlertDialog(containerColor=Color(0xFF141C39),onDismissRequest={info=false},title={Text("Public & Private")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Public · People can request a chat or call while you are online. You still choose whether to accept.")
  Text("Private · New random requests stop immediately. Pending requests close without using a chance. Your current conversation can continue.")
  Text("Green means active and available. The light turns off when private, busy or disconnected. Profile visibility settings still apply.")
  Text("Be respectful. Never pressure anyone to share personal details. Block or report unwanted contact.")
 }},confirmButton={TextButton(onClick={info=false}){Text("Got it")}})
}
@Composable fun AvailabilityLight(online:Boolean){
 val tint=if(online)Color(0xFF42F596)else Color(0xFF687080)
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
  Box(Modifier.size(18.dp),contentAlignment=Alignment.Center){if(online)Box(Modifier.fillMaxSize().background(tint.copy(alpha=.18f),androidx.compose.foundation.shape.CircleShape));Box(Modifier.size(9.dp).background(tint,androidx.compose.foundation.shape.CircleShape))}
  Text(if(online)"online" else "offline",fontSize=12.sp,color=if(online)tint else Muted)
 }
}
