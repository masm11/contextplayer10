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

import android.appwidget.AppWidgetProvider
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.app.PendingIntent
import android.widget.RemoteViews


class MainAppWidgetProvider: AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
	update(context, appWidgetManager, appWidgetIds, null, false)
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
    
    companion object {
	fun update(context: Context, appWidgetManager0: AppWidgetManager?, appWidgetIds0: IntArray?, contextName0: String?, playing: Boolean) {
	    val appWidgetManager = if (appWidgetManager0 != null) appWidgetManager0 else getAppWidgetManager(context)
	    val appWidgetIds = if (appWidgetIds0 != null) appWidgetIds0 else getAppWidgetIds(context, appWidgetManager)
	    val contextName = if (contextName0 != null) contextName0 else getContextName()
	    
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
		    setTextViewText(R.id.context_name, contextName)
		    setTextViewText(R.id.button, if (playing) "■" else "▶")
		    setOnClickPendingIntent(R.id.context_name, pendingIntent_context)
		    setOnClickPendingIntent(R.id.button, pendingIntent_play)
		}
		
		// Tell the AppWidgetManager to perform an update on the current app widget
		appWidgetManager.updateAppWidget(appWidgetId, views)
            }
	}
	
	private fun getAppWidgetManager(context: Context): AppWidgetManager {
	    return AppWidgetManager.getInstance(context);
	}
	private fun getAppWidgetIds(context: Context, appWidgetManager: AppWidgetManager): IntArray {
	    val componentName = ComponentName(context, MainAppWidgetProvider::class.java)
	    return appWidgetManager.getAppWidgetIds(componentName);
	}
	
	private fun getContextName(): String {
	    val uuid = PlayContextStore.getPlayingUuid()
	    val ctxt = PlayContextStore.find(uuid)
	    return ctxt.name
	}
    }
}
