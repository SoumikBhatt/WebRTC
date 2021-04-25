package com.madroid.webrtc.activities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
//import com.github.nkzawa.socketio.client.IO
//import com.github.nkzawa.socketio.client.Socket
//import com.github.nkzawa.socketio.client.Socket.EVENT_CONNECT
//import com.github.nkzawa.socketio.client.Socket.EVENT_DISCONNECT
import com.madroid.webrtc.R
import com.madroid.webrtc.SimpleSdpObserver
import com.madroid.webrtc.databinding.ActivityCallBinding
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.client.Socket.EVENT_CONNECT
import io.socket.client.Socket.EVENT_DISCONNECT
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.*
import org.webrtc.PeerConnection.Observer
import org.webrtc.SurfaceTextureHelper
import java.net.URISyntaxException
import java.util.*


class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private var socket: Socket?=null
    private var isInitiator: Boolean = false
    private var isChannelReady: Boolean = false
    private var isStarted: Boolean = false
    var audioConstraints: MediaConstraints? = null
    var videoConstraints: MediaConstraints? = null
    var sdpConstraints: MediaConstraints? = null
    var videoSource: VideoSource? = null
    var localVideoTrack: VideoTrack? = null
    var audioSource: AudioSource? = null
    var localAudioTrack: AudioTrack? = null
    var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var peerConnection: PeerConnection? = null
    private var rootEglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var videoTrackFromCamera: VideoTrack? = null


    private val videoCapturer by lazy { getVideoCapturer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,
            R.layout.activity_call
        )

//        setSupportActionBar(binding.toolbar)

        if (allPermissionsGranted()) start() else ActivityCompat.requestPermissions(
                this,
            REQUIRED_PERMISSIONS,
            REQUEST_CODE_PERMISSIONS
        )

    }

    override fun onDestroy() {
        if (socket != null) {
            sendMessage("bye")
            socket?.disconnect()
        }
        super.onDestroy()
    }

    private fun start() {

        connectToSignallingServer()
        initializeSurfaceViews()
        initializePeerConnectionFactory()
//        createVideoTrackFromCameraAndShowIt()
        startLocalVideoCapture(binding.surfaceView)
        initializePeerConnections()
        startStreamingVideo()
    }

    private fun connectToSignallingServer() {
        try {
            socket = IO.socket("https://desolate-brook-24962.herokuapp.com/")
            //https://lit-chamber-59828.herokuapp.com/

            socket?.on(EVENT_CONNECT) {
                Log.d(TAG, "connectToSignallingServer: connect")
                socket?.emit("create-join-salvin", "foo")
            }?.on("ipaddr-salvin") { Log.d(TAG, "connectToSignallingServer: ipaddr") }?.on("created-salvin") { args ->
                Log.d(TAG, "connectToSignallingServer: created")
                isInitiator = true
            }?.on("full-salvin") { Log.d(TAG, "connectToSignallingServer: full") }?.on("join-salvin") { args ->
                Log.d(TAG, "connectToSignallingServer: join")
                Log.d(TAG, "connectToSignallingServer: Another peer made a request to join room")
                Log.d(TAG, "connectToSignallingServer: This peer is the initiator of room")
                isChannelReady = true
            }?.on("joined-salvin") {
                Log.d(TAG, "connectToSignallingServer: joined")
                isChannelReady = true
            }?.on("log-salvin") { args ->
                for (arg in args) {
                    Log.d(TAG, "connectToSignallingServer: $arg")
                }
            }?.on("message-salvin") { args -> Log.d(TAG, "connectToSignallingServer: got a message") }?.on("message-salvin") { args ->
                try {
                    if (args[0] is String) {
                        val message = args[0] as String
                        if (message == "got user media") {
                            maybeStart()
                        }
                    } else {
                        val message = args[0] as JSONObject
                        Log.d(TAG, "connectToSignallingServer: got message $message")
                        if (message.getString("type") == "offer") {
                            Log.d(TAG, "connectToSignallingServer: received an offer $isInitiator $isStarted")
                            if (!isInitiator && !isStarted) {
                                maybeStart()
                            }
                            peerConnection!!.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.OFFER, message.getString("sdp")))
                            doAnswer()
                        } else if (message.getString("type") == "answer" && isStarted) {
                            peerConnection!!.setRemoteDescription(SimpleSdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, message.getString("sdp")))
                        } else if (message.getString("type") == "candidate" && isStarted) {
                            Log.d(TAG, "connectToSignallingServer: receiving candidates")
                            val candidate = IceCandidate(message.getString("id"), message.getInt("label"), message.getString("candidate"))
                            peerConnection!!.addIceCandidate(candidate)
                        }
                        /*else if (message === 'bye' && isStarted) {
                                handleRemoteHangup();
                            }*/
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }?.on(EVENT_DISCONNECT) {
                Log.d(TAG, "connectToSignallingServer: disconnect")
            }
            socket?.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    private fun initializeSurfaceViews() {
        rootEglBase = EglBase.create()
        binding.surfaceView.init(rootEglBase?.eglBaseContext, null)
        binding.surfaceView.setEnableHardwareScaler(true)
        binding.surfaceView.setMirror(true)
        binding.surfaceView2.init(rootEglBase?.eglBaseContext, null)
        binding.surfaceView2.setEnableHardwareScaler(true)
        binding.surfaceView2.setMirror(true)

        //add one more
    }

    private fun initializePeerConnectionFactory() {

        val options = PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        factory = PeerConnectionFactory
                .builder()
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase?.eglBaseContext))
                .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase?.eglBaseContext, true, true))
                .setOptions(PeerConnectionFactory.Options().apply {
                    disableEncryption = true
                    disableNetworkMonitor = true
                })
                .createPeerConnectionFactory()

