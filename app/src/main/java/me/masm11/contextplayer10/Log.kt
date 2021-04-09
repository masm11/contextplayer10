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

import android.content.Context

import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date

class Log {
    companion object {
	private val TAG = "contextplayer10";
	private lateinit var writer: FileWriter
	private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")

	fun init(context: Context) {
	    val file = File(context.getExternalFilesDir(null), "log.txt")
	    writer = FileWriter(file, true)
	    i("START ----------------")
	}
	fun d(str: String) {
	    android.util.Log.d(TAG, str)
	    write("D", str)
	}
	fun i(str: String) {
	    android.util.Log.i(TAG, str)
	    write("I", str)
	}
	fun w(str: String) {
	    android.util.Log.w(TAG, str)
	    write("W", str)
	}
	fun w(str: String, e: Throwable) {
	    android.util.Log.w(TAG, str, e)
	    write("W", "${str} ${e.toString()}")
	}
	fun e(str: String, e: Throwable) {
	    android.util.Log.e(TAG, str, e)
	    write("E", "${str} ${e.toString()}")
	}

	private fun write(level: String, str: String) {
	    val stamp = sdf.format(Date())
	    val s = "${stamp} ${level}: ${str}\n"
	    writer.write(s)
	    writer.flush()
	}
    }
}
