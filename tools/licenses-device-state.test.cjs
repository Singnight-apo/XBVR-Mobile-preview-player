const test=require('node:test'),assert=require('node:assert/strict');
const state=require('./licenses-device-state.cjs');
const pkg='top.liuwei.xbvr';
function device(options={}){
 const calls=[];
 const settings=new Map(Object.entries({'system/user_rotation':'null','system/accelerometer_rotation':'1','global/wifi_on':'1','global/mobile_data':'0',...options.settings}));
 let locale=options.locale??'fr-FR,de-DE',size=options.size??null,density=options.density??null,night=options.night??'auto';
 let start='22:15:30',end='06:20';
 const shell=(...a)=>{
  calls.push(a);
  if(options.fail?.(a))throw Error('injected failure');
  if(a[0]==='settings'){
   const key=a[2]+'/'+a[3];
   if(a[1]==='get')return settings.get(key)??'null';
   if(a[1]==='put')settings.set(key,a[4]);
   if(a[1]==='delete')settings.delete(key);
   return '';
  }
  if(a[0]==='wm'){
   if(a.length===2)return `Physical ${a[1]}: ${a[1]==='size'?'1080x2400':'420'}\n`+(a[1]==='size'?size?`Override size: ${size}`:'':density?`Override density: ${density}`:'');
   if(a[1]==='size')size=a[2]==='reset'?null:a[2];else density=a[2]==='reset'?null:a[2];return '';
  }
  if(a[0]==='svc'){settings.set(a[1]==='wifi'?'global/wifi_on':'global/mobile_data',a[2]==='enable'?'1':'0');return '';}
  if(a[1]==='locale'){
   if(a[2]==='get-app-locales')return `Locales for ${pkg} for user 0 are [${locale}]`;
   const index=a.indexOf('--locales');locale=index<0?'':a[index+1];return '';
  }
  if(a[2]==='help')return 'night [yes|no|auto|custom_schedule|custom_bedtime]\ntime [start|end] <ISO time>';
  if(a[2]==='night'){if(a[3])night=a[3];return 'Night mode: '+night;}
  if(a[2]==='time'){if(a[3]==='start')start=a[4];if(a[3]==='end')end=a[4];return `start ${start}\nend ${end}`;}
  throw Error('unexpected '+a.join(' '));
 };
 return {shell,calls,settings,values:()=>({locale,size,density,night,start,end})};
}
test('capture is read-only and restores original locale and display overrides',()=>{
 const d=device({size:'1440x2560',density:'320'}),saved=state.capture(d.shell,pkg);
 assert.ok(d.calls.every(a=>!['put','delete','set-app-locales'].includes(a[1])&&!a.includes('reset')));
 d.shell('wm','size','2560x1600');d.shell('wm','density','240');d.shell('cmd','locale','set-app-locales',pkg);
 assert.deepEqual(state.restore(d.shell,pkg,saved),[]);
 assert.equal(d.values().locale,'fr-FR,de-DE');assert.equal(d.values().size,'1440x2560');assert.equal(d.values().density,'320');
 assert.equal(d.settings.has('system/user_rotation'),false);
});
for(const night of ['yes','no','auto','custom_schedule','custom_bedtime'])test('restores '+night+' with no display override and empty locale',()=>{
 const d=device({night,locale:''}),saved=state.capture(d.shell,pkg);
 d.shell('cmd','uimode','night','no');d.shell('cmd','uimode','time','start','01:00');d.shell('wm','size','2560x1600');
 assert.deepEqual(state.restore(d.shell,pkg,saved),[]);
 assert.equal(d.values().night,night);assert.equal(d.values().start,'22:15:30');assert.equal(d.values().size,null);assert.equal(d.values().locale,'');
});
test('one restore failure does not prevent the remaining restore attempts',()=>{
 let fail=false;const d=device({fail:a=>fail&&a.join(' ')==='wm size reset'}),saved=state.capture(d.shell,pkg);
 fail=true;const errors=state.restore(d.shell,pkg,saved);
 assert.equal(errors.length,1);assert.equal(errors[0].name,'wm size');
 assert.ok(d.calls.some(a=>a.join(' ')==='svc data disable'));
 assert.ok(d.calls.some(a=>a.join(' ')==='settings delete system user_rotation'));
});
test('malformed preflight values reject before any mutation',()=>{
 for(const bad of ['Night mode: unknown','Night mode: custom','Permission denied']){
  const d=device(),shell=(...a)=>a.join(' ')==='cmd uimode night'?bad:d.shell(...a);
  assert.throws(()=>state.capture(shell,pkg),/Cannot capture/);
  assert.ok(d.calls.every(a=>a[0]!=='svc'&&a[1]!=='put'&&a[1]!=='delete'&&a[2]!=='set-app-locales'));
 }
 const d=device({settings:{'global/mobile_data':'null'}});assert.throws(()=>state.capture(d.shell,pkg),/mobile_data/);
});
test('unparseable locale, display, time and setting output abort preflight',()=>{
 for(const command of ['cmd locale get-app-locales '+pkg,'wm size','wm density','cmd uimode help','cmd uimode time','settings get system user_rotation','settings get system accelerometer_rotation','settings get secure ui_night_mode']){
  const d=device(),shell=(...a)=>a.join(' ')===command?'unparseable output':d.shell(...a);
  assert.throws(()=>state.capture(shell,pkg),/Cannot capture/,command);
  assert.ok(d.calls.every(a=>a[0]!=='svc'&&a[1]!=='put'&&a[1]!=='delete'&&a[2]!=='set-app-locales'));
 }
});
test('older API without time support accepts fixed night mode and rejects custom',()=>{
 for(const night of ['no','custom_schedule']){
  const d=device({night}),shell=(...a)=>a.join(' ')==='cmd uimode help'?'night [yes|no|auto|custom_schedule]':d.shell(...a);
  if(night==='no'){const saved=state.capture(shell,pkg);assert.equal(saved.times,undefined);assert.deepEqual(state.restore(shell,pkg,saved),[]);}
  else assert.throws(()=>state.capture(shell,pkg),/time API unavailable/);
 }
});
test('active automatic night overrides abort before mutation because shell cannot restore activation',()=>{
 for(const key of ['ui_night_mode_override_on','ui_night_mode_override_off']){
  const d=device({settings:{['secure/'+key]:'1'}});
  assert.throws(()=>state.capture(d.shell,pkg),/active night override/);
  assert.ok(d.calls.every(a=>a[0]!=='svc'&&a[1]!=='put'&&a[1]!=='delete'&&a[2]!=='set-app-locales'));
 }
});
test('a silently ignored restore command is a failure and does not stop other restores',()=>{
 const d=device({size:'1440x2560'}),saved=state.capture(d.shell,pkg);
 d.shell('wm','size','2560x1600');
 const shell=(...a)=>a.join(' ')==='wm size 1440x2560'?'':d.shell(...a);
 const errors=state.restore(shell,pkg,saved);
 assert.equal(errors.length,1);assert.match(errors[0].error.message,/Restore mismatch/);
 assert.ok(d.calls.some(a=>a.join(' ')==='svc data disable'));
});
test('cleanup failure keeps original failure as cause and fails an otherwise passing flow',async()=>{
 const original=Error('UI assertion failed'),cleanup=Error('restore failed');
 await assert.rejects(state.withRestoration(async()=>{throw original;},()=>[{name:'wifi',error:cleanup}]),e=>e.cause===original&&e.errors.includes(original)&&e.errors.includes(cleanup));
 await assert.rejects(state.withRestoration(async()=>{},()=>[{name:'wifi',error:cleanup}]),AggregateError);
 await assert.rejects(state.withRestoration(async()=>{throw original;},()=>[]),e=>e===original);
});