//        binding.surfaceView.apply {
//            setMirror(true)
//            setEnableHardwareScaler(true)
//            init(rootEglBase?.eglBaseContext, null)
//        }
//

//        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)
//        factory = PeerConnectionFactory(null)
//        factory.setVideoHwAccelerationOptions(rootEglBase!!.eglBaseContext, rootEglBase!!.eglBaseContext)
    }

    private fun createVideoTrackFromCameraAndShowIt() {
        audioConstraints = MediaConstraints()
        val videoCapturer: VideoCapturer? = getVideoCapturer(this)

        if (videoCapturer != null) {
            val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase!!.eglBaseContext)
            videoSource = factory!!.createVideoSource(videoCapturer.isScreencast)
            videoCapturer.initialize(surfaceTextureHelper, this, videoSource?.capturerObserver)
        }

        val videoSource = factory!!.createVideoSource(false)
        videoCapturer?.startCapture(
            VIDEO_RESOLUTION_WIDTH,
            VIDEO_RESOLUTION_HEIGHT,
            FPS
        )
        videoTrackFromCamera = factory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        videoTrackFromCamera?.setEnabled(true)
        videoTrackFromCamera?.addSink(binding.surfaceView)

        //create an AudioSource instance
        audioSource = factory!!.createAudioSource(audioConstraints)
        localAudioTrack = factory!!.createAudioTrack("101", audioSource)
    }

    private fun getVideoCapturer(context: Context) =
            Camera2Enumerator(context).run {
                deviceNames.find {
                    isFrontFacing(it)
                }?.let {
                    createCapturer(it, null)
                } ?: throw IllegalStateException()
            }

    private fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        audioConstraints = MediaConstraints()
        val localVideoSource = factory!!.createVideoSource(false)
        val surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase?.eglBaseContext)
        (videoCapturer as VideoCapturer).initialize(surfaceTextureHelper, localVideoOutput.context, localVideoSource.capturerObserver)

        videoCapturer.startCapture(
            VIDEO_RESOLUTION_WIDTH,
            VIDEO_RESOLUTION_HEIGHT,
            FPS
        )
        videoTrackFromCamera = factory?.createVideoTrack(VIDEO_TRACK_ID, localVideoSource)
        videoTrackFromCamera?.addSink(localVideoOutput)
