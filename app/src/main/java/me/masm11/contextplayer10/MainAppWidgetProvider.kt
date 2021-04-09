package me.masm11.contextplayer10

import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.widget.RemoteViews


class MainAppWidgetProvider: AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
	val context_name = getContextName()
	
        appWidgetIds.forEach { appWidgetId ->
	    val pendingIntent_context = Intent(context, MainActivity::class.java).let { intent ->
		intent.action = "me.masm11.contextplayer10.context_list"
		PendingIntent.getActivity(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT)
	    }
	    // 参考: https://qiita.com/wakwak/items/e8daef3a7a3003bfe360
	    val pendingIntent_play = Intent(context, MainAppWidgetProvider::class.java).let { intent ->
		intent.action = "me.masm11.contextplayer10.play"
		PendingIntent.getBroadcast(context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT)
	    }
	    
            // Get the layout for the App Widget and attach an on-click listener
            // to the button
	    val views = RemoteViews(context.packageName, R.layout.main_appwidget).apply {
		setTextViewText(R.id.context_name, context_name)
		setOnClickPendingIntent(R.id.context_name, pendingIntent_context)
		setOnClickPendingIntent(R.id.button, pendingIntent_play)
	    }
	    
            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
	
	super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
	Log.d("broadcast onReceive ${intent.action}")
	if (intent.action == "me.masm11.contextplayer10.play") {
	    val i = Intent(context, MainService::class.java)
	    i.action = "me.masm11.contextplayer10.play"
	    context.startForegroundService(i)
	} else
	    super.onReceive(context, intent)
    }

    private fun getContextName(): String {
	val uuid = PlayContextStore.getPlayingUuid()
	val ctxt = PlayContextStore.find(uuid)
	return ctxt.name
    }
}
