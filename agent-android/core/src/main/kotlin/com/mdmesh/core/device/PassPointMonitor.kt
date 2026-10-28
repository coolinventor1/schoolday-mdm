package com.mdmesh.core.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.mdmesh.proto.ProtocolJson
import kotlinx.serialization.json.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A bounded, metadata-only channel to the managed PassPoint app. No student records or cookies. */
@Singleton
class PassPointMonitor @Inject constructor(@ApplicationContext private val context: Context) {
    companion object { const val PACKAGE = "one.schoolday.passpoint.prototype" }
    private fun call(method: String): android.os.Bundle? {
        return context.contentResolver.acquireUnstableContentProviderClient(Uri.parse("content://$PACKAGE.management"))?.use {
            it.call(method, null, null)
        }
    }
    fun snapshot(): JsonObject = runCatching {
        val info = context.packageManager.getPackageInfo(PACKAGE, 0)
        val data = call("health")
        buildJsonObject {
            put("installed", true); put("version", info.versionName ?: "unknown")
            put("versionCode", if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()); put("capturedAt", System.currentTimeMillis())
            if (data == null) put("status", "Health integration unavailable")
            else {
                put("status", if (data.getBoolean("responsive")) "Responding" else "Not responding")
                put("foreground", data.getBoolean("foreground"))
                put("uiHeartbeatAt", data.getLong("uiHeartbeatAt"))
                put("backendCheckedAt", data.getLong("backendCheckedAt"))
                put("backendConnected", data.getBoolean("backendConnected"))
                put("lastError", data.getString("lastError") ?: "")
                put("processStartedAt", data.getLong("processStartedAt"))
                put("recentExits", data.getString("recentExits") ?: "[]")
            }
        }
    }.getOrElse { buildJsonObject {
        put("status", "Unavailable: app missing, older version, or management channel denied")
        put("capturedAt", System.currentTimeMillis())
    } }

    /** The app terminates its own process. Android device owner can then relaunch the allowlisted activity. */
    fun restart(): String {
        check(call("prepareRestart")?.getBoolean("accepted") == true) { "Install a PassPoint build with management support first" }
        Thread.sleep(800)
        val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(PACKAGE)) { "PassPoint launcher missing" }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        return "PassPoint restart requested; confirm the new process start and UI heartbeat in health monitoring"
    }
}
