// All preflight commands are read-only. Unknown output is an error, never a default.
const nightKeys=['ui_night_mode','ui_night_mode_custom_type','dark_theme_custom_start_time','dark_theme_custom_end_time','ui_night_mode_override_on','ui_night_mode_override_off'];
function preflightShell(q){
 return (...args)=>{
  if(args.length!==3||args[0]!=='cmd'||args[1]!=='uimode'||args[2]!=='help')return q.shell(...args);
  // AOSP's Shell.onCommand returns -1 after handleDefaultCommands("help"),
  // so adb can report 255 despite printing valid read-only help. Scope the
  // exception to this exact command and reject diagnostic or partial stdout.
  const output=q.adb(['shell',...args],[0,255]).toString();
  if(!/^UiModeManager service \(uimode\) commands:\r?\n/.test(output.trim())||
     !/^\s*night \[yes\|no\|auto(?:\|custom_schedule\|custom_bedtime)?\]\s*$/m.test(output)||
     !output.includes('Set or read night mode.')||/(?:Error:|Exception|Permission denied|Unknown command)/i.test(output))throw Error('Cannot capture uimode command capabilities: invalid help stdout');
  return output;
 };
}
function parsed(label,output,pattern){
 const match=String(output).trim().match(pattern);
 if(!match)throw Error(`Cannot capture ${label}: ${String(output).trim()}`);
 return match;
}
function readSetting(shell,namespace,key,pattern=/^(?:null|-?\d+)$/){
 return parsed(`${namespace}/${key}`,shell('settings','get',namespace,key),pattern)[0];
}
function display(shell,kind){
 const value=kind==='size'?'[1-9]\\d*x[1-9]\\d*':'[1-9]\\d*';
 const match=parsed(`wm ${kind}`,shell('wm',kind),new RegExp(`^Physical ${kind}: (${value})(?:\\r?\\nOverride ${kind}: (${value}))?$`));
 return match[2]??null;
}
function locale(shell,pkg){
 const escaped=pkg.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
 const m=parsed('app locale',shell('cmd','locale','get-app-locales',pkg),new RegExp(`^Locales for ${escaped} for user (\\d+) are \\[([A-Za-z0-9,-]*)\\]$`));
 return {user:m[1],tags:m[2]};
}
function nightMode(shell){return parsed('night mode',shell('cmd','uimode','night'),/^Night mode: (yes|no|auto|custom_schedule|custom_bedtime)$/)[1];}
function times(shell){
 const iso='(?:[01]\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d(?:\\.\\d{1,9})?)?';
 const m=parsed('night schedule',shell('cmd','uimode','time'),new RegExp(`^start (${iso})\\r?\\nend (${iso})$`));
 return {start:m[1],end:m[2]};
}
function capture(shell,pkg){
 const state={locale:locale(shell,pkg),size:display(shell,'size'),density:display(shell,'density'),night:nightMode(shell)};
 const help=shell('cmd','uimode','help');
 if(!/night \[[^\]]*yes[^\]]*no[^\]]*auto[^\]]*\]/.test(help))throw Error('Cannot capture uimode command capabilities');
 if(state.night.startsWith('custom_')&&!help.includes(state.night))throw Error('Cannot capture unsupported custom night mode');
 if(/time \[start\|end\]/.test(help))state.times=times(shell);
 else if(state.night.startsWith('custom_'))throw Error('Cannot capture custom night schedule: time API unavailable');
 state.settings=[['system','user_rotation',/^(?:null|[0-3])$/],['system','accelerometer_rotation',/^(?:null|[01])$/],['global','wifi_on',/^[01]$/],['global','mobile_data',/^[01]$/],...nightKeys.map(key=>['secure',key])]
  .map(([namespace,key,pattern])=>({namespace,key,value:readSetting(shell,namespace,key,pattern)}));
 // uimode's shell API exposes mode/schedule but cannot reactivate an automatic
 // mode's manual override. A raw settings write does not restore its runtime flag.
 if(state.settings.some(s=>s.key.startsWith('ui_night_mode_override_')&&!['null','0'].includes(s.value)))throw Error('Cannot capture restorable active night override: shell activation API unavailable');
 return state;
}
function restore(shell,pkg,state){
 const failures=[];
 const attempt=(name,action)=>{try{action();}catch(error){failures.push({name,error});}};
 const equal=(actual,expected)=>{if(JSON.stringify(actual)!==JSON.stringify(expected))throw Error(`Restore mismatch: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);};
 for(const kind of ['size','density'])attempt(`wm ${kind}`,()=>{shell('wm',kind,state[kind]??'reset');equal(display(shell,kind),state[kind]);});
 attempt('app locale',()=>{
  const args=['cmd','locale','set-app-locales',pkg,'--user',state.locale.user];
  if(state.locale.tags)args.push('--locales',state.locale.tags);
  shell(...args);equal(locale(shell,pkg),state.locale);
 });
 if(state.times)for(const key of ['start','end'])attempt(`night time ${key}`,()=>{shell('cmd','uimode','time',key,state.times[key]);equal(times(shell)[key],state.times[key]);});
 attempt('night mode',()=>{shell('cmd','uimode','night',state.night);equal(nightMode(shell),state.night);});
 // These writes also restore absent values as absent, including secure values changed by uimode.
 for(const setting of state.settings){
  const {namespace,key,value}=setting;
  attempt(`${namespace}/${key}`,()=>{
   if(key==='wifi_on'||key==='mobile_data')shell('svc',key==='wifi_on'?'wifi':'data',value==='1'?'enable':'disable');
   else if(value==='null')shell('settings','delete',namespace,key);else shell('settings','put',namespace,key,value);
   equal(readSetting(shell,namespace,key),value);
  });
 }
 return failures;
}
async function withRestoration(body,cleanup){
 let original,value;
 try{value=await body();}catch(error){original=error;}
 let failures;
 try{failures=await cleanup();}catch(error){failures=[{name:'cleanup',error}];}
 if(failures.length){
  const errors=failures.map(f=>f.error);
  throw new AggregateError(original?[original,...errors]:errors,`${original?original.message+'; ':''}Device restoration failed: ${failures.map(f=>f.name).join(', ')}`,original?{cause:original}:undefined);
 }
 if(original)throw original;
 return value;
}
module.exports={capture,restore,withRestoration,preflightShell};
