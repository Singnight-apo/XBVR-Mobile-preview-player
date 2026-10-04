// Run only against the existing emulator. Uses synthetic app data, never a private server.
const fs=require('fs'),path=require('path'),q=require('./qa.cjs');
const out='validation/compliance-qa';fs.mkdirSync(out,{recursive:true});
const results=[];let step=0;
const snap=()=>q.snapshot(`${out}/step-${++step}.xml`);
const find=(n,text)=>n.find(x=>x.text===text||x['content-desc']===text);
const assert=(name,ok)=>{results.push({name,passed:!!ok});if(!ok)throw Error(name);console.log('PASS '+name);};
async function click(text){q.tap(find(snap(),text));await q.wait(350);}
async function shot(name){snap();q.screenshot(`${out}/${name}.png`);}
async function run(){
 const rotation=q.shell('settings','get','system','user_rotation').trim();
 const auto=q.shell('settings','get','system','accelerometer_rotation').trim();
 const night=q.shell('cmd','uimode','night').trim();
 const wifi=q.shell('settings','get','global','wifi_on').trim();
 const data=q.shell('settings','get','global','mobile_data').trim();
 try{
  q.shell('cmd','locale','set-app-locales','top.liuwei.xbvr');
  q.shell('settings','put','system','accelerometer_rotation','0');q.shell('settings','put','system','user_rotation','0');
  q.shell('logcat','-b','crash','-c');q.shell('am','force-stop','top.liuwei.xbvr');q.shell('am','start','-n','top.liuwei.xbvr/.MainActivity');await q.wait(900);
  await click('Choose or connect to a server');await click('Open-source licenses');
  let n=snap();assert('English entry reads bundled index',!!find(n,'Open-source licenses')&&n.some(x=>(x.text||'').includes('Apache-2.0.txt')));
  q.shell('svc','wifi','disable');q.shell('svc','data','disable');
  assert('Emulator network disabled for offline read',q.shell('settings','get','global','wifi_on').trim()==='0');
  await click('Choose a document');
  // Single-choice dialogs initially scroll to the selected README, so reveal earlier files.
  for(let i=0;i<6&&!find(snap(),'Apache-2.0.txt');i++){q.shell('input','swipe','540','650','540','1950','300');await q.wait(150);}
  await click('Apache-2.0.txt');n=snap();
  assert('Full Apache terms read offline',!!find(n,'Apache-2.0.txt')&&n.some(x=>(x.text||'').includes('END OF TERMS AND CONDITIONS')&&(x.text||'').includes('Limitation of Liability')));
  await shot('licenses-en-dark');
  q.shell('input','swipe','540','1870','540','650','350');await q.wait(250);let before=snap();const docBefore=before.find(x=>x.class==='android.widget.ScrollView');
  q.shell('settings','put','system','user_rotation','1');await q.wait(600);n=snap();
  assert('Rotation retains selected full document',!!find(n,'Apache-2.0.txt')&&!!find(n,'Open-source licenses'));await shot('licenses-landscape');
  q.shell('cmd','locale','set-app-locales','top.liuwei.xbvr','--locales','zh-CN');await q.wait(600);n=snap();
  assert('Chinese locale changes controls and retains legal original',!!find(n,'开源许可')&&!!find(n,'选择许可文件')&&!!find(n,'Apache-2.0.txt')&&n.some(x=>(x.text||'').includes('END OF TERMS AND CONDITIONS')));
  q.shell('settings','put','system','user_rotation','0');q.shell('cmd','uimode','night','no');await q.wait(600);await shot('licenses-zh-light');
  q.shell('wm','size','2560x1600');q.shell('wm','density','240');await q.wait(600);n=snap();assert('Tablet license controls visible',!!find(n,'开源许可')&&!!find(n,'选择许可文件'));await shot('licenses-tablet');
  q.shell('wm','size','reset');q.shell('wm','density','reset');q.shell('cmd','locale','set-app-locales','top.liuwei.xbvr');await q.wait(600);
  await click('Back to library');n=snap();
  // A background library reload can report the deliberately disabled server connection.
  // This alert is unrelated to the bundled license reader; acknowledge it before checking return.
  if(find(n,'Unable to complete')&&find(n,'OK')){await click('OK');n=snap();}
  assert('Back returns to existing library',!!find(n,'Library'));
  const crashes=q.adb(['logcat','-b','crash','-d']).toString();fs.writeFileSync(`${out}/crash.txt`,crashes);assert('No app crash in license flow',!crashes.includes('top.liuwei.xbvr'));
 }finally{
  q.shell('wm','size','reset');q.shell('wm','density','reset');q.shell('settings','put','system','user_rotation',rotation);q.shell('settings','put','system','accelerometer_rotation',auto);
  q.shell('cmd','uimode','night',night.includes('yes')?'yes':night.includes('auto')?'auto':'no');
  q.shell('cmd','locale','set-app-locales','top.liuwei.xbvr');q.shell('svc','wifi',wifi==='1'?'enable':'disable');q.shell('svc','data',data==='1'?'enable':'disable');
  fs.writeFileSync(`${out}/results.json`,JSON.stringify(results,null,2)+'\n');
 }
}
run().catch(e=>{console.error(e);process.exitCode=1;});

