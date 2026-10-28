package com.mdmesh.core.command
import com.mdmesh.core.command.handlers.DeviceVolumeHandler
import com.mdmesh.proto.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class DeviceVolumeHandlerTest {
 private fun command(value:String?)=CommandEnvelope(commandId="test",issuedAt="2026-10-28T00:00:00Z",type="device.volume",payload=value?.let{ProtocolJson.json.parseToJsonElement(it).jsonObject})
 @Test fun muteAndMaximumAreAccepted()=runBlocking {
  for(percent in listOf(0,50,100)) {var applied=-1;val handler=DeviceVolumeHandler{applied=it;it to 100};assertEquals(CommandStatus.DONE,handler.handle(command("{\"percent\":$percent}")).status);assertEquals(percent,applied)}
 }
 @Test fun malformedInputNeverTouchesAudio()=runBlocking {
  var calls=0;val handler=DeviceVolumeHandler{calls++;it to 100}
  for(value in listOf(null,"{}","{\"percent\":-1}","{\"percent\":101}","{\"percent\":1.5}"))assertEquals(CommandStatus.FAILED,handler.handle(command(value)).status)
  assertEquals(0,calls)
 }
 @Test fun deniedWritesAreNotSuccessful()=runBlocking {
  val handler=DeviceVolumeHandler{throw SecurityException("Blocked")};val result=handler.handle(command("{\"percent\":50}"));assertEquals(CommandStatus.FAILED,result.status);assertEquals("Blocked",result.detail)
 }
}
