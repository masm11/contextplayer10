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
import android.net.Uri
import java.io.File

class MainService : Service() {
    private var audioManager: AudioManager? = null
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private val player = Player(this, scope)
    
    override fun onCreate() {
	super.onCreate()
	
	scope.launch {
	    player.setTopDir(MFile("//primary/nana/impact_exciter/"))
	    player.jumpTo(MFile("//primary/nana/impact_exciter/nana_ie_16.ogg"), 200000)
	}
	
	startBroadcaster()
    }
    
    override fun onDestroy() {
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
    }
    
    suspend private fun handleStop() {
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
    
}
