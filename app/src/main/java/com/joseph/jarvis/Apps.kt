package com.joseph.jarvis

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap

/** An app on the phone that can be opened. */
data class AppInfo(val label: String, val packageName: String, val icon: Bitmap?)

object Apps {
    /** Every app that shows up in your app drawer, A–Z. */
    fun launchable(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = try { it.loadIcon(pm).toBitmap(96, 96) } catch (e: Exception) { null },
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Apps that accept "play this" requests: the music apps Jarvis can hand a search to. */
    fun musicApps(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
        return pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, null) }
            .sortedBy { it.label.lowercase() }
    }

    fun open(ctx: Context, packageName: String): Boolean {
        val launch = ctx.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(launch); true } catch (e: Exception) { false }
    }

    /**
     * Asks a music app to search for and play something, e.g. "Burna Boy".
     * If the app doesn't support that, it just opens the app instead.
     */
    fun playInApp(ctx: Context, packageName: String, query: String): Boolean {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(packageName)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(intent); true } catch (e: Exception) { open(ctx, packageName) }
    }
}
