/*
    Context Player 10 - Music Player with Context
    Copyright (C) 2021 Yuuki Harano

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package me.masm11.contextplayer10

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.AudioAttributes
import android.net.Uri
import android.widget.Toast

import java.io.File
import java.util.Locale
import java.util.Collections
import java.util.Arrays

class Player(val context: Context, val scope: CoroutineScope, val audioAttributes: AudioAttributes) {
    class MFilePlayer(val player: MediaPlayer, val file: MFile)
    
    private val mutex = Mutex()
    
    private var topDir = MFile("//")
    private var current: MFilePlayer? = null
    private var next: MFilePlayer? = null
    
    private var audioSessionId: Int = 0
    private var volume: Double = 1.0
    
    fun initialize() {
	Log.d("initialize")
	val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
	audioSessionId = manager.generateAudioSessionId()
    }
    
    suspend fun setTopDir(dir: MFile) {
	Log.d("setTopDir: ${dir}")
	topDir = dir
	// 次の曲が変わるかもしれないので、enqueue しなおす
	enqueueNextMediaPlayer();
    }
    
    suspend fun jumpTo(file: MFile, msec: Long) {
	Log.d("jumpTo: ${file}, ${msec}")
	mutex.withLock {
	    val mp_orig = current
	    var playing = (mp_orig != null && mp_orig.player.isPlaying())
	    
	    val mp = createMFilePlayerNextOf(file, true, false)
	    if (mp == null)
		return
	    
	    if (mp_orig != null) {
		mp_orig.player.stop()
		mp_orig.player.release()
		Log.d("jumpTo: mp_orig=${mp_orig} release")
	    }
	    
	    mp.player.seekTo(msec.toInt())
	    
	    if (playing)
		mp.player.start()
	    current = mp
	    Log.d("jumpTo: current.file=${current?.file}")
	    if (playing)
		enqueueNextMediaPlayer()
	}
    }
    
    suspend fun play() {
	Log.d("play")
	var mp = current
	if (mp != null) {
	    mp.player.start()
	    Log.d("play: started")
	}
	
	enqueueNextMediaPlayer()
    }
    
    fun stop() {
	Log.d("stop")
	val mp = current
	if (mp != null) {
	    mp.player.pause()
	    Log.d("stop: paused")
	}
	
	dequeueNextMediaPlayer()
    }
    
    suspend fun gotoPrev() {
	Log.d("gotoPrev")
	mutex.withLock {
	    val mp_orig = current
	    if (mp_orig != null && mp_orig.player.getCurrentPosition() >= 3_000) {
		mp_orig.player.seekTo(0)
		return
	    }
	    var playing = (mp_orig != null && mp_orig.player.isPlaying())
	    
	    val mp = createMFilePlayerNextOf(mp_orig?.file, false, true)
	    if (mp == null)
		return
	    
	    if (mp_orig != null) {
		mp_orig.player.stop()
		mp_orig.player.release()
		Log.d("gotoPrev: mp_orig=${mp_orig} release")
	    }
	    
	    if (playing)
		mp.player.start()
	    current = mp
	    Log.d("gotoPrev: current.file=${current?.file}")
	}
	enqueueNextMediaPlayer()
    }
    
    suspend fun gotoNext() {
	Log.d("gotoNext")
	mutex.withLock {
	    val mp_orig = current
	    var playing = (mp_orig != null && mp_orig.player.isPlaying())
	    
	    val mp = createMFilePlayerNextOf(mp_orig?.file, false, false)
	    if (mp == null)
		return
	    
	    if (mp_orig != null) {
		mp_orig.player.stop()
		mp_orig.player.release()
		Log.d("gotoNext: mp_orig=${mp_orig} release")
	    }
	    
	    if (playing)
		mp.player.start()
	    current = mp
	    Log.d("gotoNext: current.file=${current?.file}")
	}
	enqueueNextMediaPlayer()
    }
    
    fun seekTo(msec: Long) {
	Log.d("seekTo: ${msec}")
	val mp = current
	if (mp != null) {
	    Log.d("seekTo: msec=${msec}")
	    mp.player.seekTo(msec, MediaPlayer.SEEK_PREVIOUS_SYNC)
	}
    }
    
    fun setVolume(volume: Double) {
	Log.d("setVolume: ${volume}")
	var v = volume
	if (v < 0.0)
	    v = 0.0
	if (v > 1.0)
	    v = 1.0
	this.volume = v
	
	changeVolume()
    }
    
    data class PlayStatus(val topDir: MFile, val file: MFile?, val duration: Long, val msec: Long)
    
    suspend fun getPlayStatus(): PlayStatus {
	mutex.withLock {
	    val mp = current
	    var duration: Long = 0
	    var msec: Long = 0
	    if (mp != null) {
		duration = mp.player.getDuration().toLong()
		msec = mp.player.getCurrentPosition().toLong()
	    }
	    return PlayStatus(topDir, mp?.file, duration, msec)
	}
    }
    
    private fun calcVolume(): Double {
	Log.d("calcVolume")
	var vol = volume
	return vol
    }
    
    private fun changeVolume() {
	Log.d("changeVolume")
	val vol = calcVolume()
	var mp = current
	if (mp != null)
	    mp.player.setVolume(vol.toFloat(), vol.toFloat())
	mp = next
	if (mp != null)
	    mp.player.setVolume(vol.toFloat(), vol.toFloat())
    }
    
    suspend private fun enqueueNextMediaPlayer() {
	Log.d("enqueueNextMediaPlayer: 0")
	dequeueNextMediaPlayer()
	
	Log.d("enqueueNextMediaPlayer: 1")
	val mp_orig = current
	if (mp_orig == null)
	    return
	Log.d("enqueueNextMediaPlayer: 2 create")
	val mp = createMFilePlayerNextOf(mp_orig.file, false, false)
	if (mp != null) {
	    Log.d("enqueueNextMediaPlayer: 3 set next")
	    mp_orig.player.setNextMediaPlayer(mp.player)
	    next = mp
	}
    }
    
    private fun dequeueNextMediaPlayer() {
	Log.d("dequeueNextMediaPlayer")
	val mp = next
	if (mp != null) {
	    mp.player.release()
	    Log.d("mp=${mp} release")
	    next = null
	}
    }
    
    suspend private fun createMFilePlayerNextOf(file0: MFile?, includeFile: Boolean, backward: Boolean): MFilePlayer? {
	Log.d("createMFilePlayerNextOf: ${file0}, ${includeFile}, ${backward}")
	var file = file0
	
	if (!includeFile) {
	    if (!backward)
		file = MFile.selectNext(file, topDir)
	    else
		file = MFile.selectPrev(file, topDir)
	}
	
	while (true) {
	    Log.d("createMFilePlayerNextOf: file=${file}")
	    if (file == null)
		return null
	    
	    val p = createMediaPlayer(file)
	    if (p != null)
		return MFilePlayer(p, file)
	    
	    if (!backward)
		file = MFile.selectNext(file, topDir)
	    else
		file = MFile.selectPrev(file, topDir)
	}
    }
    
    suspend private fun createMediaPlayer(file: MFile): MediaPlayer? {
	Log.d("createMediaPlayer: ${file}")
	return withContext(Dispatchers.IO) {
	    val uri = Uri.fromFile(file.file)
	    val mp = MediaPlayer.create(context, uri, null, audioAttributes, audioSessionId)
	    Log.d("createMediaPlayer: mp=${mp} alloc")
	    if (mp != null) {
		mp.setOnCompletionListener { p ->
		    Log.d("completed ${p}")
		    scope.launch {
			handleCompletion(p)
		    }
		}
		mp.setOnErrorListener { p, what, extra ->
		    val what_str = when (what) {
			MediaPlayer.MEDIA_ERROR_UNKNOWN -> "unknown"
			MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "server_died"
			else -> "???"
		    }
		    val extra_str = when (extra) {
			MediaPlayer.MEDIA_ERROR_IO -> "io"
			MediaPlayer.MEDIA_ERROR_MALFORMED -> "malformed"
			MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> "unsupported"
			MediaPlayer.MEDIA_ERROR_TIMED_OUT -> "timeout"
			// MediaPlayer.MEDIA_ERROR_SYSTEM -> "system"
			else -> "???"
		    }

		    val detail = "what=${what}(${what_str}), extra=${extra}(${extra_str})"
		    Log.d("error ${p}, ${detail}")
		    Toast.makeText(context, detail, Toast.LENGTH_LONG).show()
		    scope.launch {
			handleError(p)
		    }
		    true
		}
		val vol = calcVolume()
		mp.setVolume(vol.toFloat(), vol.toFloat())
	    }
	    mp
	}
    }
    
    suspend private fun handleCompletion(mp: MediaPlayer) {
	mutex.withLock {
	    mp.release()
	    Log.d("handleCompletion: mp=${mp} release")
	    
	    if (mp != current?.player)
		return
	    
	    if (next != null) {
		current = next
		Log.d("handleCompletion: current.file=${current?.file}")
		next = null
	    }
	    
	    enqueueNextMediaPlayer()
	}
    }
    
    suspend private fun handleError(mp_orig: MediaPlayer) {
	mutex.withLock {
	    val playing = mp_orig.isPlaying()
	    
	    mp_orig.release()
	    Log.d("handleError: mp=${mp_orig} release")
	    
	    if (mp_orig != current?.player)
		return
	    if (!playing)
		return
	    
	    val mp = createMFilePlayerNextOf(current?.file, false, false)
	    if (mp == null)
		return
	    
	    mp.player.start()
	    current = mp
	    Log.d("handleError: current.file=${current?.file}")
	    
	    enqueueNextMediaPlayer()
	}
    }


    companion object {
	/* dir に含まれるファイル名をリストアップする。
	* '.' で始まるものは含まない。
	* ソートされている。
	*/
	fun listFiles(dir: MFile, reverse: Boolean): Array<MFile> {
	    // Log.d("listFiles: dir: ${dir}")
	    val files = dir.listFiles { f -> !f.name.startsWith(".") }
	    // Log.d("listFiles: files: ${files}")
	    if (files == null)
		return emptyArray<MFile>()
	    // Log.d("listFiles: files: not empty.")

	    var comparator = Comparator<MFile> { o1, o2 ->
		val name1 = o1.name.toLowerCase(Locale.getDefault())
		val name2 = o2.name.toLowerCase(Locale.getDefault())
		// まず、大文字小文字を無視して比較
		var r = name1.compareTo(name2)
		// もしそれで同じだったら、区別して比較
		if (r == 0)
		    r = o1.compareTo(o2)
		r
	    }
	    if (reverse)
		comparator = Collections.reverseOrder(comparator)
	    Arrays.sort(files, comparator)

	    return files
	}
    }
}
