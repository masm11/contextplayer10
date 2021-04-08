package me.masm11.contextplayer10

class Application: android.app.Application() {
    override fun onCreate() {
	super.onCreate()
	Log.init(this)
	PlayContextStore.init(this)
    }
}
