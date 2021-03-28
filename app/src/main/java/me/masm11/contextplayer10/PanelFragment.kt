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

class PanelFragment: Fragment() {
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private var binder: MainService.Binder? = null
    private var userSeeking = false
    private lateinit var seekBar: SeekBar
    private lateinit var times: TextView
    private lateinit var left_of_times: Space
    private lateinit var right_of_times: Space
    
    // GC に破棄されないよう、変数に持っておく
    private val listener = object: MainService.OnPlayStatusBroadcastListener {
	override fun onPlayStatusBroadcastListener(playStatus: Player.PlayStatus) {
	    if (!userSeeking) {
		seekBar.setMax(playStatus.duration.toInt())
		seekBar.setProgress(playStatus.msec.toInt())
		
		val cur_sec = playStatus.msec / 1000
		val max_sec = playStatus.duration / 1000
		val cur_mm = cur_sec / 60
		val cur_ss = cur_sec % 60
		val max_mm = max_sec / 60
		val max_ss = max_sec % 60
		
		val text = String.format("%d:%02d/%d:%02d", cur_mm, cur_ss, max_mm, max_ss)
		times.setText(text)
		
		val left_params = left_of_times.getLayoutParams() as ConstraintLayout.LayoutParams
		left_params.horizontalWeight = playStatus.msec.toFloat()
		left_of_times.setLayoutParams(left_params)
		val right_params = right_of_times.getLayoutParams() as ConstraintLayout.LayoutParams
		right_params.horizontalWeight = (playStatus.duration - playStatus.msec).toFloat()
		right_of_times.setLayoutParams(right_params)
	    }
	}
    }
    
    private val conn = object: ServiceConnection {
	override fun onServiceConnected(name: ComponentName, binder: IBinder) {
	    val b = binder as MainService.Binder
	    this@PanelFragment.binder = b
	    Log.d("connected")
	    
	    b.setOnPlayStatusBroadcastedListener(listener)
	}
	override fun onServiceDisconnected(name: ComponentName) {
	    this@PanelFragment.binder = null
	}
    }
    
/*
    override fun onCreate() {
    }
*/

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
	return inflater.inflate(R.layout.panel_fragment, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
	view.findViewById<Button>(R.id.button_prev).apply {
	    setOnClickListener {
		val b = binder
		if (b != null) {
		    scope.launch {
			b.prev()
		    }
		}
	    }
	}
	
	view.findViewById<Button>(R.id.button_play).apply {
	    setOnClickListener {
		val b = binder
		if (b != null) {
		    scope.launch {
			b.play()
		    }
		}
	    }
	}
	
	view.findViewById<Button>(R.id.button_stop).apply {
	    setOnClickListener {
		val b = binder
		if (b != null) {
		    scope.launch {
			b.stop()
		    }
		}
	    }
	}
	
	view.findViewById<Button>(R.id.button_next).apply {
	    setOnClickListener {
		val b = binder
		if (b != null) {
		    scope.launch {
			b.next()
		    }
		}
	    }
	}
	
	seekBar = view.findViewById<SeekBar>(R.id.seek_bar).apply {
	    setOnSeekBarChangeListener(seekBarChangeListener)
	}
	
	times = view.findViewById<TextView>(R.id.times)
	left_of_times = view.findViewById<Space>(R.id.left_of_times)
	right_of_times = view.findViewById<Space>(R.id.right_of_times)
    }
    
    override fun onStart() {
	super.onStart()
	val ctxt = getContext()
	if (ctxt != null) {
	    val intent = Intent(ctxt, MainService::class.java)
	    ctxt.bindService(intent, conn, Context.BIND_AUTO_CREATE)
	}
    }
    
    override fun onStop() {
	super.onStop()
	val ctxt = getContext()
	if (ctxt != null)
	    ctxt.unbindService(conn)
    }

    val seekBarChangeListener = object: SeekBar.OnSeekBarChangeListener {
	override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
	    if (!fromUser)
		return
	    val b = binder
	    if (b != null) {
		scope.launch {
		    b.seek(progress.toLong())
		}
	    }
	}
	override fun onStartTrackingTouch(seekBar: SeekBar) {
	    userSeeking = true
	}
	override fun onStopTrackingTouch(seekBar: SeekBar) {
	    userSeeking = false
	}
    }
}