//        val localStream = factory?.createLocalMediaStream(STREA)
//        localStream?.addTrack(localVideoTrack)
//        peerConnection?.addStream(localStream)

//
        audioSource = factory!!.createAudioSource(audioConstraints)
        localAudioTrack = factory!!.createAudioTrack("101", audioSource)
    }

    private fun createVideoCapture(): VideoCapturer? {
        return if (useCamera2()) {
            createCameraCapture(Camera2Enumerator(this))!!
        } else {
            createCameraCapture(Camera1Enumerator(true))!!
        }
    }

    private fun initializePeerConnections() {
        peerConnection = createPeerConnection(factory!!)
    }

    private fun startStreamingVideo() {
        val mediaStream = factory!!.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(videoTrackFromCamera)
        mediaStream.addTrack(localAudioTrack)
        peerConnection!!.addStream(mediaStream)
        sendMessage("got user media")
    }

    private fun doAnswer() {
        peerConnection!!.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "answer")
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, MediaConstraints())
    }

    private fun maybeStart() {
        Log.d(TAG, "maybeStart: $isStarted $isChannelReady")
        if (!isStarted && isChannelReady) {
            isStarted = true
            if (isInitiator) {
                doCall()
            }
        }
    }

    private fun doCall() {
        val sdpMediaConstraints = MediaConstraints()
        sdpMediaConstraints.mandatory.add(
                MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        sdpMediaConstraints.mandatory.add(
                MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        peerConnection!!.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "onCreateSuccess: ")
                peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "offer")
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, sdpMediaConstraints)
    }

    private fun createCameraCapture(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapture: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapture != null) {
                    return videoCapture
                }
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapture: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapture != null) {
                    return videoCapture
                }
            }
        }
        return null
    }

    private fun createPeerConnection(factory: PeerConnectionFactory): PeerConnection? {
        val iceServers = ArrayList<IceServer>()
        iceServers.add(IceServer("stun:stun.l.google.com:19302"))
        val rtcConfig = RTCConfiguration(iceServers)
        val pcConstraints = MediaConstraints()
        val pcObserver: Observer = object : Observer {
            override fun onSignalingChange(signalingState: SignalingState) {
                Log.d(TAG, "onSignalingChange: ")
            }

            override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: ")
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                Log.d(TAG, "onIceConnectionReceivingChange: ")
            }

            override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: ")
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                Log.d(TAG, "onIceCandidate: ")
                val message = JSONObject()
                try {
                    message.put("type", "candidate")
                    message.put("label", iceCandidate.sdpMLineIndex)
                    message.put("id", iceCandidate.sdpMid)
                    message.put("candidate", iceCandidate.sdp)
                    Log.d(TAG, "onIceCandidate: sending candidate $message")
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                Log.d(TAG, "onIceCandidatesRemoved: ")
            }

            override fun onAddStream(mediaStream: MediaStream) {
                Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size)
                val remoteVideoTrack = mediaStream.videoTracks[0]
                val remoteAudioTrack = mediaStream.audioTracks[0]
                remoteAudioTrack.setEnabled(true)
                remoteVideoTrack.setEnabled(true)
                remoteVideoTrack.addSink(binding.surfaceView2)
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.d(TAG, "onRemoveStream: ")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.d(TAG, "onDataChannel: ")
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded: ")
            }

            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {

            }
        }
        return factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    private fun sendMessage(message: Any) {
        socket?.emit("message-salvin", message)
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // permission granted
                start()
            } else {
                ActivityCompat.requestPermissions(
                        this,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        private const val TAG = "CallActivity"
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val VIDEO_RESOLUTION_WIDTH = 1280
        const val VIDEO_RESOLUTION_HEIGHT = 720
        const val FPS = 30
    }
}