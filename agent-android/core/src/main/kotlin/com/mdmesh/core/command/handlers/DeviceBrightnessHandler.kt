package com.mdmesh.core.command.handlers

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.mdmesh.core.command.CommandHandler
import com.mdmesh.core.command.CommandResults
import com.mdmesh.policy.wifi.DpmHandle
import com.mdmesh.proto.CommandEnvelope
import com.mdmesh.proto.CommandResult
import com.mdmesh.proto.DeviceAction
import com.mdmesh.proto.ProtocolJson
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Manual device-wide brightness; does not unlock or foreground the agent. */
class DeviceBrightnessHandler(
    private val supported: () -> Boolean,
    private val apply: (Int) -> Int,
) : CommandHandler {
    constructor(context: Context, handle: DpmHandle) : this(
        supported = { Build.VERSION.SDK_INT >= 28 && handle.dpm.isDeviceOwnerApp(handle.admin.packageName) },
        apply = { raw ->
            handle.dpm.setSystemSetting(handle.admin, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL.toString())
            handle.dpm.setSystemSetting(handle.admin, Settings.System.SCREEN_BRIGHTNESS, raw.toString())
            check(Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) { "Automatic brightness is still enabled" }
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        },
    )

    override val type = DeviceAction.BRIGHTNESS
    @Serializable private data class Payload(val percent: Int)

    override suspend fun handle(command: CommandEnvelope): CommandResult = runCatching {
        if (!supported()) return CommandResults.unsupported(command, "Requires device owner on Android 9 or newer")
        val payload = requireNotNull(command.payload) { "Brightness percent is required" }
        val percent = ProtocolJson.json.decodeFromJsonElement(Payload.serializer(), payload).percent
        require(percent in 1..100) { "Brightness must be an integer from 1 to 100" }
        val raw = (percent * 255.0 / 100).roundToInt().coerceIn(1, 255)
        val actual = apply(raw)
        check(actual == raw) { "Brightness readback mismatch: requested $raw, got $actual" }
        CommandResults.done(command, "Brightness $percent% (Android $actual/255); automatic brightness off")
    }.getOrElse { CommandResults.failed(command, it.message ?: "Brightness update failed") }
}
