package com.artii.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

val BadgeColors=listOf("#7B6CFF","#EF62B9","#2AA9EE","#18BE96","#F49651","#A367E7")
fun badgeColor(p:Person):Color=runCatching{Color(android.graphics.Color.parseColor(p.badgeColor.ifBlank{BadgeColors[(p.id.hashCode() and Int.MAX_VALUE)%BadgeColors.size]}))}.getOrDefault(Coral)
fun initials(p:Person)=p.username.ifBlank{p.name}.take(2).uppercase()
@Composable fun LetterBadge(p:Person,size:Int=84){Box(Modifier.size(size.dp).background(badgeColor(p),CircleShape).border(2.dp,Color.White.copy(alpha=.6f),CircleShape),contentAlignment=Alignment.Center){Text(initials(p),fontSize=(size/3).sp,fontWeight=FontWeight.Black,color=Color.White)}}

@Composable fun SettingsScreen(m:ArtiiModel,share:LocationShare,onBack:()->Unit,onLogin:()->Unit){
 var section by remember{mutableStateOf("Profile")}
 val context=LocalContext.current;val scope=rememberCoroutineScope();val tr:(String,String)->String={a,b->m.tr(a,b)}
 var name by remember(m.me.id){mutableStateOf(m.me.name)};var username by remember(m.me.id){mutableStateOf(m.me.username)};var bio by remember(m.me.id){mutableStateOf(m.me.bio.take(150))};var avatar by remember(m.me.id){mutableStateOf(m.me.avatar)};var color by remember(m.me.id){mutableStateOf(m.me.badgeColor.ifBlank{BadgeColors[0]})}
 var saving by remember{mutableStateOf(false)};var editingSetting by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var available by remember{mutableStateOf<Boolean?>(null)};var checking by remember{mutableStateOf(false)};var blocked by remember{mutableStateOf<List<Person>?>(null)};var delete by remember{mutableStateOf(false)};var confirmation by remember{mutableStateOf("")}
 val valid=Regex("^[a-zA-Z][a-zA-Z0-9_]{2,23}$").matches(username.trim())
 val dirty=name!=m.me.name||username.trim().lowercase()!=m.me.username||bio!=m.me.bio||avatar!=m.me.avatar||color!=m.me.badgeColor.ifBlank{BadgeColors[0]}
 LaunchedEffect(username){available=null;if(username.trim().equals(m.me.username,true)&&valid){available=true;return@LaunchedEffect};if(valid){checking=true;try{delay(400);available=m.api.request("/auth/username?username="+java.net.URLEncoder.encode(username.trim(),"UTF-8")).getBoolean("available")}catch(e:CancellationException){throw e}catch(e:Exception){available=null}finally{checking=false}}}
 fun update(key:String,value:Any){if(editingSetting)return;editingSetting=true;scope.launch{try{if(key=="ghost"&&value==true)share.stop();m.updateSettings(key,value)}catch(e:Exception){error=e.message}finally{editingSetting=false}}}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)scope.launch{try{avatar=withContext(Dispatchers.IO){val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true};context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,opts)};require(opts.outWidth>0&&opts.outHeight>0);val decode=BitmapFactory.Options().apply{inSampleSize=(maxOf(opts.outWidth,opts.outHeight)/512).coerceAtLeast(1)};val bitmap=context.contentResolver.openInputStream(uri).use{BitmapFactory.decodeStream(it,null,decode)}?:kotlin.error("Invalid photo");val scale=384f/maxOf(bitmap.width,bitmap.height);val small=Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true);val out=ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,80,out);if(small!==bitmap)small.recycle();bitmap.recycle();"data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)}}catch(e:Exception){error="Choose another photo"}}}
 Box(Modifier.fillMaxSize().artiiBackdrop().imePadding()){
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=105.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
   TextButton(onClick=onBack,enabled=!saving){Text(tr("← Back","← Retour"))};Title(when(section){"Privacy"->tr("Privacy & safety","Confidentialité");"Notifications"->tr("Notifications","Notifications");"Preferences"->tr("App preferences","Préférences");else->tr("Your profile","Votre profil")});Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Profile","Privacy","Notifications","Preferences").forEach{label->FilterChip(selected=section==label,onClick={section=label},label={Text(label)})}}
   if(section=="Profile")SettingsCard(tr("Personal profile","Profil personnel")){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)){Box{if(avatar.isBlank())LetterBadge(m.me.copy(name=name,username=username,badgeColor=color),90)else Avatar(m.me.copy(name=name,avatar=avatar),90)};Column{Text("@"+username.ifBlank{"yourname"},color=Coral);TextButton(onClick={picker.launch("image/*")},enabled=!saving){Text(tr("Edit photo","Modifier la photo"))};if(avatar.isNotBlank())TextButton(onClick={avatar=""}){Text(tr("Use initials","Utiliser les initiales"))}}}
    Text(tr("Change badge color","Couleur du badge"),fontSize=12.sp,color=Muted);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)){BadgeColors.forEach{hex->Box(Modifier.size(44.dp).background(Color(android.graphics.Color.parseColor(hex)),CircleShape).border(if(color==hex)3.dp else 0.dp,Color.White,CircleShape).clickable(enabled=!saving){color=hex})}}
    OutlinedTextField(name,{name=it.take(40)},label={Text(tr("Display name","Nom affiché"))},singleLine=true,enabled=!saving,modifier=Modifier.fillMaxWidth())
    OutlinedTextField(username,{username=it.take(24);available=null},prefix={Text("@")},label={Text(tr("Unique username","Nom d'utilisateur unique"))},singleLine=true,enabled=!saving,isError=username.isNotBlank()&&(!valid||available==false),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=if(available==true)Color(0xFF71DEB0)else Coral,unfocusedBorderColor=if(available==true)Color(0xFF71DEB0)else Muted),supportingText={Text(when{!valid->tr("3–24 letters, numbers or underscores","3–24 lettres, chiffres ou traits de soulignement");checking->tr("Checking…","Vérification…");available==false->tr("Username already taken","Nom d'utilisateur déjà pris");available==true->tr("Username available!","Nom d'utilisateur disponible !");else->tr("Connection needed to check","Connexion requise pour vérifier")})},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(bio,{bio=it.take(150)},label={Text(tr("Bio / status","Bio / statut"))},supportingText={Text(bio.length.toString()+" / 150")},minLines=2,maxLines=4,enabled=!saving,modifier=Modifier.fillMaxWidth())
   }
   if(section=="Privacy")SettingsCard(tr("Privacy & safety","Confidentialité et sécurité")){
    SettingToggle(tr("Ghost Mode","Mode fantôme"),tr("Stops and removes your shared location","Arrête et supprime votre position partagée"),m.preference("ghost"),!editingSetting){update("ghost",it)}
    SettingChoice(tr("Profile visibility","Visibilité du profil"),m.settings.optString("visibility","everyone"),listOf("everyone" to tr("Everyone","Tout le monde"),"friends" to tr("Friends Only","Amis uniquement"),"nobody" to tr("Nobody","Personne")),!editingSetting){update("visibility",it)}
    SettingToggle(tr("Chat with new people","Discuter avec de nouvelles personnes"),tr("Every Discover meeting still needs your acceptance","Chaque rencontre demande votre accord"),m.preference("strangerChats",false),!editingSetting){update("strangerChats",it)}
    SettingToggle(tr("Calls from new people","Appels de nouvelles personnes"),tr("Only after you both accept a meeting","Uniquement après votre accord mutuel"),m.preference("strangerCalls",false),!editingSetting){update("strangerCalls",it)}
    TextButton(onClick={scope.launch{try{blocked=if(m.demo)emptyList()else m.api.request("/settings").getJSONArray("blocked").objects().map{it.person()}}catch(e:Exception){error=e.message}}}){Text(tr("Manage blocked users →","Gérer les utilisateurs bloqués →"))}
    blocked?.let{people->if(people.isEmpty())Text(tr("No blocked users","Aucun utilisateur bloqué"),color=Muted);people.forEach{p->Row(verticalAlignment=Alignment.CenterVertically){Text("@"+p.username,Modifier.weight(1f));TextButton(onClick={scope.launch{try{m.api.request("/block","DELETE",json("user" to p.id));blocked=blocked?.filter{it.id!=p.id}}catch(e:Exception){error=e.message}}}){Text(tr("Unblock","Débloquer"))}}}}
   }
   if(section=="Notifications")SettingsCard(tr("Notifications","Notifications")){
    SettingToggle(tr("In-app messages","Messages dans l'application"),tr("Sounds and banners while Artii is open","Sons et bannières quand Artii est ouvert"),m.preference("messages"),!editingSetting){update("messages",it)}
    SettingToggle(tr("Game invites","Invitations aux jeux"),"Ludo · Werewolf · Undercover",m.preference("gameInvites"),!editingSetting){update("gameInvites",it)}
    SettingToggle(tr("Friend requests","Demandes d'amitié"),tr("When someone wants to add you","Quand quelqu'un souhaite vous ajouter"),m.preference("friendRequests"),!editingSetting){update("friendRequests",it)}
   }
   if(section=="Preferences")SettingsCard(tr("App preferences","Préférences de l'application")){
    SettingChoice(tr("Language","Langue"),m.settings.optString("language","en"),listOf("en" to "English","fr" to "Français"),!editingSetting){update("language",it)}
    OutlinedButton(onClick={scope.launch{try{withContext(Dispatchers.IO){context.cacheDir.listFiles()?.forEach{it.deleteRecursively()}};Toast.makeText(context,tr("Cache cleared","Cache vidé"),Toast.LENGTH_SHORT).show()}catch(e:Exception){error=e.message}}}){Text(tr("Clear cache","Vider le cache"))}
   }
   error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
   if(m.demo)OutlinedButton(onClick=onLogin,modifier=Modifier.fillMaxWidth()){Text(tr("Log in","Se connecter"))}else{
    OutlinedButton(onClick={share.stop();m.logout(onLogin)},enabled=!m.busy&&!saving,modifier=Modifier.fillMaxWidth()){Text(tr("Log out","Se déconnecter"))}
    TextButton(onClick={confirmation="";delete=true},modifier=Modifier.fillMaxWidth()){Text(tr("Delete account","Supprimer le compte"),color=Color(0xFFFF6C86))}
   }
   Text("Made by Dp1 Production",Modifier.fillMaxWidth().padding(vertical=18.dp),color=Muted,fontSize=12.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
  }
  if(section=="Profile")Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),color=Cream,shadowElevation=12.dp){Button(onClick={saving=true;error=null;scope.launch{try{m.savePersonal(name,username,bio,avatar,color);Toast.makeText(context,tr("Profile saved","Profil enregistré"),Toast.LENGTH_SHORT).show()}catch(e:Exception){error=e.message}finally{saving=false}}},enabled=dirty&&name.trim().length>=2&&valid&&available==true&&!checking&&!saving,modifier=Modifier.padding(16.dp).fillMaxWidth().heightIn(min=52.dp).artiiAction(dirty&&name.trim().length>=2&&valid&&available==true&&!checking&&!saving),colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,disabledContainerColor=Color.Transparent,contentColor=Color.White),shape=RoundedCornerShape(24.dp)){if(saving){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp))};Text(tr("Save changes","Enregistrer le profil"))}}
 }
 if(delete)AlertDialog(onDismissRequest={if(!saving)delete=false},title={Text(tr("Delete your account?","Supprimer votre compte ?"))},text={Column{Text(tr("This permanently removes your Artii profile and account data. Cancel any Google Play subscription separately in Play Store. Type DELETE to confirm.","Cette action supprime définitivement votre profil et vos données Artii. Annulez séparément votre abonnement dans Google Play. Tapez DELETE pour confirmer."));OutlinedTextField(confirmation,{confirmation=it},singleLine=true,enabled=!saving,label={Text("DELETE")})}},confirmButton={TextButton(enabled=confirmation=="DELETE"&&!saving,onClick={saving=true;scope.launch{try{share.stop();m.deleteAccount();delete=false;onLogin()}catch(e:Exception){error=e.message;delete=false}finally{saving=false}}}){Text(tr("Delete permanently","Supprimer définitivement"),color=Color(0xFFFF6C86))}},dismissButton={TextButton(onClick={delete=false},enabled=!saving){Text(tr("Cancel","Annuler"))}})
}
@Composable private fun SettingsCard(title:String,content:@Composable ColumnScope.()->Unit){Surface(modifier=Modifier.artiiPanel(),shape=RoundedCornerShape(24.dp),color=Color.Transparent){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text(title,color=Ink,fontWeight=FontWeight.Bold,fontSize=20.sp);content()}}}
@Composable private fun SettingToggle(title:String,detail:String,value:Boolean,enabled:Boolean,onChange:(Boolean)->Unit){Row(Modifier.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).padding(end=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,fontSize=15.sp);Text(detail,color=Muted,fontSize=12.sp)};Switch(value,onChange,enabled=enabled)}}
@Composable private fun SettingChoice(title:String,value:String,options:List<Pair<String,String>>,enabled:Boolean,onChange:(String)->Unit){var open by remember{mutableStateOf(false)};Row(verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),fontSize=14.sp);Box{TextButton(onClick={open=true},enabled=enabled){Text(options.find{it.first==value}?.second?:value)};DropdownMenu(open,{open=false}){options.forEach{(key,label)->DropdownMenuItem(text={Text(label)},onClick={onChange(key);open=false})}}}}
}
