import {useState} from 'react';
import {copyConfiguration,listConfigurations,getConfiguration,getConfigurationApps,saveConfiguration} from '../api/configurations';
import {listApplications,getVersions} from '../api/applications';
import {bulkSetConfiguration} from '../api/devices';
import {forceSync} from '../api/commands';
import {useToast} from '../ui/toast';
export function PersistentKiosk({device}:{device:{id?:number;number:string;configurationId?:number}}) {
 const [busy,setBusy]=useState(false); const [open,setOpen]=useState(false); const toast=useToast();
 async function apply(){setBusy(true);try{
   if(!device.id||!device.configurationId)throw new Error('Device configuration is unavailable');
   const app=(await listApplications()).find(a=>a.pkg==='one.schoolday.passpoint.prototype');
   if(!app)throw new Error('Add PassPoint to the app library first');
   const versions=await getVersions(app.id);
   const version=versions.sort((a,b)=>(b.versionCode??0)-(a.versionCode??0))[0];
   if(!version)throw new Error('PassPoint has no library version');
   const name=`PassPoint kiosk — ${device.number}`;
   let target=(await listConfigurations()).find(c=>c.name===name);
   if(!target){await copyConfiguration(device.configurationId,name,'Persistent strict SchoolDay kiosk');target=(await listConfigurations()).find(c=>c.name===name);}
   if(!target)throw new Error('Could not create the device kiosk configuration');
   const cfg=await getConfiguration(target.id);
   const apps=await getConfigurationApps(target.id);
   const others=apps.filter(a=>a.pkg!==app.pkg).map(a=>({...a,showIcon:false}));
   await saveConfiguration({...cfg,kioskMode:true,mainAppId:version.id,kioskHome:false,kioskRecents:false,kioskNotifications:false,kioskSystemInfo:false,kioskKeyguard:false,kioskExit:false,password:'',kioskLockVolume:false,
     applications:[...others,{id:app.id,applicationId:app.id,name:app.name,pkg:app.pkg,usedVersionId:version.id,action:1,showIcon:true,remove:false}]});
   await bulkSetConfiguration([device.id],target.id);await forceSync(device.number);
   toast.push('ok','Persistent kiosk assigned','Check configuration status for In sync. Reload this page to see the assignment.');setOpen(false);
 }catch(e){toast.push('err','Kiosk configuration failed',e instanceof Error?e.message:'');}finally{setBusy(false);}}
 return <section className="action-group"><h3 className="action-group-title">Persistent kiosk</h3><p className="muted">Use an assigned configuration to restore strict PassPoint kiosk after boot and agent updates. A dedicated copy preserves the current configuration and affects only this device. On-device exit is disabled.</p><button className="btn" disabled={busy} onClick={()=>setOpen(true)}>Assign persistent PassPoint kiosk</button>
 {open&&<div className="modal-backdrop" role="dialog" aria-modal="true"><div className="modal"><h3>Assign persistent kiosk</h3><p>Device: {device.number}. Pin PassPoint, disable Home, Recents and notifications, and retain remote portal access. The latest library version of PassPoint will be assigned.</p><div className="modal-actions"><button className="btn" disabled={busy} onClick={()=>setOpen(false)}>Cancel</button><button className="btn btn-primary" disabled={busy} onClick={()=>void apply()}>{busy?'Applying…':'Apply configuration'}</button></div></div></div>}
 </section>;
}
