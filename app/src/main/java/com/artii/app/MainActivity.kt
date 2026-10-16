package com.artii.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.webkit.*
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayOutputStream
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay

val Coral=Color(0xFFF28EC7)
val Ink=Color(0xFFF4EFFF)
val Cream=Color(0xFF0B132B)
val Muted=Color(0xFFB7B4EA)
private val Sage=Color(0xFF32264D)
private val Lilac=Color(0xFF202D4D)

class MainActivity:ComponentActivity(){
    lateinit var model:ArtiiModel
    lateinit var sharing:LocationShare
    var engine:CallEngine?=null
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT));model=ArtiiModel(this);sharing=LocationShare(this,model);setContent{MaterialTheme(colorScheme=darkColorScheme(primary=Coral,onPrimary=Color(0xFF2A1138),background=Cream,surface=Cream,onSurface=Ink,secondary=Color(0xFFB79CFF),surfaceVariant=Color(0xFF262B47))){ArtiiApp(this,model,sharing)}}}
    override fun onWindowFocusChanged(hasFocus:Boolean){super.onWindowFocusChanged(hasFocus);if(::model.isInitialized)model.setFocus(hasFocus)}
    override fun onStart(){super.onStart();if(::model.isInitialized){model.foreground=true;model.startPolling()}}
    override fun onStop(){if(::model.isInitialized){model.foreground=false;model.stopPolling();sharing.stop();engine?.close();engine=null};super.onStop()}
    override fun onDestroy(){if(::model.isInitialized){sharing.stop();engine?.close()};super.onDestroy()}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ArtiiApp(activity:MainActivity,m:ArtiiModel,share:LocationShare){
    var tab by rememberSaveable{mutableStateOf("Discover")}
    var profile by remember{mutableStateOf<Person?>(null)}
    var auth by remember{mutableStateOf(m.demo)}
    LaunchedEffect(m.demo){if(m.demo)auth=true}
    var loginOnly by remember{mutableStateOf(false)}
    var meetPerson by remember{mutableStateOf<Person?>(null)}
    var edit by remember{mutableStateOf(false)}
    var newGroup by remember{mutableStateOf(false)}
    var shareSheet by remember{mutableStateOf(false)}
    var selectedFriends by remember{mutableStateOf(emptyList<String>())}
    var duration by remember{mutableIntStateOf(60)}
    var call by remember{mutableStateOf<CallInfo?>(null)}
    var pendingCall by remember{mutableStateOf<Pair<String,Boolean>?>(null)}
    val cameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->
        val pending=pendingCall;pendingCall=null
        if(grants.values.all{it}&&pending!=null)m.action{if(pending.second){m.api.request("/calls/${pending.first}/accept","POST");call=m.api.request("/calls/${pending.first}").getJSONObject("call").call()}else call=m.api.request("/calls","POST",json("user" to pending.first)).getJSONObject("call").call()}
        else m.error="Camera and microphone access are needed for a video call."
    }
    val meetingCameraPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->
        val id=m.discovery.optJSONObject("active")?.optString("call")
        if(grants.values.all{it}&&!id.isNullOrBlank())m.action{call=m.api.request("/calls/"+id).getJSONObject("call").call()}else{m.endMeeting();m.error="Camera and microphone access are needed. The accepted meeting has ended."}
    }
    val activeMeeting=m.discovery.optJSONObject("active")
    LaunchedEffect(activeMeeting?.optString("id")){
        if(activeMeeting!=null){if(activeMeeting.optString("mode")=="chat"){m.selectedRoom=m.rooms.find{it.id==activeMeeting.optString("room")};if(m.selectedRoom!=null)runCatching{m.api.request("/discover/"+activeMeeting.getString("id")+"/ready","POST")}.onFailure{m.error=it.message;m.endMeeting()}}else meetingCameraPermission.launch(arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO))}
    }
    val locationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){grants->if(grants.values.any{it})share.start(selectedFriends,duration)else m.error="Allow location access to share your position."}
    fun beginCall(id:String,incoming:Boolean=false){if(m.demo){m.notice="Connect an account to call a real friend.";return};pendingCall=id to incoming;cameraPermission.launch(arrayOf(Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO))}
    BackHandler(enabled=profile!=null||m.selectedRoom!=null||auth||edit||newGroup||call!=null){when{call!=null->{activity.engine?.close();call=null};profile!=null->profile=null;auth->auth=false;edit->edit=false;newGroup->newGroup=false;else->m.closeChat()}}
    if(call!=null){val c=call!!;val engine=remember(c.id){CallEngine(activity,m,c).also{activity.engine=it}};DisposableEffect(c.id){onDispose{engine.close();activity.engine=null}};LaunchedEffect(engine.finished){if(engine.finished)call=null};CallScreen(engine){call=null};return}
    Scaffold(containerColor=Cream,
        bottomBar={if(!auth&&!edit&&!newGroup&&m.selectedRoom==null)NavigationBar(containerColor=Color(0xFF101932),tonalElevation=0.dp){
            listOf("Profile","Discover","Chats","Games","Meet").forEachIndexed{i,label->
                NavigationBarItem(selected=tab==label,onClick={tab=label},
                    icon={Icon(listOf(Icons.Default.Person,Icons.Default.Search,Icons.Default.Email,Icons.Default.Star,Icons.Default.LocationOn)[i],m.tr(label,when(label){"Profile"->"Profil";"Discover"->"Découvrir";"Chats"->"Discussions";"Games"->"Jeux";else->"Carte"}))},
                    alwaysShowLabel=false,
                    colors=NavigationBarItemDefaults.colors(indicatorColor=Color(0xFF50316F),selectedIconColor=Coral,selectedTextColor=Coral))
            }
        }},
        topBar={if(m.selectedRoom==null&&tab!="Meet"&&!auth&&!newGroup)Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Image(painterResource(R.drawable.artii_logo),null,Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Fit);Text("Artii",fontSize=26.sp,fontWeight=FontWeight.Bold)}
            Row(verticalAlignment=Alignment.CenterVertically){if(m.demo)TextButton(onClick={auth=true}){Text("Log in",fontSize=12.sp)};if(tab=="Discover")ChanceCounter(m)}
        }}
    ){padding->
        Box(Modifier.padding(padding).fillMaxSize().artiiBackdrop()){
            when{
                auth->AuthScreen(m,startWithLogin=loginOnly){auth=false}
                edit->SettingsScreen(m,share,onBack={edit=false},onLogin={loginOnly=true;auth=true;edit=false})
                newGroup->GroupEditor(m){newGroup=false}
                m.selectedRoom!=null->ChatScreen(m,onBack={m.closeChat()},onCall={m.selectedRoom?.members?.firstOrNull{it!=m.me.id}?.let{beginCall(it)}},onMap={m.closeChat();tab="Meet"})
                tab=="Discover"->DiscoverScreen(m,profile==null){profile=it}
                tab=="Profile"->SettingsScreen(m,share,onBack={tab="Discover"},onLogin={loginOnly=true;auth=true;tab="Discover"})
                tab=="Chats"->ChatsScreen(m,onNew={newGroup=true})
                tab=="Games"->GamesScreen(m){auth=true}
                tab=="Meet"->Box(Modifier.fillMaxSize()){
                    FriendMap(m.places.filter{m.demo||System.currentTimeMillis()<it.expires},false,share,Modifier.fillMaxSize())
                    FilledTonalButton(colors=ButtonDefaults.filledTonalButtonColors(containerColor=Color(0xFF793BED),contentColor=Color.White),onClick={if(share.active)share.stop()else shareSheet=true},modifier=Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)){
                        Text(if(share.active)"Stop sharing" else "Share location",fontSize=12.sp)
                    }
                }
            }
            m.banner?.let{Surface(Modifier.align(Alignment.BottomCenter).padding(20.dp),color=Color(0xFF393057),shape=RoundedCornerShape(18.dp)){Text(it,Modifier.padding(16.dp),color=Ink)}}
            if(m.busy)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter),color=Coral)
        }
    }
    profile?.let{p->ModalBottomSheet(onDismissRequest={profile=null},containerColor=Cream){Column(Modifier.padding(24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(14.dp)){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Avatar(p,70);Column{Title(p.name);if(p.username.isNotBlank())Text("@${p.username}",color=Coral,fontSize=13.sp);Text(p.country+" · "+p.language,color=Muted,fontSize=13.sp)}};Text(p.bio);Text(p.interests.joinToString("  ·  "),color=Coral,fontSize=13.sp);Primary("Chat with ${p.name}"){if(m.friends.any{it.id==p.id})m.openChat(p)else meetPerson=p;profile=null};OutlinedButton(onClick={m.requestFriend(p)},enabled=p.id !in m.sent&&m.friends.none{it.id==p.id},modifier=Modifier.fillMaxWidth()){Text(when{m.friends.any{it.id==p.id}->"Already friends";p.id in m.sent->"Request pending";else->"Send friend request"})};Row{TextButton(onClick={m.block(p);profile=null}){Text("Block")};TextButton(onClick={m.report(p,"Profile reported from Artii");profile=null}){Text("Report profile")}}}}
    }
    if(shareSheet)ModalBottomSheet(onDismissRequest={shareSheet=false},containerColor=Cream){Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){Title("Share with your people");Text("Updates stop when you leave Artii. A last location may remain visible for up to 60 seconds.",fontSize=13.sp,color=Muted);if(m.friends.isEmpty())Text("Add and accept a friend first.");m.friends.forEach{p->Row(verticalAlignment=Alignment.CenterVertically){Avatar(p,38);Text(p.name,Modifier.weight(1f).padding(10.dp));Checkbox(p.id in selectedFriends,{checked->selectedFriends=if(checked)selectedFriends+p.id else selectedFriends-p.id})}};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(15,60,240).forEach{n->FilterChip(selected=duration==n,onClick={duration=n},label={Text(if(n==15)"15 min" else "${n/60} hour${if(n>60)"s" else ""}")})}};Primary("Start sharing",selectedFriends.isNotEmpty()){shareSheet=false;if(m.demo)share.start(selectedFriends,duration)else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))};Spacer(Modifier.height(20.dp))}}
    meetPerson?.let{MeetingConsent(m,it){meetPerson=null}}
    if(m.storeOpen)StoreScreen(m){m.storeOpen=false}
    if(!auth&&!edit)MeetingPrompts(m)
    if(m.banner!=null){val message=m.banner;LaunchedEffect(message){runCatching{android.media.RingtoneManager.getRingtone(activity,android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)).play()};delay(3500);m.banner=null}}
    if(m.error!=null)AlertDialog(onDismissRequest={m.error=null},title={Text("Something needs attention")},text={Text(m.error!!)},confirmButton={TextButton(onClick={m.error=null}){Text("OK")}})
    if(m.notice!=null)AlertDialog(onDismissRequest={m.notice=null},title={Text("Artii")},text={Text(m.notice!!)},confirmButton={TextButton(onClick={m.notice=null}){Text("Got it")}})
    if(!auth&&m.incoming.isNotEmpty()&&call==null){val incoming=m.incoming.first();AlertDialog(onDismissRequest={},title={Text("Incoming video call")},text={Text(m.friends.find{it.id==incoming.from}?.name?:"Your friend")},confirmButton={TextButton(onClick={beginCall(incoming.id,true)}){Text("Answer")}},dismissButton={TextButton(onClick={m.action{m.api.request("/calls/${incoming.id}/end","POST");m.refresh()}}){Text("Decline")}})}
}

