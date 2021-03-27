package me.masm11.contextplayer10

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

import android.content.Context

import java.util.UUID

@Serializable
class PlayContext (
    var uuid: String,
    var name: String,
    var topDir: String,
    var path: String?,
    var msec: Long,
)

@Serializable
class PlayContextConfig (
    var list: MutableList<PlayContext>,
    var playingUuid: String
)

class PlayContextStore {
    companion object {
	private val KEY_STORAGE = "me.masm11.contextplayer10.storage"
	private val KEY_PLAY_CONTEXT_CONFIG = "me.masm11.contextplayer10.play_context_config"
	private lateinit var context: Context
	private lateinit var config: PlayContextConfig
	
	private val INITIAL_JSON = "{\"list\":[{\"uuid\":\"0000\",\"name\":\"default\",\"topDir\":\"//primary\",\"path\":null,\"msec\":0}],\"playingUuid\":\"0000\"}"
	
	fun load(context: Context) {
	    this.context = context
	    val pref = context.getSharedPreferences(KEY_STORAGE, Context.MODE_PRIVATE)
	    var json = pref.getString(KEY_PLAY_CONTEXT_CONFIG, null)
	    Log.d("json1-2: ${json}")
	    if (json == null)
		json = INITIAL_JSON
	    Log.d("json1-1: ${json}")
	    config = Json.decodeFromString(json)
	    
	    Log.d(config.list::class.toString())
	}
	
	fun find(uuid: String): PlayContext {
	    try {
		return config.list.first { c -> c.uuid == uuid }
	    } catch (e: NoSuchElementException) {
		val new = PlayContext(uuid, "default", "//primary", null, 0)
		config.list.add(new)
		return new
	    }
	}
	
	fun getPlayingUuid(): String {
	    return config.playingUuid
	}
	
	fun save() {
	    val pref = context.getSharedPreferences(KEY_STORAGE, Context.MODE_PRIVATE)
	    val json = Json.encodeToString(config)
	    Log.d("save json1: ${json}")
	    with (pref.edit()) {
		putString(KEY_PLAY_CONTEXT_CONFIG, json)
		apply()
	    }
	}
    }
}
