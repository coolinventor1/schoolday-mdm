package com.mdmesh.core.command.handlers

import android.content.Context
import android.media.AudioManager
import com.mdmesh.core.command.*
import com.mdmesh.core.device.OperationsCollector
import com.mdmesh.core.device.PassPointMonitor
import com.mdmesh.proto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

class DeviceVolumeHandler(private val setVolume: (Int) -> Pair<Int, Int>) : CommandHandler {
    constructor(context: Context) : this({ percent ->
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        check(!audio.isVolumeFixed) { "Device has fixed volume" }
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (percent * max / 100.0).roundToInt()
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        val actual = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        check(actual == target) { "Volume change blocked: expected $target, read $actual" }
        actual to max
    })
    override val type = "device.volume"
    @Serializable private data class Payload(val percent: Int)
    override suspend fun handle(command: CommandEnvelope): CommandResult = runCatching {
        val percent = ProtocolJson.json.decodeFromJsonElement(Payload.serializer(), requireNotNull(command.payload)).percent
        require(percent in 0..100) { "Volume must be an integer from 0 to 100" }
        val (actual, max) = setVolume(percent)
        CommandResults.done(command, "Media volume $actual/$max (requested $percent%); 0 mutes media")
    }.getOrElse { CommandResults.failed(command, it.message ?: "Volume update failed") }
}
class PassPointRestartHandler(private val monitor: PassPointMonitor) : CommandHandler {
    override val type = "device.passpointRestart"
    override suspend fun handle(command: CommandEnvelope): CommandResult = withContext(Dispatchers.IO) {
        runCatching { CommandResults.done(command, monitor.restart()) }
            .getOrElse { CommandResults.failed(command, it.message ?: "Restart failed") }
    }
}
class DeviceDiagnosticsHandler(private val collector: OperationsCollector) : CommandHandler {
    override val type = "device.diagnostics"
    override suspend fun handle(command: CommandEnvelope): CommandResult = withContext(Dispatchers.IO) {
        runCatching { CommandResults.done(command, collector.snapshot().toString()) }
            .getOrElse { CommandResults.failed(command, "Diagnostics failed: ${it.javaClass.simpleName}") }
    }
}
