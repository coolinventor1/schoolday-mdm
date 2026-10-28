import { useEffect, useState } from 'react';
import { getEvents, type DeviceEvent } from '../api/events';
import { getTelemetry, type Operations } from '../api/telemetry';
import { listCommandHistory, type CommandHistoryItem } from '../api/commands';

export function OperationsPanel({number}:{number:string}) {
  const [data,setData]=useState<Operations|null>(null);
  const [logs,setLogs]=useState<DeviceEvent[]>([]);
  const [error,setError]=useState('');
  const [events,setEvents]=useState<CommandHistoryItem[]>([]);
  useEffect(()=>{
    let stopped=false; let timer:ReturnType<typeof setTimeout>;
    const load=async()=>{
      try {
        const [tel,history,deviceEvents]=await Promise.all([getTelemetry(number),listCommandHistory(number),getEvents(number)]);
        if(stopped)return;
        const diagnostic=history.find(c=>c.type==='device.diagnostics'&&c.status.toLowerCase()==='done'&&c.detail);
        let snapshot=tel?.operations ?? null;
        if(diagnostic?.detail) { try { const parsed=JSON.parse(diagnostic.detail) as Operations; if(parsed.capturedAt>(snapshot?.capturedAt??0))snapshot=parsed; }catch{} }
        setData(snapshot);setEvents(history);setLogs(deviceEvents.map(({id,type,ts})=>({id,type,ts})).slice(-50));setError('');
      }catch{if(!stopped)setError('Could not refresh monitoring. Displayed readings may be stale.');}
      if(!stopped)timer=setTimeout(()=>void load(),10000);
    }; void load();return()=>{stopped=true;clearTimeout(timer);};
  },[number]);
  const p=data?.passpoint;
  const age=data?Date.now()-data.capturedAt:Infinity;
  function download() {
    const url=URL.createObjectURL(new Blob([JSON.stringify({device:number,downloadedAt:new Date().toISOString(),snapshot:data,agentEvents:logs,commands:events.map(({type,status,createdAt,completedAt,subject})=>({type,status,createdAt,completedAt,subject}))},null,2)],{type:'application/json'}));
    const a=document.createElement('a');a.href=url;a.download=`diagnostics-${number}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  }
  return <section className="action-group">
    <h3 className="action-group-title">PassPoint health & diagnostics</h3>
    {error&&<p role="alert">{error}</p>}
    {!data?<p>No health report yet. Update the agent and PassPoint, then collect diagnostics.</p>:<>
      <p className="muted">Reported {new Date(data.capturedAt).toLocaleString()}{age>120000?' — stale; collect diagnostics for a fresh reading':''}</p>
      <dl className="state-grid">
        <div><dt>PassPoint</dt><dd>{p?.status??'Unknown'}{p?.foreground===false?' (background)':''}</dd></div>
        <div><dt>SchoolDay connection</dt><dd>{!p?.backendCheckedAt?'Not checked':Date.now()-p.backendCheckedAt>120000?'Stale':p.backendConnected?'Connected':'Failed'}</dd></div>
        <div><dt>PassPoint version</dt><dd>{p?.version??'Unknown'} {p?.versionCode?`(${p.versionCode})`:''}</dd></div>
        <div><dt>Media volume</dt><dd>{data.mediaVolume??'—'} / {data.mediaVolumeMax??'—'}</dd></div>
        <div><dt>Free storage</dt><dd>{data.freeStorageBytes==null?'Unknown':`${(data.freeStorageBytes/1073741824).toFixed(1)} GB`}</dd></div>
        <div><dt>Process started</dt><dd>{p?.processStartedAt?new Date(p.processStartedAt).toLocaleString():'Unknown'}</dd></div>
      </dl>
      {p?.lastError&&<p role="alert">{p.lastError}</p>}
      <button className="btn" onClick={download}>Download diagnostics</button>
      <details><summary>Recent agent events and app exits</summary><pre style={{whiteSpace:'pre-wrap'}}>{JSON.stringify({agentEvents:logs,appExitReasons:p?.recentExits??'[]'},null,2)}</pre></details>
    </>}
    <p className="muted">Remote viewing/control is unavailable in this agent. Unattended screen capture is not enabled on this Lenovo.</p>
  </section>;
}
