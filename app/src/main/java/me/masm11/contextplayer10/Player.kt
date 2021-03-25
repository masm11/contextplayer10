package me.masm11.contextplayer10

import kotlinx.coroutines.*

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioManager
import android.net.Uri

import java.io.File

class Player(val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    
    suspend fun play(file: MFile) {
	withContext(Dispatchers.IO) {
	    val uri = Uri.fromFile(file.file)
	    val mp = MediaPlayer.create(context, uri)
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
