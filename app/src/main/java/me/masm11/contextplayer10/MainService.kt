package me.masm11.contextplayer10

import androidx.appcompat.app.AppCompatActivity
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

import kotlinx.coroutines.*
import android.media.MediaPlayer
import android.media.AudioManager
import android.net.Uri
import java.io.File

class MainService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    
    override fun onCreate() {
        super.onCreate()
    }
    
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
	return START_STICKY
    }
    
    inner class Binder: android.os.Binder() {
	suspend fun play() {
	    val uri = Uri.fromFile(File("/sdcard/Music/nainai/bgm/nainai_bgm2_m00.ogg"))
	    scope.launch {
		withContext(Dispatchers.IO) {
		    val mp = MediaPlayer.create(this@MainService, uri)
		    Log.d("mp=${mp}")
		    if (mp != null) {
			Log.d("mp is not null")
			Log.d("start")
			mp.start()
			Log.d("started")
		    }
		    mediaPlayer = mp
		}
	    }
	}
    }
    
    override fun onBind(intent: Intent): IBinder? {
	return Binder()
    }
}
