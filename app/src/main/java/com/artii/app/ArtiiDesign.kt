package com.artii.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

val ArtiiGradient = Brush.horizontalGradient(listOf(Color(0xFFE940C7),Color(0xFF863AFF),Color(0xFF248CFF)))
val ArtiiPanel = Brush.linearGradient(listOf(Color(0xFF1C2446),Color(0xFF111B36)))
val ArtiiOutline = Brush.linearGradient(listOf(Color(0xFF8053BE),Color(0xFF354D89),Color(0xFF303658)))

// Lightweight vector decoration: scales with the screen without large bitmap backgrounds.
fun Modifier.artiiBackdrop() = background(Color(0xFF0B132B)).drawBehind {
    val w=size.width;val h=size.height
    drawCircle(Brush.radialGradient(listOf(Color(0xFF55299B).copy(alpha=.24f),Color.Transparent),Offset(w,h*.12f),w*.75f),w*.75f,Offset(w,h*.12f))
    val ribbon=Path().apply{moveTo(-w*.1f,h*.08f);cubicTo(w*.28f,h*.28f,w*.66f,-h*.06f,w*1.1f,h*.17f)}
    drawPath(ribbon,Brush.horizontalGradient(listOf(Color(0xFFBB38D8).copy(alpha=.12f),Color(0xFF245DFF).copy(alpha=.10f))),style=Stroke(26.dp.toPx()))
    drawPath(ribbon,Brush.horizontalGradient(listOf(Color(0xFFDD4FD8).copy(alpha=.35f),Color(0xFF3877FF).copy(alpha=.24f))),style=Stroke(1.dp.toPx()))
}
fun Modifier.artiiPanel(radius:Int=24) = background(ArtiiPanel,RoundedCornerShape(radius.dp)).border(1.dp,ArtiiOutline,RoundedCornerShape(radius.dp))
fun Modifier.artiiAction(enabled:Boolean=true) = background(if(enabled) ArtiiGradient else Brush.horizontalGradient(listOf(Color(0xFF343C60),Color(0xFF343C60))),RoundedCornerShape(24.dp))
@Composable fun ArtiiCard(title:String,content:@Composable ColumnScope.()->Unit){
 Column(Modifier.fillMaxWidth().artiiPanel().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
  Text(title,style=MaterialTheme.typography.titleLarge,color=Ink);content()
 }
}
