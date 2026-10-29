package com.artii.app

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

private val Good=Color(0xFF71DEB0)
private val Danger=Color(0xFFFF879F)
@Composable fun AuthScreen(m:ArtiiModel,startWithLogin:Boolean=false,onDone:()->Unit){
 var register by rememberSaveable{mutableStateOf(!startWithLogin)}
 var email by rememberSaveable{mutableStateOf("")}
 var password by remember{mutableStateOf("")}
 var username by rememberSaveable{mutableStateOf("")}
 var visible by remember{mutableStateOf(false)}
 var adult by rememberSaveable{mutableStateOf(false)}
 var server by rememberSaveable{mutableStateOf(m.api.base)}
 var settings by remember{mutableStateOf(false)}
 var active by remember{mutableStateOf<String?>(null)}
 var error by remember{mutableStateOf<String?>(null)}
 var connection by remember{mutableStateOf("Checking connection…")}
 var available by remember{mutableStateOf<Boolean?>(null)}
 var checking by remember{mutableStateOf(false)}
 var usernameError by remember{mutableStateOf<String?>(null)}
 var providers by remember{mutableStateOf(emptySet<String>())}
 var retry by remember{mutableIntStateOf(0)}
 var ready by remember{mutableStateOf(false)}
 val context=LocalContext.current
 val pendingVault=remember{SessionVault(context,"artii-pending-login")}
 var pending by remember{mutableStateOf(runCatching{org.json.JSONObject(pendingVault.read()).takeIf{it.getLong("expires")>System.currentTimeMillis()&&it.getString("server")==server}}.getOrNull())}
 var flow by remember{mutableStateOf<String?>(null)}
 var proof by remember{mutableStateOf(pending?.optString("proof")?:"")}
 var socialStatus by remember{mutableStateOf("Finish signing in in your browser, then return to Artii.")}
 var operation by remember{mutableStateOf<Job?>(null)}
 val scope=rememberCoroutineScope()
 val waiting=pending!=null&&flow==null
 val locked=active!=null||waiting
 val emailValid=android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
 val usernameValid=Regex("^[a-zA-Z][a-zA-Z0-9_]{2,23}$").matches(username.trim())
 val needUsername=!waiting&&(register||flow!=null)
 val colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Color(0xFFB79CFF),cursorColor=Coral,focusedLabelColor=Coral,unfocusedBorderColor=Color(0xFF4C4F69),errorBorderColor=Danger,errorSupportingTextColor=Danger)
 LaunchedEffect(server,retry){
  ready=false;providers=emptySet();connection="Checking connection…";delay(350)
  try{val config=m.configureServer(server).request("/auth/config");providers=listOf("google").filter{config.getJSONObject("providers").optBoolean(it)}.toSet();ready=true;connection="Connected to Artii"}
  catch(e:CancellationException){throw e}catch(e:Exception){connection=e.message?:"Unable to connect"}
 }
 LaunchedEffect(username,needUsername,server,ready,retry){
  available=null;usernameError=null;checking=false
  if(needUsername&&usernameValid&&ready){checking=true;try{delay(400);val result=m.configureServer(server).request("/auth/username?username="+java.net.URLEncoder.encode(username.trim(),"UTF-8"));available=result.getBoolean("available");if(!result.optBoolean("valid"))usernameError="Choose another username"}catch(e:CancellationException){throw e}catch(e:Exception){usernameError="Couldn't check this username. Retry your connection."}finally{checking=false}}
 }
 LaunchedEffect(pending,flow){
  val saved=pending?:return@LaunchedEffect
  if(flow!=null)return@LaunchedEffect
  while(true){
   if(System.currentTimeMillis()>=saved.getLong("expires")){pendingVault.clear();pending=null;error="Sign-in expired. Please try again.";break}
   if(!m.foreground){delay(500);continue}
   try{
    val api=m.configureServer(saved.getString("server"))
    val result=api.request("/auth/social/result","POST",json("flowId" to saved.getString("flowId"),"proof" to saved.getString("proof")))
    when(result.getString("status")){
     "username_required"->{proof=saved.getString("proof");flow=saved.getString("flowId");error=null;break}
     "authenticated"->{m.acceptSession(api,result);pendingVault.clear();pending=null;password="";onDone();break}
     else->socialStatus="Finish signing in in your browser, then return to Artii."
    }
   }catch(e:CancellationException){throw e}
   catch(e:java.io.IOException){socialStatus="Reconnecting to Artii… Your sign-in is still waiting."}
   catch(e:Exception){pendingVault.clear();pending=null;error=e.message?:"Please try signing in again.";break}
   delay(2000)
  }
 }
 fun launchAction(key:String,block:suspend()->Unit){if(active!=null)return;active=key;error=null;operation=scope.launch{try{block()}catch(e:TimeoutCancellationException){error="Sign-in timed out. Please try again."}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Please try again"}finally{active=null}}}
 Box(Modifier.fillMaxSize().artiiBackdrop().imePadding(),contentAlignment=Alignment.TopCenter){
  Column(Modifier.widthIn(max=480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
   TextButton(onClick=onDone,enabled=!locked){Text("← Back")}
   Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Image(painterResource(R.drawable.artii_logo),null,Modifier.size(44.dp));Text("Artii",fontSize=28.sp,fontWeight=FontWeight.Bold)}
    AnimatedContent(targetState=if(flow!=null)"Make it yours." else if(register)"Create your account" else "Welcome back",label="auth-title"){Title(it)}
    Text(if(flow!=null)"Welcome to Artii! Choose your username once. Future sign-ins will use this same profile." else if(register)"Create your Artii identity and find your people." else "Log in to pick up where you left off.",color=Muted,fontSize=14.sp)
   }
   Surface(modifier=Modifier.artiiPanel(26),shape=RoundedCornerShape(26.dp),color=Color.Transparent){
    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
     if(waiting){CircularProgressIndicator(Modifier.size(28.dp));Text(socialStatus,color=Muted)}
     if(flow==null&&!waiting){
      listOf("google" to "Google").forEach{(id,label)->
       OutlinedButton(onClick={launchAction(id){
        val api=m.configureServer(server);val bytes=ByteArray(32).also{SecureRandom().nextBytes(it)};proof=Base64.encodeToString(bytes,Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val challenge=Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(proof.toByteArray()),Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val start=api.request("/auth/social/start","POST",json("provider" to id,"challenge" to challenge));val uri=Uri.parse(start.getString("authorizationUrl"));require(uri.scheme=="https"){"A secure sign-in connection is required"}
        val saved=json("flowId" to start.getString("flowId"),"proof" to proof,"server" to server,"expires" to (System.currentTimeMillis()+600000))
        pendingVault.save(saved.toString());pending=saved
        context.startActivity(Intent(Intent.ACTION_VIEW,uri))
       }},enabled=!locked&&ready&&id in providers,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=Ink)){
        if(active==id)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Image(painterResource(R.drawable.ic_google),null,Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp));Text("Continue with $label",fontWeight=FontWeight.SemiBold)
       }
      }
      if(ready && "google" !in providers)Text("Google sign-in is currently unavailable. You can use email instead.",fontSize=11.sp,color=Muted)
      Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){HorizontalDivider(Modifier.weight(1f));Text("or use email",fontSize=12.sp,color=Muted);HorizontalDivider(Modifier.weight(1f))}
      OutlinedTextField(email,{email=it.take(200)},enabled=!locked,label={Text(if(register)"01  Email address" else "Email address")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email,autoCorrect=false),isError=email.isNotEmpty()&&!emailValid,supportingText={if(email.isNotEmpty()&&!emailValid)Text("Enter a valid email address")},colors=colors,modifier=Modifier.fillMaxWidth())
      OutlinedTextField(password,{password=it.take(128)},enabled=!locked,label={Text(if(register)"02  Password" else "Password")},singleLine=true,visualTransformation=if(visible)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrect=false),trailingIcon={IconButton(onClick={visible=!visible},enabled=!locked){Icon(painterResource(if(visible)R.drawable.ic_eye_off else R.drawable.ic_eye),if(visible)"Hide password" else "Show password")}},supportingText={if(register)Text("10–128 characters · use a unique password")},isError=register&&password.isNotEmpty()&&password.length<10,colors=colors,modifier=Modifier.fillMaxWidth())
     }
     AnimatedVisibility(needUsername){
      Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
       OutlinedTextField(username,{username=it.take(24);available=null},enabled=!locked,label={Text(if(flow!=null)"Your username" else "03  Unique username")},prefix={Text("@")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii,autoCorrect=false),isError=username.isNotEmpty()&&(!usernameValid||available==false||usernameError!=null),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=if(available==true)Good else Coral,unfocusedBorderColor=if(available==true)Good else Color(0xFF4C4F69),errorBorderColor=Danger,errorSupportingTextColor=Danger),supportingText={Text(when{username.isBlank()->"3–24 letters, numbers or underscores";!usernameValid->"Start with a letter; use 3–24 letters, numbers or underscores";checking->"Checking availability…";usernameError!=null->usernameError!!;available==true->"Username available!";available==false->"Username already taken";!ready->"Connect to check availability";else->"Checking availability…"},color=if(available==true)Good else if(available==false)Danger else Muted)},modifier=Modifier.fillMaxWidth())
       Row(verticalAlignment=Alignment.CenterVertically){Checkbox(adult,{adult=it},enabled=!locked);Text("I am 18 or older",fontSize=13.sp)}
      }
     }
     error?.let{Text(it,color=Danger,fontSize=13.sp)}
     if(!waiting)Button(onClick={launchAction("email"){
      if(flow!=null){val api=m.configureServer(server);val r=api.request("/auth/social/complete","POST",json("flowId" to flow,"proof" to proof,"username" to username.trim(),"adult" to adult));m.acceptSession(api,r);pendingVault.clear();pending=null}
      else m.authenticate(server,email,password,username,register,adult)
      password="";onDone()
     }},enabled=!locked&&ready&&(if(flow!=null)true else emailValid&&password.length in 10..128)&&(!needUsername||adult&&usernameValid&&available==true&&!checking),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(15.dp)){
      if(active=="email"){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp))}
      Text(if(flow!=null)"Finish creating account" else if(register)"Create account" else "Log in",fontWeight=FontWeight.Bold)
     }
     if(locked)TextButton(onClick={operation?.cancel();pendingVault.clear();pending=null;flow=null;proof="";error="Sign-in cancelled. You can try again."},modifier=Modifier.fillMaxWidth()){Text("Cancel sign-in")}
     if(flow==null)TextButton(onClick={register=!register;error=null;visible=false},enabled=!locked,modifier=Modifier.fillMaxWidth()){Text(if(register)"Already have an account? Log in" else "New to Artii? Sign up")}
     else TextButton(onClick={pendingVault.clear();pending=null;flow=null;proof=""},enabled=!locked){Text("Use a different sign-in method")}
    }
   }
   Text(connection,color=if(ready)Good else Muted,fontSize=12.sp)
   if(!ready){TextButton(onClick={retry++},enabled=!locked){Text("Retry connection")};if(BuildConfig.DEBUG&&BuildConfig.API_URL.isBlank())Text("Test build: connect your phone by USB and run Start-Artii-Phone-Test.cmd on your PC. No server address needs to be typed.",color=Muted,fontSize=12.sp)}
   if(BuildConfig.DEBUG){TextButton(onClick={settings=!settings},enabled=!locked){Text("Advanced test connection")};AnimatedVisibility(settings){Column{OutlinedTextField(server,{server=it;ready=false},enabled=!locked,label={Text("Test server address")},singleLine=true,modifier=Modifier.fillMaxWidth());TextButton(onClick={server=m.defaultServer;retry++},enabled=!locked){Text("Reset to automatic test address")}}}}
   Spacer(Modifier.height(12.dp))
  }
 }
}