@Composable fun Eyebrow(text:String){Text(text,fontSize=10.sp,letterSpacing=1.6.sp,color=Muted,fontWeight=FontWeight.SemiBold)}
@Composable fun Title(text:String){Text(text,fontSize=30.sp,lineHeight=35.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.7).sp)}
@Composable fun Primary(text:String,enabled:Boolean=true,onClick:()->Unit){Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).artiiAction(enabled),colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,disabledContainerColor=Color.Transparent,contentColor=Color.White),shape=RoundedCornerShape(24.dp)){Text(text,fontWeight=FontWeight.SemiBold)}}
@Composable fun Avatar(p:Person,size:Int=56){val bmp=remember(p.avatar){runCatching{val bytes=Base64.decode(p.avatar.substringAfter(','),Base64.DEFAULT);BitmapFactory.decodeByteArray(bytes,0,bytes.size)?.asImageBitmap()}.getOrNull()};Box(Modifier.size(size.dp).border(2.dp,ArtiiGradient,CircleShape).padding(4.dp).clip(CircleShape).background(if(p.id.hashCode()%2==0)Sage else Lilac),contentAlignment=Alignment.Center){if(bmp!=null)Image(bmp,p.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Text(p.name.take(1).uppercase(),fontSize=(size/2.6).sp,fontWeight=FontWeight.Medium,color=Ink)}}

@OptIn(ExperimentalFoundationApi::class)
@Composable fun DiscoverScreen(m:ArtiiModel,autoAdvance:Boolean=true,onPerson:(Person)->Unit){
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Row(Modifier.fillMaxWidth().padding(horizontal=24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Title("Discover");DiscoverActions(m)}
        DiscoverAvailability(m)
        if(m.people.isEmpty()){Box(Modifier.weight(1f).padding(24.dp),contentAlignment=Alignment.Center){Text(if(m.demo)"Connect to meet new people." else "Your community starts here. Invite a friend to create a profile.",color=Muted,textAlign=TextAlign.Center)}}else{
            val pager=rememberPagerState(initialPage=10000-(10000%m.people.size),pageCount={20000})
            val dragging by pager.interactionSource.collectIsDraggedAsState()
            LaunchedEffect(pager.settledPage,dragging,autoAdvance,m.storeOpen,m.discovery.optJSONObject("active")?.optString("id"),m.foreground,m.people.size){
                if(autoAdvance&&!m.storeOpen&&m.discovery.optJSONObject("active")==null&&m.foreground&&!dragging&&m.people.size>1){delay(3000);if(!pager.isScrollInProgress){if(pager.currentPage>=19998)pager.scrollToPage(10000-(10000%m.people.size)+(pager.currentPage%m.people.size));pager.animateScrollToPage(pager.currentPage+1,animationSpec=androidx.compose.animation.core.tween(650))}}
            }
            HorizontalPager(state=pager,contentPadding=PaddingValues(horizontal=22.dp),pageSpacing=14.dp,modifier=Modifier.weight(1f)){page->
                val p=m.people[page%m.people.size];val offset=((pager.currentPage-page)+pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f,1f)
                Card(onClick={onPerson(p)},modifier=Modifier.fillMaxSize().graphicsLayer{scaleY=1f-.055f*offset;alpha=1f-.25f*offset;rotationZ=-2f*((pager.currentPage-page)+pager.currentPageOffsetFraction).coerceIn(-1f,1f)},shape=RoundedCornerShape(32.dp),border=BorderStroke(1.dp,ArtiiOutline),colors=CardDefaults.cardColors(containerColor=Color.Transparent)){
                    Column(Modifier.fillMaxSize().background(ArtiiPanel).padding(22.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween){Text("${p.country.ifBlank{"Somewhere in the world"}}  ·  ${p.language}",fontSize=12.sp,color=Ink);Box(Modifier.fillMaxWidth().padding(vertical=18.dp),contentAlignment=Alignment.Center){Avatar(p,156)};Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)){Text(p.name,fontSize=32.sp,fontWeight=FontWeight.Bold);AvailabilityLight(p.discoverOnline);Text(p.bio,textAlign=TextAlign.Center,color=Ink,fontSize=14.sp,maxLines=3);Text(p.interests.take(3).joinToString("  •  "),fontSize=12.sp,color=Muted);Surface(modifier=Modifier.artiiAction(),shape=RoundedCornerShape(24.dp),color=Color.Transparent){Text("Tap to connect  ↗",Modifier.padding(horizontal=26.dp,vertical=14.dp),fontSize=12.sp,fontWeight=FontWeight.SemiBold)}}}
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center){repeat(m.people.size.coerceAtMost(12)){i->Box(Modifier.padding(3.dp).size(if(pager.currentPage%m.people.size==i)8.dp else 5.dp).clip(CircleShape).background(if(pager.currentPage%m.people.size==i)Coral else Color(0xFF4C4B69)))}}
        }
        Text(if(m.demo)"Sign in to discover people" else if(m.connected)"" else "Reconnecting…",Modifier.fillMaxWidth().padding(bottom=16.dp),color=Muted,fontSize=11.sp,textAlign=TextAlign.Center)
    }
}

@Composable fun ChatsScreen(m:ArtiiModel,onNew:()->Unit){
    var query by rememberSaveable{mutableStateOf("")}
    var filter by rememberSaveable{mutableStateOf("All")}
    var finder by remember{mutableStateOf(false)}
    val rooms=m.rooms.filter{(filter!="Groups"||it.group)&&(it.name.contains(query,true)||it.last.contains(query,true))}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){Title("Chats")};IconButton(onClick={finder=!finder}){Icon(Icons.Default.Person,"Find a friend")};IconButton(onClick=onNew){Icon(Icons.Default.Add,"New group")}}}
        item{OutlinedTextField(query,{query=it},placeholder={Text("Search chats")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(24.dp),modifier=Modifier.fillMaxWidth())}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("All","Groups").forEach{label->FilterChip(selected=filter==label,onClick={filter=label},label={Text(label)})}}}
        if(finder)item{FriendFinder(m)}
        items(m.requests,key={it.id}){r->Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF352645))){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Avatar(r.person,40);Text(r.person.name,Modifier.weight(1f).padding(10.dp));TextButton(onClick={m.respond(r,true)}){Text("Accept")};IconButton(onClick={m.respond(r,false)}){Icon(Icons.Default.Close,"Decline")}}}}
        items(rooms,key={it.id}){r->
            Row(Modifier.fillMaxWidth().artiiPanel(20).clickable{m.selectRoom(r)}.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                Avatar(if(r.group)Person(r.id,r.name)else m.friends.find{it.id in r.members}?:Person(r.id,r.name),56)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(r.name,fontWeight=FontWeight.SemiBold,maxLines=1);Text(r.last.ifBlank{if(r.group)"Group chat" else "Say hello"},fontSize=13.sp,color=Muted,maxLines=1)}
                if(r.group)Icon(Icons.Default.Person,"Group",tint=Muted,modifier=Modifier.size(18.dp))
            }
        }
        if(rooms.isEmpty())item{Box(Modifier.fillMaxWidth().padding(vertical=40.dp),contentAlignment=Alignment.Center){Text(if(query.isNotBlank())"No chats found" else "Your conversations appear here",color=Muted)}}
    }
}

