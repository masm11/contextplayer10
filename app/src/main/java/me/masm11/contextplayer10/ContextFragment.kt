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

import androidx.fragment.app.Fragment
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
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

class ContextFragment: Fragment() {
    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private var binder: MainService.Binder? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var itemTouchHelper: ItemTouchHelper
    
    // GC に破棄されないよう、変数に持っておく
    private val listener = object: MainService.OnPlayStatusBroadcastListener {
	override fun onPlayStatusBroadcastListener(playStatus: Player.PlayStatus) {
	    val adapter = recyclerView.getAdapter() as ContextAdapter
	    adapter.refresh()
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
	recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
	val ctxt = getContext()
	if (ctxt != null) {
	    val adapter = ContextAdapter(ctxt)
	    recyclerView.setLayoutManager(LinearLayoutManager(ctxt))
	    recyclerView.setAdapter(adapter)
	    
	    val cb = object: ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
		override fun onMove(p0: RecyclerView, p1: RecyclerView.ViewHolder, p2: RecyclerView.ViewHolder): Boolean {
                    val fromPosition = p1.adapterPosition	// 移動元
                    val toPosition = p2.adapterPosition		// 移動先
		    Log.d("from=${fromPosition}, to=${toPosition}")
		    adapter.reorder(fromPosition, toPosition)
                    return true
		}
		override fun onSwiped(p0: RecyclerView.ViewHolder, p1: Int) {
		}
		
		override fun isLongPressDragEnabled(): Boolean {
		    return (getContext() as MainActivity).getContextListActionMode()
		}
 	    }
	    
	    itemTouchHelper = ItemTouchHelper(cb)
	    itemTouchHelper.attachToRecyclerView(recyclerView)
	    
	    adapter.setOnClickListener { c ->
		PlayContextStore.setPlayingUuid(c.uuid)
		PlayContextStore.save(true)
		
		binder?.switchContext()
	    }
	    adapter.setOnLongClickListener { c ->
		if (!(getContext() as MainActivity).getContextListActionMode()) {
		    val fragment = ActionSelectionDialogFragment(adapter, c)
		    fragment.show(getParentFragmentManager(), "action_selection")
		    true
		} else
		    false
	    }
	}
	
	val fab = view.findViewById<View>(R.id.fab)
	fab.setOnClickListener { _ ->
	    val fragment = InputNameDialogFragment()
	    fragment.setAction { newName ->
		val list = PlayContextStore.loadAll()
		val maxDisplayOrder = list.map<PlayContext, Int> { i -> i.displayOrder }.maxOrNull()
		val newDisplayOrder = if (maxDisplayOrder == null) 1 else maxDisplayOrder + 1
		val i = PlayContext(UUID.randomUUID().toString(), newName, "//primary", null, 0, newDisplayOrder)
		list.add(i)
		PlayContextStore.save(true)
		val adapter = recyclerView.getAdapter() as ContextAdapter
		adapter.reloadList()
	    }
	    fragment.show(getParentFragmentManager(), "action_selection")
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
    
    override fun onDestroy() {
	Log.d("ContextFragment.onDestroy")
	(getContext() as MainActivity).back()
	super.onDestroy()
    }
    
    
    class ContextAdapter(private val context: Context): RecyclerView.Adapter<ContextAdapter.ViewHolder>() {
	private lateinit var contextList: List<PlayContext>
	private var onClick: (PlayContext) -> Unit = { c -> }
	private var onLongClick: (PlayContext) -> Boolean = { c -> false }
	private lateinit var currentUuid: String
	
	init {
	    reloadList()
	    Log.d("count=${contextList.count()}")
	}
	
	fun setOnClickListener(onClick: (PlayContext) -> Unit) {
	    this.onClick = onClick
	}
	
	fun setOnLongClickListener(onLongClick: (PlayContext) -> Boolean) {
	    this.onLongClick = onLongClick
	}
	
	class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
	    val textView_name: TextView
	    val textView_topDir: TextView
	    val textView_path: TextView
	    private lateinit var ctxt: PlayContext
	    private var onClick: (PlayContext) -> Unit = { c -> }
	    private var onLongClick: (PlayContext) -> Boolean = { c -> false }
	    init {
		textView_name = view.findViewById<TextView>(R.id.name)
		textView_topDir = view.findViewById<TextView>(R.id.top_dir)
		textView_path = view.findViewById<TextView>(R.id.path)
		
		view.setOnClickListener { v -> onClick(ctxt) }
		view.setOnLongClickListener { v -> onLongClick(ctxt) }
	    }
	    
	    fun setOnClickListener(onClick: (PlayContext) -> Unit) {
		this.onClick = onClick
	    }
	    fun setOnLongClickListener(onLongClick: (PlayContext) -> Boolean) {
		this.onLongClick = onLongClick
	    }
	    fun bind(ctxt: PlayContext) {
		this.ctxt = ctxt
	    }
	}
	
	// Create new views (invoked by the layout manager)
	override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            // Create a new view, which defines the UI of the list item
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.context_item, viewGroup, false)
	    
            return ViewHolder(view).apply {
		setOnClickListener(onClick)
		setOnLongClickListener(onLongClick)
	    }
	}
	
	// Replace the contents of a view (invoked by the layout manager)
	override fun onBindViewHolder(viewHolder: ViewHolder, pos: Int) {
	    val ctxt = contextList[pos]
	    viewHolder.textView_name.text = ctxt.name
	    viewHolder.textView_topDir.text = ctxt.topDir
	    viewHolder.textView_path.text = ctxt.path
	    
	    viewHolder.bind(ctxt)
	}
	
	// Return the size of your dataset (invoked by the layout manager)
	override fun getItemCount() = contextList.count()
	
	fun refresh() {
	    var curPos: Int? = null
	    contextList.forEachIndexed { i, c ->
		if (c.uuid == currentUuid)
		    notifyItemChanged(i)
	    }
	    
	    var newUuid = PlayContextStore.getPlayingUuid()
	    if (newUuid != currentUuid) {
		var newPos: Int? = null
		contextList.forEachIndexed { i, c ->
		    if (c.uuid == newUuid)
			notifyItemChanged(i)
		}
		currentUuid = newUuid
	    }
	}
	fun reloadList() {
	    Log.d("reload")
	    contextList = PlayContextStore.loadAll().sortedBy { a -> a.displayOrder }
	    currentUuid = PlayContextStore.getPlayingUuid()
	    notifyDataSetChanged()
	}
	fun reorder(fromPos: Int, toPos: Int) {
	    val list = mutableListOf<PlayContext>()
	    contextList.forEach { c -> list.add(c) }
	    Log.d("---- before")
	    list.forEach { c ->
		Log.d("${c.displayOrder} ${c.name}")
	    }
	    Log.d("---- before")
	    
	    list.add(toPos, list.removeAt(fromPos))
	    
	    var displayOrder = 1
	    list.forEach { c -> c.displayOrder = displayOrder++ }
	    Log.d("==== after")
	    list.forEach { c ->
		Log.d("${c.displayOrder} ${c.name}")
	    }
	    Log.d("==== after")
	    PlayContextStore.save(true)
	    
	    contextList = PlayContextStore.loadAll().sortedBy { a -> a.displayOrder }
	    notifyItemMoved(fromPos, toPos)
	}
    }
    
    
    class ActionSelectionDialogFragment(
	private val adapter: ContextAdapter,
	private val item: PlayContext
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

	    fragment.show(getParentFragmentManager(), "input_name")
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
