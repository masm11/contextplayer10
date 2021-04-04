package me.masm11.contextplayer10

import androidx.fragment.app.Fragment
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
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
import android.widget.EditText
import android.widget.ListView
import android.widget.Space
import android.widget.BaseAdapter
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.animation.ObjectAnimator
import android.app.Dialog
import android.app.AlertDialog
import android.content.DialogInterface

import java.util.UUID

import kotlinx.coroutines.*

class ContextFragment(private val supportFragmentManager: FragmentManager): Fragment() {
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private var binder: MainService.Binder? = null
    private lateinit var listView: ListView
    
    // GC に破棄されないよう、変数に持っておく
    private val listener = object: MainService.OnPlayStatusBroadcastListener {
	override fun onPlayStatusBroadcastListener(playStatus: Player.PlayStatus) {
	    val adapter = listView.getAdapter() as ContextAdapter
	    adapter.reloadList()
	}
    }
    
    private val conn = object: ServiceConnection {
	override fun onServiceConnected(name: ComponentName, binder: IBinder) {
	    val b = binder as MainService.Binder
	    this@ContextFragment.binder = b
	    Log.d("connected")
	    
	    b.setOnPlayStatusBroadcastedListener(listener)
	}
	override fun onServiceDisconnected(name: ComponentName) {
	    this@ContextFragment.binder = null
	}
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
	return inflater.inflate(R.layout.context_fragment, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
	listView = view.findViewById<ListView>(R.id.list_view)
	val ctxt = getContext()
	if (ctxt != null) {
	    val adapter = ContextAdapter(ctxt)
	    listView.setAdapter(adapter)
	    
	    listView.setOnItemClickListener { _, _, pos, _ ->
		val item = adapter.getItem(pos)
		PlayContextStore.setPlayingUuid(item.uuid)
		PlayContextStore.save(true)
		
		val b = binder as MainService.Binder
		if (b != null)
		    b.switchContext()
	    }
	    listView.setOnItemLongClickListener { _, _, pos, _ ->
		val item = adapter.getItem(pos)
		val fragment = ActionSelectionDialogFragment(adapter, item, supportFragmentManager)
		fragment.show(supportFragmentManager, "action_selection")
		true
	    }
	}
	
	val fab = view.findViewById<View>(R.id.fab)
	fab.setOnClickListener { _ ->
	    val fragment = InputNameDialogFragment()
	    fragment.setAction { newName ->
		val list = PlayContextStore.loadAll()
		val maxDisplayOrder = list.map<PlayContext, Int> { i -> i.displayOrder }.max()
		val newDisplayOrder = if (maxDisplayOrder == null) 1 else maxDisplayOrder + 1
		val i = PlayContext(UUID.randomUUID().toString(), newName, "//primary", null, 0, newDisplayOrder)
		list.add(i)
		PlayContextStore.save(true)
		val adapter = listView.getAdapter() as ContextAdapter
		adapter.reloadList()
	    }
	    fragment.show(supportFragmentManager, "action_selection")
	}
    }
    
    override fun onStart() {
	super.onStart()
	val ctxt = getContext()
	if (ctxt != null) {
	    val intent = Intent(ctxt, MainService::class.java)
	    ctxt.startService(intent)
	    ctxt.bindService(intent, conn, Context.BIND_AUTO_CREATE)
	}
    }
    
    override fun onStop() {
	super.onStop()
	val ctxt = getContext()
	if (ctxt != null)
	    ctxt.unbindService(conn)
    }
    
    
    inner class ContextAdapter(private val context: Context): BaseAdapter() {
	private var layoutInflater: LayoutInflater
	private lateinit var contextList: List<PlayContext>
	
	init {
	    layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
	    reloadList()
	}
	
	override fun getCount(): Int {
	    return contextList.count()
	}
	override fun getItem(pos: Int): PlayContext {
	    return contextList[pos]
	}
	override fun getItemId(pos: Int): Long {
	    return getItem(pos).uuid.hashCode().toLong()
	}
	override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
	    val view = if (convertView == null) layoutInflater.inflate(R.layout.context_item, parent, false) else convertView
	    
	    val ctxt = getItem(pos)
	    view.findViewById<TextView>(R.id.name).setText(ctxt.name)
	    view.findViewById<TextView>(R.id.top_dir).setText(ctxt.topDir)
	    view.findViewById<TextView>(R.id.path).setText(ctxt.path)
            return view
	}

	fun reloadList() {
	    contextList = PlayContextStore.loadAll().sortedBy { a -> a.displayOrder }
	    notifyDataSetChanged()
	}
    }
    
    
    class ActionSelectionDialogFragment(
	private val adapter: ContextAdapter,
	private val item: PlayContext,
	private val supportFragmentManager: FragmentManager
    ) : DialogFragment() {
	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
	    return activity?.let {
		val builder = AlertDialog.Builder(it)
		builder.setTitle("タイトル")
                    .setItems(R.array.context_actions,
                DialogInterface.OnClickListener { dialog, which ->
		    Log.d("which=${which}")
		    when (which) {
			0 -> editContext()
			1 -> deleteContext()
		    }
                })
		builder.create()
	    } ?: throw IllegalStateException("Activity cannot be null")
	}
	
	private fun editContext() {
	    val fragment = InputNameDialogFragment(item.name)
	    fragment.setAction { newName ->
		val list = PlayContextStore.loadAll()
		val i = list.find { i -> i.uuid == item.uuid }
		if (i != null)
		    i.name = newName
		PlayContextStore.save(true)
		adapter.reloadList()
	    }

	    fragment.show(supportFragmentManager, "input_name")
	}
	
	private fun deleteContext() {
	    if (PlayContextStore.getPlayingUuid() == item.uuid)
		Toast.makeText(context, "プレイ中のコンテキストは削除できません", Toast.LENGTH_SHORT).show()
	    else {
		val list = PlayContextStore.loadAll()
		val i = list.find { i -> i.uuid == item.uuid }
		if (i != null)
		    list.remove(i)
		PlayContextStore.save(true)
		adapter.reloadList()
	    }
	}
    }

    class InputNameDialogFragment(private val initialName: String? = null) : DialogFragment() {
	private var action: (String) -> Unit

	init {
	    action = { newName -> }
	}
	
	fun setAction(action: (String) -> Unit) {
	    this.action = action
	}
	
	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
	    return activity?.let {
		val builder = AlertDialog.Builder(it)
		builder.setTitle("コンテキスト名")
		val inflater = requireActivity().layoutInflater;
		val view = inflater.inflate(R.layout.dialog_edit_context, null)
		val editText = view.findViewById<EditText>(R.id.new_name)
		
		if (initialName != null)
		    editText.setText(initialName)
		builder.setView(view)
                builder.setPositiveButton("OK",
		DialogInterface.OnClickListener { dialog, id ->
		    val newName = editText.getText().toString()
		    action(newName)
                })
                builder.setNegativeButton("Cancel",
                DialogInterface.OnClickListener { dialog, id ->
                    dialog.cancel()
                })
		builder.create()
	    } ?: throw IllegalStateException("Activity cannot be null")
	}
    }

}
