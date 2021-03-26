package me.masm11.contextplayer10

import kotlinx.coroutines.*

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioManager
import android.net.Uri

import java.io.File
import java.util.Locale
import java.util.Collections
import java.util.Arrays

class Player(val context: Context, val scope: CoroutineScope) {
    private var currentMediaPlayer: MediaPlayer? = null
    private var nextMediaPlayer: MediaPlayer? = null
    private var currentMFile: MFile? = null
    private var nextMFile: MFile? = null
    
    suspend fun play(file: MFile) {
	withContext(Dispatchers.IO) {
	    val mp = createMediaPlayer(file)
	    if (mp != null) {
		mp.start()
		Log.d("started")
		currentMediaPlayer = mp
		currentMFile = file
	    }
	}
	
	enqueueNextMediaPlayer()
    }
    
    suspend fun enqueueNextMediaPlayer() {
	val currMFile = currentMFile
	
	nextMediaPlayer = null
	nextMFile = null
	
	withContext(Dispatchers.IO) {
	    var next = currMFile
	    while (true) {
		next = MFile.selectNext(next, MFile("//"))		// fixme: topDir
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
    }
    
    suspend fun createMediaPlayer(file: MFile): MediaPlayer? {
	return withContext(Dispatchers.IO) {
	    val uri = Uri.fromFile(file.file)
	    val mp = MediaPlayer.create(context, uri)
	    Log.d("mp=${mp}")
	    if (mp != null) {
		mp.setOnCompletionListener { p ->
		    scope.launch {
			handleCompletion(p)
		    }
		}
	    }
	    mp
	}
    }
    
    suspend fun handleCompletion(mp: MediaPlayer) {
	mp.release()
	
	if (mp != currentMediaPlayer)
	    return
	
	currentMediaPlayer = nextMediaPlayer
	currentMFile = nextMFile
	
	enqueueNextMediaPlayer()
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