@Composable fun ProfileEditor(m:ArtiiModel,onBack:()->Unit,connect:()->Unit){var name by remember{mutableStateOf(m.me.name)};var country by remember{mutableStateOf(m.me.country)};var bio by remember{mutableStateOf(m.me.bio)};var interests by remember{mutableStateOf(m.me.interests.joinToString(", "))};var avatar by remember{mutableStateOf(m.me.avatar)};val context=LocalContext.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        if(uri!=null)runCatching{
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
            context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,bounds)}
            require(bounds.outWidth>0&&bounds.outHeight>0){"Invalid photo"}
            val opts=BitmapFactory.Options().apply{inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/512).coerceAtLeast(1)}
            val image=context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,opts)}?:error("Cannot read photo")
            val ratio=384f/maxOf(image.width,image.height)
            val scaled=Bitmap.createScaledBitmap(image,(image.width*ratio).toInt().coerceAtLeast(1),(image.height*ratio).toInt().coerceAtLeast(1),true)
            val output=ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,80,output)
            avatar="data:image/jpeg;base64,"+Base64.encodeToString(output.toByteArray(),Base64.NO_WRAP)
            if(scaled!==image)scaled.recycle();image.recycle()
        }.onFailure{m.error="Choose a smaller image or another photo."}
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){TextButton(onClick=onBack){Text("← Back")};Title("Your corner of Artii.");Row(verticalAlignment=Alignment.CenterVertically){Avatar(m.me.copy(avatar=avatar),80);TextButton(onClick={picker.launch("image/*")}){Text("Choose profile picture")}};OutlinedTextField(name,{name=it.take(40)},label={Text("Name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(country,{country=it.take(60)},label={Text("Country")},modifier=Modifier.fillMaxWidth());OutlinedTextField(bio,{bio=it.take(200)},label={Text("A little about you")},modifier=Modifier.fillMaxWidth());OutlinedTextField(interests,{interests=it},label={Text("Interests, separated by commas")},modifier=Modifier.fillMaxWidth());Primary("Save profile"){m.saveProfile(name,country,bio,interests,avatar)};if(m.demo)OutlinedButton(onClick=connect,modifier=Modifier.fillMaxWidth()){Text("Connect an account")}else TextButton(onClick={m.logout();onBack()}){Text("Sign out")}}
}

@Composable fun GroupEditor(m:ArtiiModel,onDone:()->Unit){var name by rememberSaveable{mutableStateOf("")};var members by remember{mutableStateOf(emptyList<String>())};Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){TextButton(onClick=onDone){Text("← Back")};Title("New group");OutlinedTextField(name,{name=it.take(50)},label={Text("Group name")},modifier=Modifier.fillMaxWidth());m.friends.forEach{p->Row(verticalAlignment=Alignment.CenterVertically){Avatar(p,42);Text(p.name,Modifier.weight(1f).padding(12.dp));Checkbox(p.id in members,{checked->members=if(checked)members+p.id else members-p.id})}};Primary("Create group",name.isNotBlank()&&members.isNotEmpty()){m.createGroup(name,members,onDone)}}}

@Composable fun CallScreen(engine:CallEngine,onEnd:()->Unit){Column(Modifier.fillMaxSize().background(Color(0xFF0B1024)).systemBarsPadding().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(engine.status,color=Color.White,modifier=Modifier.padding(12.dp));Box(Modifier.weight(1f).clip(RoundedCornerShape(24.dp))){AndroidView(factory={engine.remote},modifier=Modifier.fillMaxSize());AndroidView(factory={engine.local},modifier=Modifier.align(Alignment.TopEnd).padding(12.dp).size(100.dp,140.dp))};Row(Modifier.padding(vertical=20.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton(onClick={engine.toggleMic()}){Text(if(engine.muted)"Unmute" else "Mute")};OutlinedButton(onClick={engine.toggleCamera()}){Text(if(engine.cameraOff)"Camera on" else "Camera off")}};TextButton(onClick={engine.switchCamera()}){Text("Switch camera")};Primary("End call"){engine.close();onEnd()}}}

@Composable fun GamesScreen(m:ArtiiModel,onConnect:()->Unit){
    var inviteCode by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val web=remember(m.demo,m.api.token){WebView(context).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.mixedContentMode=if(BuildConfig.DEBUG)WebSettings.MIXED_CONTENT_ALWAYS_ALLOW else WebSettings.MIXED_CONTENT_NEVER_ALLOW;setBackgroundColor(android.graphics.Color.rgb(16,22,44));webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?)=true};addJavascriptInterface(object{
        @JavascriptInterface fun session()=json("server" to m.api.base,"token" to m.api.token,"name" to m.me.name,"demo" to m.demo,"lastRoom" to context.getSharedPreferences("game-rooms",android.content.Context.MODE_PRIVATE).getString(m.api.token.hashCode().toString(),"")).toString()
        @JavascriptInterface fun saveRoom(code:String){context.getSharedPreferences("game-rooms",android.content.Context.MODE_PRIVATE).edit().putString(m.api.token.hashCode().toString(),code.take(4)).apply()}
        @JavascriptInterface fun inviteFriends(code:String){if(Regex("^[A-Z]{4}$").matches(code))(context as? android.app.Activity)?.runOnUiThread{inviteCode=code}}
        @JavascriptInterface fun connect(){(context as? android.app.Activity)?.runOnUiThread{onConnect()}}
    },"ArtiiNative");loadUrl("file:///android_asset/games/index.html")}}
    DisposableEffect(web){onDispose{web.destroy()}}
    AndroidView(factory={web},modifier=Modifier.fillMaxSize())
    if(inviteCode!=null)AlertDialog(onDismissRequest={inviteCode=null},title={Text("Invite friends")},text={Column{if(m.friends.isEmpty())Text("Add friends before inviting them to a game.");m.friends.forEach{p->TextButton(onClick={val code=inviteCode;m.action{m.api.request("/games/invite","POST",json("user" to p.id,"code" to code));m.notice="Game invitation sent"};inviteCode=null}){Text("@"+p.username.ifBlank{p.name})}}}},confirmButton={TextButton(onClick={inviteCode=null}){Text("Done")}})
}
