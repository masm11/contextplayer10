package me.masm11.contextplayer10

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.widget.Button

import kotlinx.coroutines.*

class MainActivity: AppCompatActivity() {
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private var binder: MainService.Binder? = null
    
    private val conn = object : ServiceConnection {
	override fun onServiceConnected(name: ComponentName, binder: IBinder) {
	    this@MainActivity.binder = binder as MainService.Binder
	    Log.d("connected")
	}
	override fun onServiceDisconnected(name: ComponentName) {
	    this@MainActivity.binder = null
	}
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
	
	val btn: Button = findViewById(R.id.button)
	btn.setOnClickListener {
	    val b = binder
	    if (b != null) {
		scope.launch {
		    b.play()
		}
	    }
	}

	val btn_stop: Button = findViewById(R.id.button_stop)
	btn_stop.setOnClickListener {
	    val b = binder
	    if (b != null) {
		scope.launch {
		    b.stop()
		}
	    }
	}
    }
    
    override fun onStart() {
	super.onStart()
	val intent = Intent(this, MainService::class.java)
	bindService(intent, conn, BIND_AUTO_CREATE)
    }
    
    override fun onStop() {
	super.onStop()
	unbindService(conn)
    }
}
