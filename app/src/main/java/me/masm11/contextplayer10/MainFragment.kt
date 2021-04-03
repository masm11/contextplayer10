package me.masm11.contextplayer10

import androidx.fragment.app.Fragment
import androidx.constraintlayout.widget.ConstraintLayout
import android.os.Bundle
import android.os.IBinder
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.widget.SeekBar
import android.widget.Button
import android.widget.TextView
import android.widget.Space
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import kotlinx.coroutines.*

class MainFragment: Fragment() {
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private var binder: MainService.Binder? = null
    private var userSeeking = false
    private lateinit var textView_contextName: TextView
    private lateinit var textView_path: TextView
    private lateinit var textView_title: TextView
    private lateinit var textView_artist: TextView
    private var cache_path: String? = null
    private var cache_title: String? = null
    private var cache_artist: String? = null
    
    // GC に破棄されないよう、変数に持っておく
    private val listener = object: MainService.OnPlayStatusBroadcastListener {
	override fun onPlayStatusBroadcastListener(playStatus: Player.PlayStatus) {
	    scope.launch {
		updateInfo(playStatus)
	    }
	}
    }
    
    private val conn = object: ServiceConnection {
	override fun onServiceConnected(name: ComponentName, binder: IBinder) {
	    val b = binder as MainService.Binder
	    this@MainFragment.binder = b
	    Log.d("connected")
	    
	    b.setOnPlayStatusBroadcastedListener(listener)
	}
	override fun onServiceDisconnected(name: ComponentName) {
	    this@MainFragment.binder = null
	}
    }
    
/*
    override fun onCreate() {
    }
*/
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
	return inflater.inflate(R.layout.main_fragment, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
	textView_contextName = view.findViewById<TextView>(R.id.context_name)
	textView_path = view.findViewById<TextView>(R.id.path)
	textView_title = view.findViewById<TextView>(R.id.title)
	textView_artist = view.findViewById<TextView>(R.id.artist)

	view.findViewById<View>(R.id.context_name).setOnClickListener {
	    Log.d("click on title")
	    val ctxt = getContext() as MainActivity
	    ctxt.switchToContext()
	}
	view.findViewById<View>(R.id.track_info).setOnClickListener {
	    Log.d("click on title")
	    val ctxt = getContext() as MainActivity
	    ctxt.switchToExplorer()
	}
    }
    
    override fun onStart() {
	super.onStart()
	val ctxt = getContext()
	if (ctxt != null) {
	    val intent = Intent(ctxt, MainService::class.java)
	    ctxt.bindService(intent, conn, Context.BIND_AUTO_CREATE)
	}
	cache_path = null
	cache_title = null
	cache_artist = null
    }
    
    override fun onStop() {
	super.onStop()
	val ctxt = getContext()
	if (ctxt != null)
	    ctxt.unbindService(conn)
    }
    
    suspend private fun updateInfo(playStatus: Player.PlayStatus) {
	val file = playStatus.file
	if (file.toString() != cache_path) {
	    cache_path = file.toString()
	    val metadata = Metadata(file?.file.toString())
	    if (metadata.extract()) {
		cache_path = playStatus.file.toString()
		cache_title = metadata.title
		cache_artist = metadata.artist
	    } else {
		cache_path = playStatus.file.toString()
		cache_title = null
		cache_artist = null
	    }
	    if (cache_title == null)
		cache_title = "不明なタイトル"
	    if (cache_artist == null)
		cache_artist = "不明なアーティスト"
	    textView_path.setText(cache_path)
	    textView_title.setText(cache_title)
	    textView_artist.setText(cache_artist)
	}
    }
}
