package com.artii.app
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class EmojiEntry(val emoji:String,val name:String,val group:String)
// A drawn icon avoids Android replacing the picker control with a colored emoji.
@Composable fun OutlineSmiley(modifier:Modifier=Modifier){
 Canvas(modifier.size(26.dp)){
  val ink=Color(0xFFDCE2EB);val stroke=Stroke(2.dp.toPx())
  drawCircle(ink,size.minDimension*.43f,style=stroke)
  drawCircle(ink,size.width*.045f,Offset(size.width*.35f,size.height*.37f))
  drawCircle(ink,size.width*.045f,Offset(size.width*.65f,size.height*.37f))
  drawArc(ink,0f,180f,false,Offset(size.width*.27f,size.height*.40f),Size(size.width*.46f,size.height*.32f),style=stroke)
 }
}
@Composable fun EmojiTray(onSelect:(String)->Unit){
 val context=LocalContext.current
 val catalogue by produceState<List<EmojiEntry>>(emptyList()){value=withContext(Dispatchers.IO){JSONArray(context.assets.open("emojis.json").bufferedReader().use{it.readText()}).objects().map{EmojiEntry(it.getString("emoji"),it.getString("name"),it.getString("group"))}}}
 var query by remember{mutableStateOf("")};var category by remember{mutableStateOf("All")}
 var searching by remember{mutableStateOf(false)}
 val grid=rememberLazyGridState()
 val groups=remember{linkedMapOf("All" to "◎","Smileys & Emotion" to "☺","People & Body" to "♙","Animals & Nature" to "♧","Food & Drink" to "♨","Travel & Places" to "✈","Activities" to "⚽","Objects" to "◇","Symbols" to "♪","Flags" to "⚑")}
 val filtered=remember(catalogue,query,category){catalogue.filter{(category=="All"||it.group==category)&&(query.isBlank()||it.name.contains(query,true))}}
 LaunchedEffect(category,query){grid.scrollToItem(0)}
 val trayHeight=(LocalConfiguration.current.screenHeightDp*.44f).coerceIn(220f,380f).dp
 Column(Modifier.fillMaxWidth().height(trayHeight).background(Color(0xFF151E29))){
  Box(Modifier.align(Alignment.CenterHorizontally).padding(top=6.dp).size(32.dp,3.dp).background(Color(0xFF657080),CircleShape))
  Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){
   IconButton(onClick={searching=!searching;if(!searching)query=""}){Icon(if(searching)Icons.Default.Close else Icons.Default.Search,if(searching)"Close emoji search" else "Search emojis",tint=Color(0xFFDCE2EB))}
   if(searching)TextField(query,{query=it},singleLine=true,placeholder={Text("Search emojis",fontSize=14.sp)},modifier=Modifier.weight(1f))
   else Text(if(category=="All")"Emojis" else category,color=Color(0xFFDCE2EB),fontSize=14.sp)
  }
  if(filtered.isEmpty())Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text(if(catalogue.isEmpty())"Loading emojis…" else "No emojis found",color=Color(0xFFA8B2C1))}
  else LazyVerticalGrid(columns=GridCells.Adaptive(48.dp),state=grid,modifier=Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(horizontal=6.dp,vertical=4.dp)){
   items(filtered,key={it.emoji}){item->Box(Modifier.fillMaxWidth().height(50.dp).clickable{onSelect(item.emoji)}.semantics{contentDescription=item.name;role=Role.Button},contentAlignment=Alignment.Center){Text(item.emoji,fontSize=30.sp,modifier=Modifier.clearAndSetSemantics{})}}
  }
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
   groups.forEach{(group,symbol)->Box(Modifier.size(48.dp).padding(3.dp).background(if(category==group)Color(0xFF483765) else Color.Transparent,CircleShape).clickable{category=group}.semantics{contentDescription=group;selected=category==group;role=Role.Tab},contentAlignment=Alignment.Center){
    if(group=="Smileys & Emotion")OutlineSmiley() else Text(symbol+"\uFE0E",fontSize=23.sp,color=Color(0xFFDCE2EB),modifier=Modifier.clearAndSetSemantics{})
   }}
  }
 }
}
