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

class Player(val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var nextMediaPlayer: MediaPlayer? = null
    
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
	
	withContext(Dispatchers.IO) {
	    val nextMFile = MFile.selectNext(file, MFile("//"))
	    Log.d("nextMFile=${nextMFile}")
	    if (nextMFile != null) {
		val uri = Uri.fromFile(nextMFile.file)
		val mp = MediaPlayer.create(context, uri)
		Log.d("mp=${mp}")
		if (mp != null) {
		    Log.d("mp is not null")
		    Log.d("set next")
		    val prim = mediaPlayer
		    if (prim != null) {
			prim.setNextMediaPlayer(mp)
			Log.d("set next done")
		    }
		}
		nextMediaPlayer = mp
	    }
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
