package me.masm11.contextplayer10

import androidx.appcompat.app.AppCompatActivity
import android.app.Service
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile

import java.util.WeakHashMap

import kotlinx.coroutines.*
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.net.Uri
import java.io.File

class MainService : Service() {
    private lateinit var audioManager: AudioManager
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private val audioAttributes = AudioAttributes.Builder().apply {
	setUsage(AudioAttributes.USAGE_MEDIA)
	setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    }.build()
    val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).apply {
	setAudioAttributes(audioAttributes)
	// system handles duck.
	// setWillPauseWhenDucked(false)
	setOnAudioFocusChangeListener { focusChange ->
	    Log.d("focusChange=${focusChange}")
	    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
		handleStop(false)
	    }
	}
    }.build()
    private val player = Player(this, scope, audioAttributes)
    private lateinit var playingContextUuid: String
    private lateinit var playingContext: PlayContext
    
    override fun onCreate() {
	super.onCreate()
	
	PlayContextStore.load(this)
	
	playingContextUuid = PlayContextStore.getPlayingUuid()
	playingContext = PlayContextStore.find(playingContextUuid)
	
	player.initialize()
	
	scope.launch {
	    if (playingContext.path == null) {
		playingContext.path = "//primary/nana/impact_exciter/nana_ie_16.ogg"
		PlayContextStore.save()
	    }
	    player.setTopDir(MFile(playingContext.topDir))
	    var path = playingContext.path
	    if (path != null)
		player.jumpTo(MFile(path), playingContext.msec)
	}
	
	audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
	
	startBroadcaster()
    }
    
    override fun onDestroy() {
	Log.d("service destroyed")
	handleStop(true)
	stopBroadcaster()
	Log.d("canceling scope")
	scope.cancel()
	Log.d("super destroy")
	super.onDestroy()
    }
    
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
	Log.d("onStartCommand")
	return START_NOT_STICKY
    }
    
    
    interface OnPlayStatusBroadcastListener {
	fun onPlayStatusBroadcastListener(playStatus: Player.PlayStatus)
    }
    private val onPlayStatusBroadcastListeners = WeakHashMap<OnPlayStatusBroadcastListener, Boolean>()
    private lateinit var broadcaster: Thread
    
    fun startBroadcaster() {
	broadcaster = Thread { ->
	    try {
		while (true) {
		    scope.launch {
			val playStatus = player.getPlayStatus()

			if (playStatus.file != null) {
			    playingContext.topDir = playStatus.topDir.toString()
			    playingContext.path = playStatus.file.toString()
			    playingContext.msec = playStatus.msec
			    PlayContextStore.save(false)

			    try {
				onPlayStatusBroadcastListeners.forEach { listener, _ ->
				    scope.launch {
					withContext(Dispatchers.Main) {
					    listener.onPlayStatusBroadcastListener(playStatus)
					}
				    }
				}
			    } catch (e: Exception) {
				Log.e("stopped", e)
			    }
			}
		    }
		    Thread.sleep(100)
		}
	    } catch (e: InterruptedException) {
	    }
	}
	broadcaster.start()
    }
    
    fun stopBroadcaster() {
	broadcaster.interrupt()
	broadcaster.join()
    }
    
    
    inner class Binder: android.os.Binder() {
	suspend fun play() {
	    handlePlay()
	}
	suspend fun jump(path: MFile) {
	    handleJump(path)
	}
	fun stop() {
	    handleStop(false)
	}
	suspend fun prev() {
	    handlePrev()
	}
	suspend fun next() {
	    handleNext()
	}
	fun seek(msec: Long) {
	    handleSeek(msec)
	}
	suspend fun setTopDir(topDir: MFile) {
	    handleSetTopDir(topDir)
	}
	fun switchContext() {
	    stopBroadcaster()
	    playingContextUuid = PlayContextStore.getPlayingUuid()
	    playingContext = PlayContextStore.find(playingContextUuid)
	    val path = playingContext?.path
	    if (path != null) {
		runBlocking {
		    handleJump(MFile(path))
		}
	    }
	    startBroadcaster()
	}
	fun setOnPlayStatusBroadcastedListener(listener: OnPlayStatusBroadcastListener) {
	    onPlayStatusBroadcastListeners.put(listener, true)
	}
    }
    
    override fun onBind(intent: Intent): IBinder? {
	return Binder()
    }
    
    private var playing = false
    
    suspend private fun handlePlay() {
	Log.d("handlePlay")
	if (!playing) {
	    enterForeground()
	    requestAudioFocus()
	    startA2dpWatcher()
	    player.play()
	    playing = true
	}
    }
    
    private fun handleStop(block: Boolean) {
	Log.d("handleStop")
	if (playing) {
	    player.stop()
	    stopA2dpWatcher()
	    abandonAudioFocus()
	    leaveForeground()
	    playing = false
	    
	    saveContext(block)
	}
    }
    
    suspend private fun handleJump(path: MFile) {
	player.jumpTo(path, 0)
    }

    suspend private fun handlePrev() {
	player.gotoPrev()
    }
    
    suspend private fun handleNext() {
	player.gotoNext()
    }

    private fun handleSeek(msec: Long) {
	player.seekTo(msec)
    }
    
    suspend private fun handleSetTopDir(topDir: MFile) {
	player.setTopDir(topDir)
    }
    
    private fun saveContext(block: Boolean) {
	Log.d("saveContext")
	if (block) {
	    runBlocking {
		saveContext_Body()
	    }
	} else {
	    scope.launch {
		saveContext_Body()
	    }
	}
    }
    
    suspend private fun saveContext_Body() {
	Log.d("get status")
	val playStatus = player.getPlayStatus()
	Log.d("get status done")
	if (playStatus.file != null) {
	    playingContext.topDir = playStatus.topDir.toString()
	    playingContext.path = playStatus.file.toString()
	    playingContext.msec = playStatus.msec
	    Log.d("save!")
	    PlayContextStore.save(true)
	    Log.d("save done!")
	}
    }
    
    private fun enterForeground() {
	val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
	val id = "playing"
	val name = "再生中"
	
	if (manager.getNotificationChannel(id) == null) {
	    val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
	    manager.createNotificationChannel(channel)
	}
	
	val pendingIntent = Intent(this, MainActivity::class.java).let { i ->
	    PendingIntent.getActivity(this, 0, i, 0)
	}
	
	val notification = NotificationCompat.Builder(this, id).apply {
	    setContentTitle("再生中")
	    setContentText("Playing...")
	    setSmallIcon(R.drawable.ic_launcher_background)
	    setContentIntent(pendingIntent)
	}.build()
	
	startForeground(1, notification)
    }
    
    private fun leaveForeground() {
	stopForeground(true)
    }
    
    
    private fun requestAudioFocus() {
	audioManager.requestAudioFocus(audioFocusRequest)
    }
    
    private fun abandonAudioFocus() {
	audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    private var a2dpWatcher: Thread? = null
    private fun startA2dpWatcher() {
	val adapter = BluetoothAdapter.getDefaultAdapter()
	if (adapter == null)
	    return
	val watcher = Thread { ->
	    try {
		var connected = false
		while (true) {
		    var newConnected: Boolean? = null
		    if (!adapter.isEnabled)
			newConnected = false
		    else {
			val state = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
			when (state) {
			    BluetoothProfile.STATE_DISCONNECTED,
			    BluetoothProfile.STATE_DISCONNECTING -> newConnected = false
			    BluetoothProfile.STATE_CONNECTED,
			    BluetoothProfile.STATE_CONNECTING -> newConnected = true
			}
		    }

		    // Log.d("connected=${connected}, newConnected = ${newConnected}")
		    if (newConnected != null) {
			if (connected && !newConnected) {
			    scope.launch {
				withContext(Dispatchers.Main) {
				    handleStop(false)
				}
			    }
			}
			connected = newConnected
		    }

		    Thread.sleep(500)
		}
	    } catch (e: InterruptedException) {
	    }
	}
	watcher.start()
	a2dpWatcher = watcher
    }
    
    private fun stopA2dpWatcher() {
	Log.d("a2dpWatcher=${a2dpWatcher}")
	val watcher = a2dpWatcher
	if (watcher != null) {
	    a2dpWatcher = null
	    watcher.interrupt()
	    watcher.join()
	}
    }
}
