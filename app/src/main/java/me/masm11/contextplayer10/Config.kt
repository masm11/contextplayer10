package me.masm11.contextplayer10

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

import android.content.Context
import android.widget.Toast

import java.util.UUID

@Serializable
class PlayContext (
    var uuid: String,
    var name: String,
    var topDir: String,
    var path: String?,
    var msec: Long,
    var displayOrder: Int,
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
	
	private val INITIAL_JSON = """
          {
            "list":[
              {
                "uuid":"0000",
                "name":"default",
                "topDir":"//primary",
                "path":null,
                "msec":0,
                "displayOrder":1
              }
            ],
            "playingUuid":"0000"
          }
        """
	
	fun init(context: Context) {
	    this.context = context
	    val pref = context.getSharedPreferences(KEY_STORAGE, Context.MODE_PRIVATE)
	    var json = pref.getString(KEY_PLAY_CONTEXT_CONFIG, null)
	    Log.d("json1-2: ${json}")
	    if (json == null)
		json = INITIAL_JSON
	    Log.d("json1-1: ${json}")
	    try {
		config = Json.decodeFromString(json)
	    } catch (e: Exception) {
		Toast.makeText(context, "初期化しました", Toast.LENGTH_SHORT).show()
		config = Json.decodeFromString(INITIAL_JSON)
	    }
	    
	    Log.d(config.list::class.toString())
	}
	
	fun find(uuid: String): PlayContext {
	    try {
		return config.list.first { c -> c.uuid == uuid }
	    } catch (e: NoSuchElementException) {
		val new = PlayContext(uuid, "default", "//primary", null, 0, 1)
		config.list.add(new)
		return new
	    }
	}
	
	fun getPlayingUuid(): String {
	    return config.playingUuid
	}
	
	fun setPlayingUuid(uuid: String) {
	    config.playingUuid = uuid
	}

	fun save(save_msec: Boolean = true) {
	    val pref = context.getSharedPreferences(KEY_STORAGE, Context.MODE_PRIVATE)
	    var currJson = pref.getString(KEY_PLAY_CONTEXT_CONFIG, null)
	    val json = Json.encodeToString(config)
	    // Log.d("save json1: ${json}")
	    var differ = json != currJson
	    if (differ && !save_msec) {
		if (currJson != null)
		    differ = differWithMsecSuppressed(currJson, json)
	    }
	    if (differ) {
		// Log.d("json changed.")
		Log.d("save json: ${json}")
		with (pref.edit()) {
		    putString(KEY_PLAY_CONTEXT_CONFIG, json)
		    apply()
		}
	    }
	}
	
	fun differWithMsecSuppressed(json1: String, json2: String): Boolean {
	    val config1 = Json.decodeFromString<PlayContextConfig>(json1)
	    val config2 = Json.decodeFromString<PlayContextConfig>(json2)
	    
	    if (config1.playingUuid != config2.playingUuid)
		return true
	    config1.list.forEach { ctxt1 ->
		config2.list.forEach { ctxt2 ->
		    if (ctxt2.uuid == ctxt1.uuid)
			ctxt2.msec = ctxt1.msec
		}
	    }
	    val json22 = Json.encodeToString(config2)
	    // Log.d("json1:  ${json1}")
	    // Log.d("json22: ${json22}")
	    return json1 != json22
	}
	
	fun loadAll(): MutableList<PlayContext> {
	    return config.list
	}
    }
}
