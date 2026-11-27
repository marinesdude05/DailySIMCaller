// Daily SIM Caller V52 - administrator-owned Apps Script web application.
// Configure Script Properties CALL_LOG_SHEET_ID and DEVICE_TRACKING_SHEET_ID.
// Deploy as owner; anonymous web access is authenticated by individual device tokens.
// Never place tokens or Google credentials in the APK or repository.
const HEAD = ['call_id','device_id','phone_number','date','time','time_zone','connected_seconds','result'];
const DEVICE = /^C9-[A-F0-9]{8}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
function digest_(s) {
  return Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256,s,Utilities.Charset.UTF_8)
    .map(b => ('0'+((b+256)%256).toString(16)).slice(-2)).join('');
}
function check_(condition,code) { if (!condition) throw new Error(code); }
function output_(obj) { return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON); }
function doGet() { return output_({ok:true,service:'Daily SIM Caller',protocol:1}); }
// Admin-only editor function. Set ENROLL_DEVICE_ID and ENROLL_INSTALLATION_ID in
// Script Properties before running. Copy the generated token from execution log
// to the authorized device using a private provisioning channel; then clear log access.
function issueDeviceCredential() {
  const p=PropertiesService.getScriptProperties();
  const device=p.getProperty('ENROLL_DEVICE_ID'), installation=p.getProperty('ENROLL_INSTALLATION_ID');
  check_(DEVICE.test(device||'') && UUID.test(installation||''),'INVALID_ID');
  const lock=LockService.getScriptLock(); lock.waitLock(10000);
  try {
    check_(!p.getProperty('device:'+device),'ALREADY_ENROLLED');
    const token=Utilities.getUuid()+Utilities.getUuid();
    p.setProperty('device:'+device,JSON.stringify({installation,hash:digest_(token),enabled:true}));
    p.deleteProperty('ENROLL_DEVICE_ID'); p.deleteProperty('ENROLL_INSTALLATION_ID');
    // The editor execution log is restricted to administrators. Treat as secret.
    console.log(token);
  } finally {lock.releaseLock();}
}
function doPost(e) {
  const lock=LockService.getScriptLock();
  try {
    const raw=e && e.postData && e.postData.contents;
    check_(typeof raw==='string' && raw.length<=32000,'INVALID_REQUEST');
    const b=JSON.parse(raw);
    check_(b && DEVICE.test(b.deviceId||'') && UUID.test(b.installationId||'') &&
      typeof b.token==='string' && b.token.length===72 && b.consent===true,'UNAUTHORIZED');
    check_(lock.tryLock(10000),'RETRY');
    const p=PropertiesService.getScriptProperties();
    const saved=JSON.parse(p.getProperty('device:'+b.deviceId)||'null');
    check_(saved && saved.enabled===true && saved.installation===b.installationId &&
      saved.hash===digest_(b.token),'UNAUTHORIZED');
    check_(b.action==='register' || b.action==='sync','INVALID_ACTION');
    check_(typeof b.version==='string' && /^[0-9A-Za-z. _-]{1,64}$/.test(b.version),'INVALID_VERSION');
    const calls=b.action==='sync'?b.calls:[];
    check_(Array.isArray(calls)&&calls.length<=20,'INVALID_BATCH');
    const seen=new Set();
    for(const c of calls) {
      check_(c && Number.isSafeInteger(c.id)&&c.id>=1&&c.id<=499999&&!seen.has(c.id),'INVALID_CALL_ID');
      seen.add(c.id);
      check_(typeof c.date==='string'&&/^\d{4}-\d{2}-\d{2}$/.test(c.date)&&
        typeof c.time==='string'&&/^\d{2}:\d{2}:\d{2}$/.test(c.time)&&
        typeof c.timeZone==='string'&&/^[A-Za-z0-9_+:/-]{1,80}$/.test(c.timeZone)&&
        Number.isSafeInteger(c.connectedSeconds)&&c.connectedSeconds>=0&&c.connectedSeconds<=86400&&
        typeof c.outcome==='string'&&/^[A-Za-z0-9_ -]{1,64}$/.test(c.outcome),'INVALID_CALL');
    }
    const track=SpreadsheetApp.openById(p.getProperty('DEVICE_TRACKING_SHEET_ID'));
    const events=track.getSheetByName('Device Events');
    check_(events && events.getMaxColumns()>=6,'TRACKING_SCHEMA');
    const last=events.getLastRow();
    const existing=last>=5?events.getRange(5,2,last-4,2).getValues():[];
    if(!existing.some(r=>String(r[0])===b.installationId && r[1]==='first_open')){
      events.getRange(Math.max(5,last+1),1,1,6).setValues([[new Date().toISOString(),b.installationId,'first_open',b.version,'Yes','Opted-in test installation; central sync']]);
    }
    const confirmed=[];
    if(calls.length){
      const book=SpreadsheetApp.openById(p.getProperty('CALL_LOG_SHEET_ID'));
      let tab=book.getSheetByName(b.deviceId);
      if(!tab){tab=book.insertSheet(b.deviceId);tab.getRange(1,1,1,8).setValues([HEAD]);}
      check_(tab.getRange(1,1,1,8).getDisplayValues()[0].every((v,i)=>v===HEAD[i]),'CALL_SCHEMA');
      const maxRow=Math.max(...calls.map(c=>c.id+1));
      // Prevent a malformed authenticated client from expanding a tab by huge amounts.
      check_(maxRow<=tab.getMaxRows()+1000,'ROW_LIMIT');
      if(maxRow>tab.getMaxRows())tab.insertRowsAfter(tab.getMaxRows(),maxRow-tab.getMaxRows());
      const pending=[];
      for(const c of calls){
        const row=c.id+1;
        const a=tab.getRange(row,1,1,2), d=tab.getRange(row,4,1,5);
        const av=a.getValues()[0],dv=d.getValues()[0];
        const expected=[c.date,c.time,c.timeZone,c.connectedSeconds,c.outcome];
        const used=av.concat(dv).some(v=>v!=='')||a.getFormulas()[0].concat(d.getFormulas()[0]).some(Boolean);
        if(used){
          check_(String(av[0])===String(c.id)&&av[1]===b.deviceId&&
            dv.every((v,i)=>String(v)===String(expected[i])),'ROW_CONFLICT');
        }else pending.push({row,expected,id:c.id});
        confirmed.push(c.id);
      }
      // Never read or write column C (dialed numbers). Atomicity across the two
      // ranges requires the Advanced Sheets service; use one batch per request.
      if(pending.length){
        const data=[];
        for(const x of pending){
          data.push({range:"'"+b.deviceId+"'!A"+x.row+":B"+x.row,values:[[x.id,b.deviceId]]});
          data.push({range:"'"+b.deviceId+"'!D"+x.row+":H"+x.row,values:[x.expected]});
        }
        const result=Sheets.Spreadsheets.Values.batchUpdate({valueInputOption:'RAW',data},book.getId());
        check_(result.responses && result.responses.length===data.length,'RETRY');
      }
    }
    SpreadsheetApp.flush();
    return output_({ok:true,registered:true,confirmed});
  }catch(err){
    const code=String(err.message||'');
    const allowed=['INVALID_REQUEST','UNAUTHORIZED','RETRY','INVALID_ACTION','INVALID_VERSION','INVALID_BATCH','INVALID_CALL_ID','INVALID_CALL','TRACKING_SCHEMA','CALL_SCHEMA','ROW_LIMIT','ROW_CONFLICT'];
    return output_({ok:false,error:allowed.includes(code)?code:'SERVER_ERROR'});
  }finally{if(lock.hasLock())lock.releaseLock();}
}
