package com.mdmesh.core.device

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.os.StatFs
import android.os.Environment
import com.mdmesh.core.telemetry.EventLog
import kotlinx.serialization.json.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val passPoint: PassPointMonitor,
    private val events: EventLog,
) {
    fun snapshot(): JsonObject = buildJsonObject {
        put("capturedAt", System.currentTimeMillis())
        put("passpoint", passPoint.snapshot())
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        put("mediaVolume", audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        put("mediaVolumeMax", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
        put("mediaMuted", audio.isStreamMute(AudioManager.STREAM_MUSIC))
        put("brightness", Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1))
        put("freeStorageBytes", StatFs(Environment.getDataDirectory().path).availableBytes)
        put("agentVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown")
        put("recentAgentEvents", buildJsonArray {
            // Event types and times only: free-form logs can contain enrollment or student data.
            events.peek().takeLast(30).forEach { event -> add(buildJsonObject {
                put("type", event.type); put("at", event.ts)
            }) }
        })
        put("remoteControl", "Unavailable: no capture/signaling implementation; Android capture requires local consent")
    }
}
