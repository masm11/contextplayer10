package me.masm11.contextplayer10

import androidx.fragment.app.Fragment
import androidx.constraintlayout.widget.ConstraintLayout
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.widget.SeekBar
import android.widget.Button
import android.widget.TextView
import android.widget.ListView
import android.widget.Space
import android.widget.BaseAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.animation.ObjectAnimator

import kotlinx.coroutines.*

class ExplorerFragment: Fragment() {
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private var binder: MainService.Binder? = null
    private lateinit var listView: ListView
    
    // GC に破棄されないよう、変数に持っておく
    private val listener = object: MainService.OnPlayStatusBroadcastListener {
	override fun onPlayStatusBroadcastListener(playStatus: Player.PlayStatus) {
/*
	    scope.launch {
		updateInfo(playStatus)
	    }
*/
	}
    }
    
    private val conn = object: ServiceConnection {
	override fun onServiceConnected(name: ComponentName, binder: IBinder) {
	    val b = binder as MainService.Binder
	    this@ExplorerFragment.binder = b
	    Log.d("connected")
	    
	    b.setOnPlayStatusBroadcastedListener(listener)
	}
	override fun onServiceDisconnected(name: ComponentName) {
	    this@ExplorerFragment.binder = null
	}
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
	return inflater.inflate(R.layout.explorer_fragment, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
	listView = view.findViewById<ListView>(R.id.list_view)
    }
    
    override fun onStart() {
	super.onStart()
	val ctxt = getContext()
	if (ctxt != null) {
	    val intent = Intent(ctxt, MainService::class.java)
	    ctxt.startService(intent)
	    ctxt.bindService(intent, conn, Context.BIND_AUTO_CREATE)
	    showListView(ctxt, MFile("//primary"), false)
	}
    }
    
    override fun onStop() {
	super.onStop()
	val ctxt = getContext()
	if (ctxt != null)
	    ctxt.unbindService(conn)
    }
    
    private fun showListView(ctxt: Context, path: MFile, leaving: Boolean) {
	val oldListView = listView
	val parent = oldListView.getParent() as ViewGroup
	val width = oldListView.getWidth().toFloat()
	
	val adapter = ItemAdapter(ctxt, path)
	
	listView = ListView(ctxt)
	listView.setLayoutParams(oldListView.getLayoutParams())
	parent.addView(listView)
	
	listView.setAdapter(adapter)
	listView.setOnItemClickListener { _, _, pos, _ ->
	    val item = adapter.getItem(pos)
	    
	    if (item.isDirectory) {
		showListView(ctxt, item.path, item.name == "..")
	    } else {
		val b = binder as MainService.Binder
		scope.launch {
		    b.jump(item.path)
		}
	    }
	}
	listView.setOnItemLongClickListener { _, _, pos, _ ->
	    val item = adapter.getItem(pos)
	    
	    if (item.isDirectory) {
		val b = binder as MainService.Binder
		scope.launch {
		    b.setTopDir(item.path)
		}
		true
	    } else
		false
	}
	
	val time = 200.toLong()
	if (!leaving) {
	    ObjectAnimator.ofFloat(listView, "translationX", width, 0f).apply {
		duration = time
		start()
	    }
	    ObjectAnimator.ofFloat(oldListView, "translationX", 0f, -width).apply {
		duration = time
		start()
	    }
	} else {
	    ObjectAnimator.ofFloat(listView, "translationX", -width, 0f).apply {
		duration = time
		start()
	    }
	    ObjectAnimator.ofFloat(oldListView, "translationX", 0f, width).apply {
		duration = time
		start()
	    }
	}
	Handler(Looper.getMainLooper()).postDelayed({ -> parent.removeView(oldListView) }, time)
    }
    
    
    inner class ItemAdapter(private val context: Context, private val dir: MFile): BaseAdapter() {
	private var layoutInflater: LayoutInflater
	private val itemList = ArrayList<Item>()

	init {
	    layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
	    
	    val dirs = ArrayList<Item>()
	    val files = ArrayList<Item>()
	    
	    val list = dir.listFiles()
	    if (list != null) {
		list.sortWith { a, b ->
		    val c = a.toString().toLowerCase().compareTo(b.toString().toLowerCase())
		    if (c != 0) c else a.toString().compareTo(b.toString())
		}
		list.forEach { m ->
		    if (m.isDirectory)
			dirs.add(Item(m, this))
		    else
			files.add(Item(m, this))
		}
	    }
	    itemList.add(Item(dir, this, dir.toString()))
	    if (dir.toString() != "//")
		itemList.add(Item(dir.parentFile, this, ".."))
	    itemList.addAll(dirs)
	    itemList.addAll(files)
	}
	
	override fun getCount(): Int {
	    return itemList.count()
	}
	override fun getItem(pos: Int): Item {
	    return itemList[pos]
	}
	override fun getItemId(pos: Int): Long {
	    return getItem(pos).id
	}
	override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
	    val view = if (convertView == null) layoutInflater.inflate(R.layout.explorer_item, parent, false) else convertView
	    
	    val item = getItem(pos)
	    view.findViewById<TextView>(R.id.name).setText(item.name)
	    view.findViewById<TextView>(R.id.title).setText(item.metadata.title)
	    view.findViewById<TextView>(R.id.artist).setText(item.metadata.artist)
	    
            return view
	}
    }
    
    inner class Item(val path: MFile, private val adapter: ItemAdapter, private val name0: String? = null) {
	var metadata: Metadata
	private set
	
	var name: String
	private set
	
	init {
	    metadata = Metadata(path.file.toString())
	    scope.launch {
		metadata.extract()
		withContext(Dispatchers.Main) {
		    Log.d("${metadata.title} / ${metadata.artist}")
		    adapter.notifyDataSetChanged()
		}
	    }

	    if (name0 == null) {
		val str = path.toString()
		val slashPos = str.lastIndexOf('/')
		name = str.substring(slashPos + 1)
	    } else {
		name = name0
	    }
	}
	
	val id: Long
	get() {
	    return path.hashCode().toLong()
	}
	
	val isDirectory: Boolean
	get() {
	    return path.isDirectory
	}
    }
}
