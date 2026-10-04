const fs=require('fs'),path=require('path'),cp=require('child_process');
const adbPath=path.resolve('toolchain/sdk/platform-tools/adb.exe');
function adb(args,acceptedExitCodes=[0]){const serial=process.env.XBVR_QA_SERIAL||'emulator-5580';if(!/^emulator-\d+$/.test(serial))throw Error('QA only targets an emulator');const r=cp.spawnSync(adbPath,['-s',serial,...args],{encoding:null,timeout:30000,windowsHide:true,maxBuffer:32*1024*1024});if(r.error||!acceptedExitCodes.includes(r.status))throw Error(r.error?.message||r.stderr?.toString()||'adb failed');return r.stdout;}
function shell(...args){return adb(['shell',...args]).toString();}
function decode(s){return s.replace(/&quot;/g,'"').replace(/&amp;/g,'&').replace(/&lt;/g,'<').replace(/&gt;/g,'>');}
function snapshot(file='validation/current-ui.xml'){shell('rm','-f','/sdcard/xbvr-ui.xml');shell('uiautomator','dump','/sdcard/xbvr-ui.xml');const xml=adb(['exec-out','cat','/sdcard/xbvr-ui.xml']).toString();if(!xml.includes('<hierarchy'))throw Error('No current UI snapshot; never reuse stale XML');fs.writeFileSync(file,xml);return [...xml.matchAll(/<node\b(?:[^>"']|"[^"]*"|'[^']*')*>/g)].map(m=>{const p={};for(const a of m[0].matchAll(/([\w-]+)=(?:"([^"]*)"|'([^']*)')/g))p[a[1]]=decode(a[2]??a[3]);return p;});}
function tap(node){if(!node)throw Error('UI target missing');const n=node.bounds.match(/\d+/g).map(Number);shell('input','tap',String(Math.round((n[0]+n[2])/2)),String(Math.round((n[1]+n[3])/2)));}
function click(text){const nodes=snapshot();tap(nodes.find(n=>n['content-desc']===text)||nodes.find(n=>n.text===text&&n.class!=='android.widget.EditText')||nodes.find(n=>n.text===text));}
function screenshot(file){fs.writeFileSync(file,adb(['exec-out','screencap','-p']));}
async function wait(ms){await new Promise(r=>setTimeout(r,ms));}
module.exports={adb,shell,snapshot,tap,click,screenshot,wait};
if(require.main===module){const action=process.argv[2];if(action==='snapshot')console.log(snapshot().filter(n=>n.text||n.class==='android.widget.EditText').map(n=>({text:n.text,class:n.class,bounds:n.bounds})));else if(action==='click')click(process.argv[3]);else if(action==='screenshot')screenshot(process.argv[3]);}
