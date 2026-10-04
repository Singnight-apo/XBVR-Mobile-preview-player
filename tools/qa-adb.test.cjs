const test=require('node:test'),assert=require('node:assert/strict'),cp=require('node:child_process');
const q=require('./qa.cjs'),state=require('./licenses-device-state.cjs');
const help='UiModeManager service (uimode) commands:\n  help\n    Print this help text.\n  night [yes|no|auto|custom_schedule|custom_bedtime]\n    Set or read night mode.\n  time [start|end] <ISO time>\n';
test('adb still rejects 255 by default and preflight rejects it for non-help commands',t=>{
 t.mock.method(cp,'spawnSync',()=>({status:255,stdout:Buffer.from(help),stderr:Buffer.from('command failed')}));
 assert.throws(()=>q.adb(['shell','cmd','uimode','help']),/command failed/);
 const shell=state.preflightShell(q);
 assert.throws(()=>shell('cmd','uimode','night'),/command failed/);
 assert.throws(()=>shell('cmd','uimode','help','extra'),/command failed/);
});
test('preflight only accepts the help 255 exception with valid help stdout',t=>{
 let stdout=help,status=255;
 t.mock.method(cp,'spawnSync',()=>({status,stdout:Buffer.from(stdout),stderr:Buffer.alloc(0)}));
 const shell=state.preflightShell(q);
 assert.equal(shell('cmd','uimode','help'),help);
 stdout='Permission denied';assert.throws(()=>shell('cmd','uimode','help'),/Cannot capture/);
 stdout='night [yes|no|auto]';assert.throws(()=>shell('cmd','uimode','help'),/Cannot capture/);
 stdout=help;status=1;assert.throws(()=>shell('cmd','uimode','help'),/adb failed/);
});
test('accepted exit code cannot hide spawn failure',t=>{
 t.mock.method(cp,'spawnSync',()=>({error:Error('spawn unavailable'),status:255,stdout:Buffer.from(help)}));
 assert.throws(()=>state.preflightShell(q)('cmd','uimode','help'),/spawn unavailable/);
});
test('complete capture succeeds with API 36 help exit 255 without accepting another command failure',t=>{
 let failedCommand;
 const outputs={'cmd locale get-app-locales top.liuwei.xbvr':'Locales for top.liuwei.xbvr for user 0 are [en-US]',
  'wm size':'Physical size: 1080x2400','wm density':'Physical density: 420','cmd uimode night':'Night mode: no',
  'cmd uimode time':'start 22:00\nend 06:00','settings get system user_rotation':'0','settings get system accelerometer_rotation':'1',
  'settings get global wifi_on':'1','settings get global mobile_data':'0'};
 t.mock.method(cp,'spawnSync',(_file,args)=>{
  const command=args.slice(3).join(' '),isHelp=command==='cmd uimode help';
  return {status:isHelp||command===failedCommand?255:0,stdout:Buffer.from(isHelp?help:outputs[command]??'null'),stderr:Buffer.alloc(0)};
 });
 const captured=state.capture(state.preflightShell(q),'top.liuwei.xbvr');
 assert.equal(captured.night,'no');assert.deepEqual(captured.times,{start:'22:00',end:'06:00'});
 failedCommand='cmd uimode night';assert.throws(()=>state.capture(state.preflightShell(q),'top.liuwei.xbvr'),/adb failed/);
});
