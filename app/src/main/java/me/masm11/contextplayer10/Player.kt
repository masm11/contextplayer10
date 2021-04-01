package me.masm11.contextplayer10

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioManager
import android.media.AudioAttributes
import android.net.Uri

import java.io.File
import java.util.Locale
import java.util.Collections
import java.util.Arrays

class Player(val context: Context, val scope: CoroutineScope, val audioAttributes: AudioAttributes) {
    private val mutex = Mutex()
    
    private var topDir = MFile("//")
    private var currentMediaPlayer: MediaPlayer? = null
    private var nextMediaPlayer: MediaPlayer? = null
    private var currentMFile: MFile? = null
    private var nextMFile: MFile? = null
    
    private var audioSessionId: Int = 0
    private var volume: Double = 1.0
    
    fun initialize() {
	val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
	audioSessionId = manager.generateAudioSessionId()
    }
    
    suspend fun setTopDir(dir: MFile) {
	topDir = dir
	// 次の曲が変わるかもしれないので、enqueue しなおす
	enqueueNextMediaPlayer();
    }
    
    suspend fun jumpTo(file: MFile, msec: Long) {
	mutex.withLock {
	    val mp_orig = currentMediaPlayer
	    var curr: MFile? = file
	    var playing = (mp_orig != null && mp_orig.isPlaying())
	    
	    while (true) {
		Log.d("curr=${curr}")
		if (curr == null)
		    break
		
		val mp = createMediaPlayer(curr)
		if (mp == null) {
		    curr = MFile.selectNext(curr, topDir)
		    continue
		}
		
		if (mp_orig != null) {
		    mp_orig.stop()
		    mp_orig.release()
		    Log.d("mp_orig=${mp_orig} release")
		}
		
		if (playing)
		    mp.start()
		currentMediaPlayer = mp
		currentMFile = curr
		Log.d("1currentMFile=${currentMFile}")
		break
	    }
	}
    }
    
    suspend fun play() {
	var mp = currentMediaPlayer
	if (mp != null) {
	    mp.start()
	    Log.d("started")
	}
	
	enqueueNextMediaPlayer()
    }
    
    fun stop() {
	val mp = currentMediaPlayer
	if (mp != null) {
	    mp.pause()
	    Log.d("paused")
	}
	
	dequeueNextMediaPlayer()
    }
    
    suspend fun gotoPrev() {
	mutex.withLock {
	    val mp_orig = currentMediaPlayer
	    if (mp_orig != null && mp_orig.getCurrentPosition() >= 3_000) {
		mp_orig.seekTo(0)
		return
	    }
	    var curr = currentMFile
	    var playing = (mp_orig != null && mp_orig.isPlaying())
	    
	    while (true) {
		curr = MFile.selectPrev(curr, topDir)
		
		Log.d("curr=${curr}")
		if (curr == null)
		    break
		
		val mp = createMediaPlayer(curr)
		if (mp == null)
		    continue
		
		if (mp_orig != null) {
		    mp_orig.stop()
		    mp_orig.release()
		    Log.d("mp_orig=${mp_orig} release")
		}
		
		if (playing)
		    mp.start()
		currentMediaPlayer = mp
		currentMFile = curr
		Log.d("2currentMFile=${currentMFile}")
		break
	    }
	}
	enqueueNextMediaPlayer()
    }

    suspend fun gotoNext() {
	mutex.withLock {
	    val mp_orig = currentMediaPlayer
	    var curr = currentMFile
	    var playing = (mp_orig != null && mp_orig.isPlaying())
	    
	    while (true) {
		Log.d("before curr=${curr}")
		curr = MFile.selectNext(curr, topDir)
		
		Log.d("after curr=${curr}")
		if (curr == null)
		    break
		
		val mp = createMediaPlayer(curr)
		if (mp == null)
		    continue
		
		if (mp_orig != null) {
		    mp_orig.stop()
		    mp_orig.release()
		    Log.d("mp_orig=${mp_orig} release")
		}
		
		if (playing)
		    mp.start()
		currentMediaPlayer = mp
		currentMFile = curr
		Log.d("3currentMFile=${currentMFile}")
		break
	    }
	}
	enqueueNextMediaPlayer()
    }

