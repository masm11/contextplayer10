package me.masm11.contextplayer10

import androidx.appcompat.app.AppCompatActivity
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

import kotlinx.coroutines.*
import android.media.AudioManager
import android.net.Uri
import java.io.File

class MainService : Service() {
    private var audioManager: AudioManager? = null
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private val player = Player(this, scope)
    
    override fun onCreate() {
	scope.launch {
	    player.setTopDir(MFile("//primary/nana/impact_exciter/"))
	    player.jumpTo(MFile("//primary/nana/impact_exciter/nana_ie_16.ogg"), 200000)
	}
	super.onCreate()
    }
    
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
	return START_STICKY
    }
    
    inner class Binder: android.os.Binder() {
	suspend fun play() {
	    player.play()
	}
	suspend fun stop() {
	    player.stop()
	}
    }
    
    override fun onBind(intent: Intent): IBinder? {
	return Binder()
    }
}
