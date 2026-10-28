import { apiClient } from './client';

export interface TelemetrySnapshot {
  operations?: Operations;
  dynamic?: Record<string, unknown>;
  hardware?: Record<string, unknown>;
  identity?: Record<string, unknown>;
  security?: Record<string, unknown>;
}

export async function getTelemetry(deviceId: number | string): Promise<TelemetrySnapshot | null> {
  return apiClient.get<TelemetrySnapshot | null>(`/private/agent/v1/devices/${deviceId}/telemetry`);
}

export interface Operations {
  capturedAt: number;
  passpoint?: { status: string; installed?: boolean; version?: string; versionCode?: number; foreground?: boolean; uiHeartbeatAt?: number; backendConnected?: boolean; backendCheckedAt?: number; lastError?: string; processStartedAt?: number; recentExits?: string };
  mediaVolume?: number; mediaVolumeMax?: number; mediaMuted?: boolean; brightness?: number; freeStorageBytes?: number;
  agentVersion?: string; recentAgentEvents?: Array<{type:string;at:number}>; remoteControl?: string;
}
