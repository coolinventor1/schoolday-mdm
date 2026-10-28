import {useEffect,useState} from 'react';
import {listApplications,getVersions,type ApplicationVersion} from '../api/applications';
import {getLatestScan,scanApps,type AppInfo} from '../api/deviceApps';
import {installApp,forceSync,type CommandHistoryItem} from '../api/commands';
import {useToast} from '../ui/toast';
interface Release {name:string;pkg:string;version:ApplicationVersion}
export function DeviceUpdates({number,history}:{number:string;history:CommandHistoryItem[]}){
 const [apps,setApps]=useState<AppInfo[]>([]);const [releases,setReleases]=useState<Release[]>([]);const [busy,setBusy]=useState(false);const [scannedAt,setScannedAt]=useState<number>();const [error,setError]=useState('');const toast=useToast();
 useEffect(()=>{let active=true;void(async()=>{try{
   const [snapshot,library]=await Promise.all([getLatestScan(number),listApplications()]);
   const versions=await Promise.all(library.filter(a=>!a.system&&a.type!=='web').map(async a=>({name:a.name,pkg:a.pkg,version:(await getVersions(a.id)).sort((x,y)=>(y.versionCode??0)-(x.versionCode??0))[0]})));
   if(active){setApps(snapshot?.apps??[]);setScannedAt(snapshot?.scannedAt);setReleases(versions.filter(r=>!!r.version));}
 }catch{if(active)setError('Could not load app releases.');}})();return()=>{active=false;};},[number]);
 async function scan(){setBusy(true);try{setApps(await scanApps(number));setScannedAt(Date.now());setError('');}catch(e){setError(e instanceof Error?e.message:'Scan failed');}finally{setBusy(false);}}
 async function deploy(r:Release){setBusy(true);try{
   const v=r.version;if(!v.url)throw new Error('This release has no APK URL');
   await installApp(number,{url:v.url,packageName:r.pkg,versionCode:v.versionCode,sha256:v.apkHash,runAfterInstall:false,parts:v.parts?JSON.parse(v.parts):undefined});await forceSync(number);
   toast.push('ok','Update queued','Completion appears below; rescan to verify the installed version.');
 }catch(e){toast.push('err','Update failed',e instanceof Error?e.message:'');}finally{setBusy(false);}}
 return <section className="action-group"><h3 className="action-group-title">App updates</h3><button className="btn" disabled={busy} onClick={()=>void scan()}>{busy?'Working…':'Refresh installed versions'}</button><a className="btn" href="/apps">Manage releases</a>
 {error&&<p role="alert">{error}</p>}<p className="muted">{scannedAt?`Inventory from ${new Date(scannedAt).toLocaleString()}`:'Scan the device to compare installed versions with the library.'}</p>
 <table><thead><tr><th>App</th><th>Installed</th><th>Library release</th><th>Action</th></tr></thead><tbody>{releases.map(r=>{const installed=apps.find(a=>a.pkg===r.pkg);const newer=(r.version.versionCode??0)>(installed?.versionCode??0);return <tr key={r.pkg}><td>{r.name}</td><td>{installed?`${installed.versionName} (${installed.versionCode})`:scannedAt?'Not installed':'Unknown'}</td><td>{r.version.version} ({r.version.versionCode})</td><td><button className="btn" disabled={busy||!scannedAt||!newer} onClick={()=>void deploy(r)}>{newer?'Deploy update':'Current'}</button></td></tr>;})}</tbody></table>
 <ul>{history.filter(c=>c.type==='app.install').slice(0,5).map(c=><li key={c.id}>{c.subject??'Application'}: {c.status}{c.status.toLowerCase()==='delivered'?' — awaiting confirmation (agent self-updates may need an inventory check)':''}</li>)}</ul></section>;
}
