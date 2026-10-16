package com.artii.app

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.webrtc.*

class CallEngine(private val context:Context,private val model:ArtiiModel,val call:CallInfo) {
    var status by mutableStateOf("Connecting…");private set
    var muted by mutableStateOf(false);private set
    var cameraOff by mutableStateOf(false);private set
    var finished by mutableStateOf(false);private set
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val egl=EglBase.create()
    val local=SurfaceViewRenderer(context)
    val remote=SurfaceViewRenderer(context)
    private var factory:PeerConnectionFactory?=null
    private var peer:PeerConnection?=null
    private var audio:AudioTrack?=null
    private var video:VideoTrack?=null
    private var audioSource:AudioSource?=null
    private var videoSource:VideoSource?=null
    private var capture:VideoCapturer?=null
    private var helper:SurfaceTextureHelper?=null
    private var closed=false
    private var offered=false
    private var remoteSet=false
    private var cursor=0
    private val pending=mutableListOf<IceCandidate>()
    private val sound=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val oldMode=sound.mode
    private val oldSpeaker=sound.isSpeakerphoneOn
    init {
        local.init(egl.eglBaseContext,null);local.setMirror(true);local.setZOrderMediaOverlay(true)
        remote.init(egl.eglBaseContext,null)
        scope.launch{try{initialize();poll()}catch(e:CancellationException){throw e}catch(e:Exception){model.error=e.message?:"Call failed";close()}}
    }
    private suspend fun initialize(){
        val config=model.api.request("/calls/${call.id}")
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        factory=PeerConnectionFactory.builder().setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext,true,true)).setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext)).createPeerConnectionFactory()
        val ice=config.getJSONArray("iceServers").objects().map{o->PeerConnection.IceServer.builder(o.getJSONArray("urls").strings()).setUsername(o.optString("username")).setPassword(o.optString("credential")).createIceServer()}
        val rtc=PeerConnection.RTCConfiguration(ice).apply{sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN}
        peer=factory!!.createPeerConnection(rtc,object:PeerConnection.Observer{
            override fun onSignalingChange(s:PeerConnection.SignalingState?){}
            override fun onIceConnectionChange(s:PeerConnection.IceConnectionState?){scope.launch{if(s==PeerConnection.IceConnectionState.CONNECTED||s==PeerConnection.IceConnectionState.COMPLETED){model.discovery.optJSONObject("active")?.optString("id")?.let{id->runCatching{model.api.request("/discover/"+id+"/ready","POST")}}};status=when(s){PeerConnection.IceConnectionState.CONNECTED,PeerConnection.IceConnectionState.COMPLETED->"Connected";PeerConnection.IceConnectionState.FAILED->"Connection failed. The server may need a TURN relay.";PeerConnection.IceConnectionState.DISCONNECTED->"Reconnecting…";else->status}}}
            override fun onIceConnectionReceivingChange(b:Boolean){}
            override fun onIceGatheringChange(s:PeerConnection.IceGatheringState?){}
            override fun onIceCandidate(c:IceCandidate?){if(c!=null)signal("ice",json("sdpMid" to c.sdpMid,"sdpMLineIndex" to c.sdpMLineIndex,"candidate" to c.sdp).toString())}
            override fun onIceCandidatesRemoved(c:Array<out IceCandidate>?){}
            override fun onAddStream(s:MediaStream?){}
            override fun onRemoveStream(s:MediaStream?){}
            override fun onDataChannel(c:DataChannel?){}
            override fun onRenegotiationNeeded(){}
            override fun onAddTrack(receiver:RtpReceiver?,streams:Array<out MediaStream>?){(receiver?.track() as? VideoTrack)?.addSink(remote)}
        })?:error("Could not start the call")
        audioSource=factory!!.createAudioSource(MediaConstraints());audio=factory!!.createAudioTrack("artii-audio",audioSource);peer!!.addTrack(audio,listOf("artii"))
        val cameras=Camera2Enumerator(context);val device=cameras.deviceNames.firstOrNull{cameras.isFrontFacing(it)}?:cameras.deviceNames.firstOrNull()?:error("No camera available")
        capture=cameras.createCapturer(device,null)?:error("Could not open camera")
        videoSource=factory!!.createVideoSource(false);helper=SurfaceTextureHelper.create("ArtiiCamera",egl.eglBaseContext)
        capture!!.initialize(helper,context,videoSource!!.capturerObserver);capture!!.startCapture(640,480,24)
        video=factory!!.createVideoTrack("artii-video",videoSource);video!!.addSink(local);peer!!.addTrack(video,listOf("artii"))
        sound.mode=AudioManager.MODE_IN_COMMUNICATION;sound.isSpeakerphoneOn=true
    }
    private suspend fun poll(){while(!closed){
        val result=model.api.request("/calls/${call.id}").getJSONObject("call");val s=result.getString("status")
        if(s=="ended"){close();break}
        if(s=="ringing")status="Ringing…"
        if(s=="accepted"){
            if(call.from==model.me.id&&!offered){offered=true;peer?.createOffer(createObserver("offer"),MediaConstraints())}
            val signals=model.api.request("/calls/${call.id}/signals?after=$cursor").getJSONArray("signals").objects()
            for(item in signals){cursor=maxOf(cursor,item.getInt("seq"));val data=item.getString("data");when(item.getString("type")){
                "offer","answer"->{val type=SessionDescription.Type.fromCanonicalForm(item.getString("type"));peer?.setRemoteDescription(object:SdpAdapter(){override fun onSetSuccess(){scope.launch{remoteSet=true;pending.forEach{peer?.addIceCandidate(it)};pending.clear();if(type==SessionDescription.Type.OFFER)peer?.createAnswer(createObserver("answer"),MediaConstraints())}}},SessionDescription(type,data))}
                "ice"->{val c=JSONObject(data);val candidate=IceCandidate(c.optString("sdpMid"),c.getInt("sdpMLineIndex"),c.getString("candidate"));if(remoteSet)peer?.addIceCandidate(candidate)else pending.add(candidate)}
            }}
        };delay(1200)
    }}
    private fun createObserver(type:String)=object:SdpAdapter(){override fun onCreateSuccess(s:SessionDescription?){if(s!=null)peer?.setLocalDescription(object:SdpAdapter(){override fun onSetSuccess(){signal(type,s.description)}},s)};override fun onCreateFailure(error:String?){scope.launch{status=error?:"Call setup failed"}}}
    private fun signal(type:String,data:String){scope.launch{try{model.api.request("/calls/${call.id}/signals","POST",json("type" to type,"data" to data))}catch(e:Exception){status="Call connection interrupted"}}}
    fun toggleMic(){muted=!muted;audio?.setEnabled(!muted)}
    fun toggleCamera(){cameraOff=!cameraOff;video?.setEnabled(!cameraOff)}
    fun switchCamera(){(capture as? CameraVideoCapturer)?.switchCamera(null)}
    fun close(){if(closed)return;closed=true;finished=true;status="Call ended";scope.cancel();model.scope.launch{runCatching{model.api.request("/calls/${call.id}/end","POST")}};runCatching{capture?.stopCapture()};capture?.dispose();video?.removeSink(local);peer?.close();peer?.dispose();video?.dispose();audio?.dispose();videoSource?.dispose();audioSource?.dispose();helper?.dispose();local.release();remote.release();factory?.dispose();egl.release();sound.mode=oldMode;sound.isSpeakerphoneOn=oldSpeaker}
}
open class SdpAdapter:SdpObserver {override fun onCreateSuccess(s:SessionDescription?){};override fun onSetSuccess(){};override fun onCreateFailure(error:String?){};override fun onSetFailure(error:String?){} }
