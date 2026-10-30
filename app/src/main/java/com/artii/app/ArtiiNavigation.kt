package com.artii.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun ArtiiNavigation(selected:String,onSelect:(String)->Unit){
 val tabs=listOf("Profile","Discover","Chats","Games","Meet")
 val labels=listOf("Profile","Discover","Chats","Games","Nearby")
 val icons=listOf(R.drawable.nav_profile,R.drawable.nav_discover,R.drawable.nav_chats,R.drawable.nav_games,R.drawable.nav_nearby)
 Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF0B132B),Color(0xFF111433),Color(0xFF0B132B)))).navigationBarsPadding().padding(horizontal=6.dp,vertical=12.dp).selectableGroup()){
  tabs.forEachIndexed{i,key->
   val active=selected==key
   Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).selectable(selected=active,role=Role.Tab,onClick={onSelect(key)}).padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally){
    val shape=RoundedCornerShape(24.dp)
    val pill=if(active)Modifier.shadow(10.dp,shape,ambientColor=Color(0xFFB520FF),spotColor=Color(0xFFEC32FF)).background(Brush.linearGradient(listOf(Color(0xFFFF41E8),Color(0xFFA200EF),Color(0xFF523AFF),Color(0xFF20ADFF))),shape).border(1.dp,Brush.linearGradient(listOf(Color(0xFFFF95EE),Color(0xFFC75AFF),Color(0xFF66BCFF))),shape)else Modifier
    Box(Modifier.fillMaxWidth().height(36.dp).then(pill),contentAlignment=Alignment.Center){Icon(painterResource(if(active&&key=="Meet")R.drawable.nav_nearby_selected else icons[i]),null,Modifier.size(27.dp),tint=if(active)Color(0xFFFFB5F3)else Color(0xFFBCB9F4))}
    Spacer(Modifier.height(5.dp))
    Text(labels[i],color=if(active)Color(0xFFFF88F0)else Color(0xFFBCB9F4),fontSize=12.sp,fontWeight=if(active)FontWeight.SemiBold else FontWeight.Medium,maxLines=1)
   }
  }
 }
}
