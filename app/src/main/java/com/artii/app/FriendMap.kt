package com.artii.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.distinctUntilChanged
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import kotlin.math.*

class LocationShare(private val context:Context,private val model:ArtiiModel):LocationListener{
 private val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
 var active by mutableStateOf(false);private set
 var expires by mutableStateOf(0L);private set
 var own by mutableStateOf<Location?>(null);private set
 var audience:List<String> = emptyList();private set
 private val network=Mutex()
 private var ticker:Job?=null;private var generation=0;private var uploading=false
 @SuppressLint("MissingPermission") fun locate(){try{val provider=if(manager.isProviderEnabled(LocationManager.GPS_PROVIDER))LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER;manager.getLastKnownLocation(provider)?.takeIf{System.currentTimeMillis()-it.time<60000}?.let{own=it};if(!active)manager.requestSingleUpdate(provider,this,null)}catch(e:Exception){model.error="Allow location access and turn on your phone's location service"}}
 @SuppressLint("MissingPermission") fun start(ids:List<String>,minutes:Int){
  if(model.demo){model.notice="Sign in to share location";return};if(ids.isEmpty()){model.error="Choose at least one friend";return};stop();generation++;val g=generation;audience=ids;expires=System.currentTimeMillis()+minutes*60000L
  model.scope.launch{try{network.withLock{model.updateSettings("ghost",false)};if(g!=generation)return@launch;active=true;if(model.demo){model.notice="Preview only: your location is not sent";return@launch}
   val providers=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).filter{manager.isProviderEnabled(it)};require(providers.isNotEmpty()){"Turn on your phone's location service"};providers.forEach{manager.requestLocationUpdates(it,2500L,0f,this@LocationShare)}
   ticker=model.scope.launch{while(active&&g==generation){if(System.currentTimeMillis()>=expires){stop();break};own?.takeIf{System.currentTimeMillis()-it.time<30000}?.let{publish(it,g)};delay(3000)}}
  }catch(e:Exception){stop();model.error=e.message}}
 }
 override fun onLocationChanged(location:Location){own=location;if(!active)manager.removeUpdates(this)}
 private suspend fun publish(location:Location,g:Int)=network.withLock{if(uploading||!active||g!=generation)return@withLock;uploading=true;try{model.api.request("/location","PUT",json("lat" to location.latitude,"lon" to location.longitude,"heading" to location.bearing,"expires" to expires,"audience" to audience));if(!active||g!=generation)model.api.request("/location","DELETE")}catch(e:Exception){model.connected=false}finally{uploading=false}}
 fun stop(){val was=active;active=false;generation++;ticker?.cancel();ticker=null;runCatching{manager.removeUpdates(this)};if(was&&!model.demo)model.scope.launch{network.withLock{runCatching{model.api.request("/location","DELETE");model.settings=org.json.JSONObject(model.settings.toString()).put("ghost",true)}}}}
}

