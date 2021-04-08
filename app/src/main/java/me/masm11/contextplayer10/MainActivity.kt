package me.masm11.contextplayer10

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.widget.Button
import android.widget.TextView
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.view.Menu
import android.view.MenuItem
import android.animation.ObjectAnimator
import android.animation.ValueAnimator

import kotlinx.coroutines.*

class MainActivity: AppCompatActivity() {
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private var binder: MainService.Binder? = null
    
    private var inContextFragment = false
    private var contextListActionMode = false
    
    // GC に破棄されないよう、変数に持っておく
    private val listener = object: MainService.OnPlayStatusBroadcastListener {
	override fun onPlayStatusBroadcastListener(playStatus: Player.PlayStatus) {
/*
	    val text = "${playStatus.file} ${playStatus.msec}/${playStatus.duration}"
	    val view: TextView = findViewById(R.id.text)
	    view.setText(text)
*/
	}
    }
    
    private val conn = object : ServiceConnection {
	override fun onServiceConnected(name: ComponentName, binder: IBinder) {
	    val b = binder as MainService.Binder
	    this@MainActivity.binder = b
	    Log.d("connected")
	    
	    b.setOnPlayStatusBroadcastedListener(listener)
	}
	override fun onServiceDisconnected(name: ComponentName) {
	    this@MainActivity.binder = null
	}
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
	setSupportActionBar(findViewById(R.id.toolbar))

	switchToMain()
	
	val intent = Intent(this, MainService::class.java)
	startService(intent)
    }
    
    override fun onStart() {
	super.onStart()
	val intent = Intent(this, MainService::class.java)
	startService(intent)
	bindService(intent, conn, BIND_AUTO_CREATE)
    }
    
    override fun onStop() {
	super.onStop()
	unbindService(conn)
    }

    fun switchToMain() {
	inContextFragment = false
	invalidateOptionsMenu()
	val fragmentManager = getSupportFragmentManager()
	val transaction = fragmentManager.beginTransaction()
//	transaction.addToBackStack(null)
	transaction.replace(R.id.container, MainFragment())
	transaction.commit()
    }
    
    fun switchToExplorer() {
	inContextFragment = false
	invalidateOptionsMenu()
	val fragmentManager = getSupportFragmentManager()
	val transaction = fragmentManager.beginTransaction()
	transaction.addToBackStack(null)
	transaction.replace(R.id.container, ExplorerFragment())
	transaction.commit()
    }
    
    fun switchToContext() {
	inContextFragment = true
	contextListActionMode = false
	invalidateOptionsMenu()
	val fragmentManager = getSupportFragmentManager()
	val transaction = fragmentManager.beginTransaction()
	transaction.addToBackStack(null)
	transaction.replace(R.id.container, ContextFragment(fragmentManager))
	transaction.commit()
    }
    
    fun back() {
	inContextFragment = false
	invalidateOptionsMenu()
    }
    
    fun getContextListActionMode(): Boolean {
	return contextListActionMode
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
	val menuInflator = getMenuInflater()
	menuInflator.inflate(R.menu.option_menu, menu)
	return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
	Log.d("onPrepareOptionsMenu")
	var item = menu.findItem(R.id.action_drag_mode)
	item.setVisible(inContextFragment and !contextListActionMode)
	item = menu.findItem(R.id.action_edit_mode)
	item.setVisible(inContextFragment and contextListActionMode)
	return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
	return when (item.itemId) {
	    R.id.action_edit_mode -> {
		contextListActionMode = false
		invalidateOptionsMenu()
		true
	    }
	    R.id.action_drag_mode -> {
		contextListActionMode = true
		invalidateOptionsMenu()
		true
	    }
	    else -> {
		super.onOptionsItemSelected(item)
	    }
	}
    }
}
