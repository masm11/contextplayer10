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