// Native vector terrain + lightweight Compose badges. Coordinates interpolate independently of network refreshes.
@Composable fun FriendMap(places:List<Place>,demo:Boolean,share:LocationShare,modifier:Modifier=Modifier){
 val context=LocalContext.current;val activity=context as ComponentActivity
 val mapView=remember{MapLibre.getInstance(context);MapView(context).apply{onCreate(null)}}
 var map by remember{mutableStateOf<MapLibreMap?>(null)};var loaded by remember{mutableStateOf(false)};var failed by remember{mutableStateOf(false)}
 var now by remember{mutableLongStateOf(System.currentTimeMillis())};var focusOwn by remember{mutableStateOf(false)}
 val points=remember{mutableMapOf<String,Pair<Double,Double>>()};val fans=remember{mutableMapOf<String,Offset>()};val currentPlaces by rememberUpdatedState(places)
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){if(it.values.any{granted->granted}){focusOwn=true;share.locate()}}
 val textPaint=remember{android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)}
 val carousel=rememberLazyListState()
 fun focus(p:Place){map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat,p.lon),15.0),850)}
 fun group(){if(places.size==1)focus(places.first())else if(places.size>1){val bounds=LatLngBounds.Builder();places.forEach{bounds.include(LatLng(it.lat,it.lon))};map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),80),900)}}
 DisposableEffect(mapView){
  mapView.addOnDidFailLoadingMapListener{failed=true}
  mapView.getMapAsync{m->map=m;m.uiSettings.isCompassEnabled=true;m.uiSettings.setAttributionMargins(8,0,0,150);m.setStyle("https://tiles.openfreemap.org/styles/positron"){loaded=true;failed=false;group()};m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(if(demo)-20.163 else 15.0,if(demo)57.505 else 0.0),if(demo)13.0 else 1.5))}
  mapView.onStart();mapView.onResume()
  val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START->mapView.onStart();Lifecycle.Event.ON_RESUME->mapView.onResume();Lifecycle.Event.ON_PAUSE->mapView.onPause();Lifecycle.Event.ON_STOP->mapView.onStop();else->{}}}
  activity.lifecycle.addObserver(observer)
  onDispose{activity.lifecycle.removeObserver(observer);mapView.onPause();mapView.onStop();mapView.onDestroy()}
 }
 LaunchedEffect(mapView){var previous=System.currentTimeMillis();while(isActive){delay(33);val time=System.currentTimeMillis();val alpha=(1-exp(-(time-previous).coerceAtMost(1000)/500.0));previous=time
  val ids=currentPlaces.map{it.person.id}.toSet();points.keys.retainAll(ids);fans.keys.retainAll(ids)
  currentPlaces.forEach{p->val old=points[p.person.id]?:Pair(p.lat,p.lon);val delta=((p.lon-old.second+540)%360)-180;points[p.person.id]=Pair(old.first+(p.lat-old.first)*alpha,((old.second+delta*alpha+540)%360)-180)};now=time}}
 LaunchedEffect(carousel,loaded){snapshotFlow{carousel.firstVisibleItemIndex}.distinctUntilChanged().collect{index->if(carousel.isScrollInProgress)currentPlaces.getOrNull(index)?.let{focus(it)}}}
 LaunchedEffect(share.own,focusOwn){if(focusOwn)share.own?.let{map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude,it.longitude),15.0),850);focusOwn=false}}
 Box(modifier.background(Color(0xFFF4F5F7))){
  AndroidView(factory={mapView},modifier=Modifier.fillMaxSize())
  Canvas(Modifier.fillMaxSize()){
   val clock=now;val projection=map?.projection?:return@Canvas
   val projected=places.map{p->val pos=points[p.person.id]?:Pair(p.lat,p.lon);val screen=projection.toScreenLocation(LatLng(pos.first,pos.second));p to Offset(screen.x,screen.y)}
   val groups=projected.groupBy{Pair((it.second.x/55.dp.toPx()).roundToInt(),(it.second.y/55.dp.toPx()).roundToInt())}
   groups.values.forEach{cluster->cluster.forEachIndexed{index,(p,base)->
    val angle=-PI/2+2*PI*index/cluster.size;val target=if(cluster.size>1)Offset((cos(angle)*44.dp.toPx()).toFloat(),(sin(angle)*44.dp.toPx()).toFloat())else Offset.Zero
    val before=fans[p.person.id]?:Offset.Zero;val fan=before+(target-before)*.15f;fans[p.person.id]=fan;val center=base+fan
    if(center.x< -60.dp.toPx()||center.x>size.width+60.dp.toPx()||center.y< -60.dp.toPx()||center.y>size.height+60.dp.toPx())return@forEachIndexed
    val age=clock-p.updated;val opacity=if(demo||age<60000)1f else .4f;val radius=23.dp.toPx();val tint=badgeColor(p.person)
    drawCircle(Color.Black.copy(alpha=.3f*opacity),radius+5.dp.toPx(),center+Offset(0f,5.dp.toPx()))
    val tail=Path().apply{moveTo(center.x-8.dp.toPx(),center.y+radius-3.dp.toPx());lineTo(center.x,center.y+radius+10.dp.toPx());lineTo(center.x+8.dp.toPx(),center.y+radius-3.dp.toPx());close()};drawPath(tail,Color.White.copy(alpha=opacity))
    drawCircle(Color.White.copy(alpha=opacity),radius+2.dp.toPx(),center)
    drawCircle(Brush.linearGradient(listOf(tint.copy(alpha=opacity),tint.copy(alpha=.65f*opacity)),center-Offset(radius,radius),center+Offset(radius,radius)),radius,center)
    drawArc(Color.White.copy(alpha=.5f*opacity),205f,100f,false,topLeft=center-Offset(radius-3.dp.toPx(),radius-3.dp.toPx()),size=androidx.compose.ui.geometry.Size((radius-3.dp.toPx())*2,(radius-3.dp.toPx())*2),style=Stroke(1.5.dp.toPx()))
    if(demo||age<15000){val pulse=((sin(clock/400.0)+1)/2).toFloat();drawCircle(Color(0xFF63F2B2).copy(alpha=.25f+.5f*pulse),radius+5.dp.toPx()+pulse*3.dp.toPx(),center,style=Stroke(2.dp.toPx()))}
    drawIntoCanvas{canvas->val paint=textPaint.apply{color=android.graphics.Color.WHITE;alpha=(255*opacity).toInt();textAlign=android.graphics.Paint.Align.CENTER;textSize=16.sp.toPx();typeface=android.graphics.Typeface.DEFAULT_BOLD};canvas.nativeCanvas.drawText(initials(p.person),center.x,center.y+5.sp.toPx(),paint)}
   }}
  }
  Column(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top=64.dp,end=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
   FilledTonalButton(colors=ButtonDefaults.filledTonalButtonColors(containerColor=Cream,contentColor=Ink),border=BorderStroke(1.dp,ArtiiOutline),onClick={permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))}){Text("◎ My location",fontSize=11.sp)}
   FilledTonalButton(colors=ButtonDefaults.filledTonalButtonColors(containerColor=Cream,contentColor=Ink),border=BorderStroke(1.dp,ArtiiOutline),onClick={group()},enabled=places.isNotEmpty()){Text("⌖ Recenter group",fontSize=11.sp)}
  }
  if(!loaded)Surface(Modifier.align(Alignment.Center).padding(24.dp),shape=RoundedCornerShape(18.dp)){Column(Modifier.padding(16.dp)){Text(if(failed)"Map is offline. Your friends stay listed below." else "Loading your world…");if(failed)TextButton(onClick={failed=false;map?.setStyle("https://tiles.openfreemap.org/styles/positron"){loaded=true}}){Text("Retry map")}}}
  LazyRow(state=carousel,modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=16.dp),contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
   items(places,key={it.person.id}){p->Surface(onClick={focus(p)},color=Color(0xF2101932),shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,badgeColor(p.person)),modifier=Modifier.width(240.dp)){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){LetterBadge(p.person,48);Column{Text("@"+p.person.username.ifBlank{p.person.name.lowercase()},fontSize=14.sp);Text(if(demo)"Sample location" else if(now-p.updated<15000)"Live now" else "Last seen "+((now-p.updated)/60000).coerceAtLeast(1)+" mins ago",fontSize=11.sp,color=Muted);Text(p.person.country.ifBlank{"Shared with you"},fontSize=10.sp,color=Muted)}}}}
  }
  if(demo)Surface(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start=12.dp,top=64.dp),shape=RoundedCornerShape(16.dp)){Text("Sample friends",Modifier.padding(8.dp),fontSize=10.sp)}
 }
}
