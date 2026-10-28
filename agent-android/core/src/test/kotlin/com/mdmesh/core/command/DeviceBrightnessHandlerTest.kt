package com.mdmesh.core.command

import com.mdmesh.core.command.handlers.DeviceBrightnessHandler
import com.mdmesh.proto.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DeviceBrightnessHandlerTest {
    private fun command(value: String?) = CommandEnvelope(commandId = "test", issuedAt = "2026-10-28T00:00:00Z",
        type = DeviceAction.BRIGHTNESS, payload = value?.let { ProtocolJson.json.parseToJsonElement(it).jsonObject })
    @Test fun validValuesAreMappedAndReadBack() = runBlocking {
        for ((percent, raw) in listOf(1 to 3, 50 to 128, 100 to 255)) {
            var written = 0
            val handler = DeviceBrightnessHandler({ true }, { written = it; it })
            assertEquals(CommandStatus.DONE, handler.handle(command("{\"percent\":$percent}")).status)
            assertEquals(raw, written)
        }
    }
    @Test fun malformedValuesNeverWrite() = runBlocking {
        val handler = DeviceBrightnessHandler({ true }, { error("Must not write") })
        for (value in listOf(null, "{}", "{\"percent\":0}", "{\"percent\":101}", "{\"percent\":1.5}")) {
            val result = handler.handle(command(value))
            assertEquals(CommandStatus.FAILED, result.status)
            assertFalse(result.detail.orEmpty().contains("Must not write"))
        }
    }
    @Test fun unsupportedDoesNotWrite() = runBlocking {
        val handler = DeviceBrightnessHandler({ false }, { error("Must not write") })
        assertEquals(CommandStatus.UNSUPPORTED, handler.handle(command("{\"percent\":50}")).status)
    }
    @Test fun readbackMismatchFails() = runBlocking {
        val handler = DeviceBrightnessHandler({ true }, { 255 })
        assertEquals(CommandStatus.FAILED, handler.handle(command("{\"percent\":50}")).status)
    }
    @Test fun policyFailureIsReported() = runBlocking {
        val handler = DeviceBrightnessHandler({ true }, { throw SecurityException("Denied") })
        val result = handler.handle(command("{\"percent\":50}"))
        assertEquals(CommandStatus.FAILED, result.status)
        assertEquals("Denied", result.detail)
    }
}
