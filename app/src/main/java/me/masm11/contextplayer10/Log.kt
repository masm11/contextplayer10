package me.masm11.contextplayer10

class Log {
    companion object {
	val TAG = "contextplayer10";
	fun d(str: String) {
	    android.util.Log.d(TAG, str)
	}
	fun i(str: String) {
	    android.util.Log.i(TAG, str)
	}
	fun w(str: String) {
	    android.util.Log.w(TAG, str)
	}
	fun e(str: String, e: Throwable) {
	    android.util.Log.e(TAG, str, e)
	}
    }
}