    fun seekTo(msec: Long) {
	val mp = currentMediaPlayer
	if (mp != null) {
	    Log.d("seekTo ${msec}")
	    mp.seekTo(msec, MediaPlayer.SEEK_PREVIOUS_SYNC)
	}
    }

    fun setVolume(volume: Double) {
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
	    val mp = currentMediaPlayer
	    var duration: Long = 0
	    var msec: Long = 0
	    if (mp != null) {
		duration = mp.getDuration().toLong()
		msec = mp.getCurrentPosition().toLong()
	    }
	    return PlayStatus(topDir, currentMFile, duration, msec)
	}
    }
    
    private fun calcVolume(): Double {
	var vol = volume
	return vol
    }
    
    private fun changeVolume() {
	val vol = calcVolume()
	var mp = currentMediaPlayer
	if (mp != null)
	    mp.setVolume(vol.toFloat(), vol.toFloat())
	mp = nextMediaPlayer
	if (mp != null)
	    mp.setVolume(vol.toFloat(), vol.toFloat())
    }
    
    suspend private fun enqueueNextMediaPlayer() {
	Log.d("enqueue")
	val currMFile = currentMFile
	
	dequeueNextMediaPlayer()
	
	var next = currMFile
	while (true) {
	    next = MFile.selectNext(next, topDir)
	    Log.d("next=${next}")
	    if (next == null)
		break
	    
	    val mp = createMediaPlayer(next)
	    if (mp == null)
		continue
	    
	    val currPlayer = currentMediaPlayer
	    if (currPlayer == null)
		break
	    
	    currPlayer.setNextMediaPlayer(mp)
	    nextMediaPlayer = mp
	    nextMFile = next
	    break
	}
    }
    
    private fun dequeueNextMediaPlayer() {
	Log.d("dequeue")
	val mp = nextMediaPlayer
	if (mp != null) {
	    mp.release()
	    Log.d("mp=${mp} release")
	    nextMediaPlayer = null
	    nextMFile = null
	}
    }

    suspend private fun createMediaPlayer(file: MFile): MediaPlayer? {
	return withContext(Dispatchers.IO) {
	    val uri = Uri.fromFile(file.file)
	    val mp = MediaPlayer.create(context, uri, null, audioAttributes, audioSessionId)
	    Log.d("mp=${mp} alloc")
	    if (mp != null) {
		mp.setOnCompletionListener { p ->
		    Log.d("completed ${p}")
		    scope.launch {
			handleCompletion(p)
		    }
		}
		mp.setOnErrorListener { p, _, _ ->
		    Log.d("error ${p}")
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
	    Log.d("mp=${mp} release")
	    
	    if (mp != currentMediaPlayer)
		return
	    
	    if (nextMediaPlayer != null) {
		currentMediaPlayer = nextMediaPlayer
		currentMFile = nextMFile
		Log.d("4currentMFile=${currentMFile}")
		nextMediaPlayer = null
		nextMFile = null
	    }
	    
	    enqueueNextMediaPlayer()
	}
    }
    
    suspend private fun handleError(mp_orig: MediaPlayer) {
	mutex.withLock {
	    mp_orig.release()
	    Log.d("mp=${mp_orig} release")
	    
	    if (mp_orig != currentMediaPlayer)
		return
	    
	    var curr = currentMFile
	    while (true) {
		Log.d("before curr=${curr}")
		curr = MFile.selectNext(curr, topDir)
		
		Log.d("after curr=${curr}")
		if (curr == null)
		    break
		
		val mp = createMediaPlayer(curr)
		if (mp == null)
		    continue
		
		mp.start()
		currentMediaPlayer = mp
		currentMFile = curr
		Log.d("3currentMFile=${currentMFile}")
		break
	    }
	    
	    enqueueNextMediaPlayer()
	}
    }


    companion object {
	/* dir に含まれるファイル名をリストアップする。
	* '.' で始まるものは含まない。
	* ソートされている。
	*/
	fun listFiles(dir: MFile, reverse: Boolean): Array<MFile> {
	    Log.d("listFiles: dir: ${dir}")
	    val files = dir.listFiles { f -> !f.name.startsWith(".") }
	    Log.d("listFiles: files: ${files}")
	    if (files == null)
		return emptyArray<MFile>()
	    Log.d("listFiles: files: not empty.")

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
