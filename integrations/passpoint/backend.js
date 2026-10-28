/* Device credentials stay in Secure HttpOnly cookies, never JS storage. */
window.PassPointAPI=(()=>{
  let assigned=null;
  const management={checkedAt:0,connected:false,error:''};
  let healthBusy=false;
  setInterval(async()=>{
    if(!assigned || healthBusy) return;
    healthBusy=true;
    try { await request('/v1/portal/kiosk/classes'); management.connected=true; management.error=''; }
    catch(e) { management.connected=false; management.error=e.status===401?'Session rejected':'SchoolDay request failed'; }
    finally { management.checkedAt=Date.now(); healthBusy=false; }
  },60000);
  async function raw(path,method='GET',body){
    const controller=new AbortController(), timeout=setTimeout(()=>controller.abort(),15000);
    try {
      const response=await fetch(path,{method,credentials:'same-origin',cache:'no-store',signal:controller.signal,
        headers:body?{'Content-Type':'application/json'}:{},body:body?JSON.stringify(body):undefined});
      const data=await response.json().catch(()=>null);
      if(!response.ok){const error=new Error(typeof data?.detail==='string'?data.detail:`SchoolDay could not complete this request (${response.status}).`);error.status=response.status;throw error;}
      if(!data)throw new Error('SchoolDay returned an unreadable response.');
      return data;
    } catch(error){
      if(error.name==='AbortError'||error instanceof TypeError)throw new Error('Connection interrupted. The result could not be confirmed. Check Wi-Fi, then try again.');
      throw error;
    } finally {clearTimeout(timeout);}
  }
  async function request(path,method='GET',body){
    try{return await raw(path,method,body);}catch(error){
      if(error.status!==401)throw error;
      await raw('/v1/kiosk/device/session','POST');
      return raw(path,method,body);
    }
  }
  async function connect(){
    await raw('/v1/kiosk/device/session','POST');
    const classes=await request('/v1/portal/kiosk/classes');
    if(classes.length!==1)throw new Error('This PassPoint needs one assigned class. Contact your administrator.');
    assigned=classes[0];management.checkedAt=Date.now();management.connected=true;return assigned;
  }
  function classPath(){
    if(!assigned)throw new Error('PassPoint is not connected to its class yet.');
    return `/v1/portal/teacher/classes/${encodeURIComponent(assigned.classId)}`;
  }
  return {connect,raw,request,managementHealth:()=>({...management}),
    lookup:id=>request('/v1/portal/kiosk/tablet/student','POST',{studentId:id}),
    checkIn:id=>request(`${classPath()}/attendance/check-in`,'POST',{studentId:id}),
    issue:(id,destination)=>request(`${classPath()}/hall-passes`,'POST',{studentId:id,destination}),
    returnPass:id=>request(`${classPath()}/hall-passes/${encodeURIComponent(id)}/return`,'POST'),
    faculty:pin=>request('/v1/portal/kiosk/tablet/faculty','POST',{pin})};
})();
