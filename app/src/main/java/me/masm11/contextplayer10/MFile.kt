/* Context Player - Audio Player with Contexts
    Copyright (C) 2016, 2018 Yuuki Harano

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package me.masm11.contextplayer10

import java.io.File
import java.util.Locale

import android.os.Environment
import android.content.Context

// Mapped file
class MFile(val path: String) {
    constructor(file: File) : this(file.toString())

    companion object {
	val mapping: HashMap<String, String> = HashMap()
	fun initMapping() {
	    if (mapping.size == 0) {
		/* 他のディレクトリに大量のファイルがあると、無駄に処理を食ってしまうので、
		* Music ディレクトリだけ扱う。
		*/

//		mapping.put("primary", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).canonicalPath)
		mapping.put("primary", "/sdcard/Music")

		/* 機種によって違うだろうけど、まずは私の機種に対応。
		* /storage/<uuid>/Music を使う。
		*/
		val dirs = File("/storage").listFiles()
		if (dirs != null) {
		    for (dir in dirs) {
			val name = dir.name
			if (!name.matches("[0-9a-fA-F\\-]+-[0-9a-fA-F\\-]+".toRegex()))
		            continue
			mapping.put(name, "/storage/${name}/Music")
		    }
		}
	    }
	}

	suspend fun selectNext(mNextOf: MFile?, mTopDir: MFile): MFile? {	// m means MFile, not member.
	    if (mNextOf == null)
		return null;
	    val nextOf = mNextOf.toString()
	    val topDir = mTopDir.toString()
	    Log.d("nextOf=${nextOf}")
	    var found: String? = null
	    if (nextOf.startsWith(topDir)) {
		if (topDir != "//") {
		    //                           +1: for '/'   ↓
		    val parts = nextOf.substring(topDir.length + 1).split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
		    found = lookForFile(MFile(topDir), parts, 0, false)
		} else {
		    val parts = nextOf.substring(2).split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
		    found = lookForFile(MFile(topDir), parts, 0, false)
		}
	    }
	    if (found == null)
		found = lookForFile(MFile(topDir), null, 0, false)
	    Log.d("found=${found}")
	    return if (found != null) MFile(found) else null
	}

	suspend fun selectPrev(mPrevOf: MFile?, mTopDir: MFile): MFile? {
	    if (mPrevOf == null)
		return null;
	    val prevOf = mPrevOf.toString()
	    val topDir = mTopDir.toString()
	    Log.d("prevOf=${prevOf}")
	    var found: String? = null
	    if (prevOf.startsWith(topDir)) {
		if (topDir != "//") {
		    //                            +1: for '/'  ↓
		    val parts = prevOf.substring(topDir.length + 1).split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
		    found = lookForFile(MFile(topDir), parts, 0, true)
		} else {
		    val parts = prevOf.substring(2).split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
		    found = lookForFile(MFile(topDir), parts, 0, true)
		}
	    }
	    if (found == null)
		found = lookForFile(MFile(topDir), null, 0, true)
	    Log.d("found=${found}")
	    return if (found != null) MFile(found) else null
	}

	/* 次のファイルを探す。
	*   dir: 今見ているディレクトリ
	*   parts[]: topdir からの相対 path。'/' で区切られている。
	*   parts_idx: ディレクトリの nest。
	*   backward: 逆向き検索。
	* 最初にこのメソッドに来る時、nextOf は、
	*   /dir/parts[0]/parts[1]/…/parts[N]
	* だったことになる。
	* lookForFile() の役割は、dir 内 subdir も含めて、nextOf の次のファイルを探すこと。
	* parts == null の場合、nextOf の path tree から外れた場所を探している。
	*/
	suspend private fun lookForFile(dir: MFile, parts: Array<String>?, parts_idx: Int, backward: Boolean): String? {
	    var cur: String? = null
	    if (parts != null) {
		if (parts_idx < parts.size)
		    cur = parts[parts_idx]
	    }

	    val files = Player.listFiles(dir, backward)

	    for (file in files) {
		if (cur == null) {
		    if (file.isDirectory) {
			val r = lookForFile(file, null, parts_idx + 1, backward)
			if (r != null)
			    return r
		    } else {
			return file.absolutePath
		    }
		} else {
		    val compare = comparePath(file.name, cur)
		    if (compare == 0) {
			// 今そこ。
			if (file.isDirectory) {
			    val r = lookForFile(file, parts, parts_idx + 1, backward)
			    if (r != null)
				return r
			} else {
			    // これは今再生中。
			}
		    } else if (!backward && compare > 0) {
			if (file.isDirectory) {
			    // 次を探していたら dir だった
			    val r = lookForFile(file, null, parts_idx + 1, backward)
			    if (r != null)
				return r
			} else {
			    // 次のファイルを見つけた
			    return file.absolutePath
			}
		    } else if (backward && compare < 0) {
			if (file.isDirectory) {
			    // 次を探していたら dir だった
			    val r = lookForFile(file, null, parts_idx + 1, backward)
			    if (r != null)
				return r
			} else {
			    // 次のファイルを見つけた
			    return file.absolutePath
			}
		    }
		}
	    }
	    
	    return null
	}
	
	private fun comparePath(p1: String, p2: String): Int {
	    val l1 = p1.toLowerCase(Locale.getDefault())
	    val l2 = p2.toLowerCase(Locale.getDefault())
	    var r = l1.compareTo(l2)
	    if (r == 0)
		r = p1.compareTo(p2)
	    return r
	}
    }

    init {
	if (!path.startsWith("//"))
	    throw RuntimeException("path not start with //: ${path}")
	initMapping()
    }
    
    override fun equals(other: Any?): Boolean {
	if (other == null)
	    return false
	if (other !is MFile)
	    return false
	return path == other.path
    }

    val isDirectory: Boolean
    get() {
	return file.isDirectory
    }

    val absolutePath: String
    get() {
	return path
    }
    
    val name: String
    get() {
	val i = path.lastIndexOf('/')
	return path.substring(i + 1)
    }

    override fun toString(): String {
	return absolutePath;
    }

    val file: File
    get() {
	Log.d("path=\"${path}\"")
	if (path == "//")
	    return File("/")
	val i = path.indexOf('/', 2)
	if (i == -1) {
	    val storageId = path.substring(2)
	    val f = mapping.get(storageId)
	    if (f != null)
	        return File(f)
	    return File("/")
	} else {
	    val storageId = path.substring(2, i)
	    val f = mapping.get(storageId)
	    if (f != null)
	        return File(f, path.substring(i + 1))
	    return File("/")
	}
    }

    fun listFiles(filter: ((MFile) -> Boolean)? = null): Array<MFile>? {
	Log.d("path=\"${path}\"")
	if (path == "//") {
	    Log.d("is root.");
	    return mapping.keys.map<String, MFile>{ s ->
		Log.d("s=\"${s}\"");
		MFile("//" + s)
	    }.filter{ f ->
	        if (filter != null) filter(f) else true
	    }.toTypedArray()
	} else {
	    val files = file.listFiles()
	    if (files == null) {
		Log.d("files is null.");
	        return null
	    }
	    return files.map<File, MFile>{ f ->
		Log.d("f=\"${f}\"");
		Log.d("new=\"${path + "/" + f.name}\"");
		MFile(path + "/" + f.name)
	    }.filter{ f ->
	        if (filter != null) filter(f) else true
	    }.toTypedArray()
	}
    }

    val parentFile: MFile
    get() {
	if (path == "//")
	    return this
	val i = path.lastIndexOf('/')
	if (i >= 2)
	    return MFile(path.substring(0, i))
	return MFile("//")
    }

    fun compareTo(file: MFile): Int {
	return this.absolutePath.compareTo(file.absolutePath)
    }
}
