package com.artii.app
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable fun FriendFinder(m:ArtiiModel){
 var username by remember{mutableStateOf("")};var person by remember{mutableStateOf<Person?>(null)};var error by remember{mutableStateOf<String?>(null)};var searching by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
  OutlinedTextField(username,{username=it.take(24);person=null;error=null},label={Text("Search & add friend")},prefix={Text("@")},singleLine=true,enabled=!searching,modifier=Modifier.fillMaxWidth(),trailingIcon={TextButton(enabled=!searching&&username.isNotBlank(),onClick={searching=true;error=null;scope.launch{try{person=m.findFriendByUsername(username)}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{searching=false}}}){Text(if(searching)"…" else "Find")}})
  error?.let{Text(it,color=MaterialTheme.colorScheme.error,fontSize=12.sp)}
  person?.let{p->Row{Column(Modifier.weight(1f)){Text(p.name);Text("@"+p.username,fontSize=12.sp,color=Coral)};TextButton(enabled=!m.busy&&p.id !in m.sent&&m.friends.none{it.id==p.id},onClick={m.requestFriend(p)}){Text(if(p.id in m.sent)"Requested" else if(m.friends.any{it.id==p.id})"Friends" else "Add friend")}}}
 }
}
