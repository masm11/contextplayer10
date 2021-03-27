package me.masm11.contextplayer10

import androidx.appcompat.app.AppCompatActivity
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat

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
		scope.launch {
		    handleStop()
		}
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
/*
	    player.setTopDir(MFile("//primary/nana/impact_exciter/"))
	    player.jumpTo(MFile("//primary/nana/impact_exciter/nana_ie_16.ogg"), 200000)
*/
	    if (playingContext.path == null) {
		playingContext.topDir = "//primary/nana/impact_exciter"
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
	abandonAudioFocus()
	stopBroadcaster()
	scope.cancel()
	super.onDestroy()
    }
    
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
	return START_STICKY
    }
    
    
    interface OnPlayStatusBroadcastListener {
	fun onPlayStatusBroadcastListener(playStatus: Player.PlayStatus)
    }
    private val onPlayStatusBroadcastListeners = WeakHashMap<OnPlayStatusBroadcastListener, Boolean>()
    private lateinit var broadcaster: Thread
    
    fun startBroadcaster() {
	broadcaster = Thread { ->
	    while (true) {
		scope.launch {
		    val playStatus = player.getPlayStatus()
		    
		    if (playStatus.file != null) {
			playingContext.topDir = playStatus.topDir.toString()
			playingContext.path = playStatus.file.toString()
			playingContext.msec = playStatus.msec
			PlayContextStore.save()
		    }

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
		Thread.sleep(100)
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
	suspend fun stop() {
	    handleStop()
	}
	fun setOnPlayStatusBroadcastedListener(listener: OnPlayStatusBroadcastListener) {
	    onPlayStatusBroadcastListeners.put(listener, true)
	}
    }
    
    override fun onBind(intent: Intent): IBinder? {
	return Binder()
    }
    
    
    suspend private fun handlePlay() {
	player.play()
	enterForeground()
	requestAudioFocus()
    }
    
    suspend private fun handleStop() {
	abandonAudioFocus()
	leaveForeground()
	player.stop()
    }
    
    
    private fun enterForeground() {
	val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
	val id = "playing"
	val name = "再生中"
	
	if (manager.getNotificationChannel(id) == null) {
	    val channel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_LOW)
	    manager.createNotificationChannel(channel)
	}
	
	val notification = NotificationCompat.Builder(this, id).apply {
	    setContentTitle("再生中")
	    setContentText("Playing...")
	    setSmallIcon(R.drawable.ic_launcher_background)
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
}
