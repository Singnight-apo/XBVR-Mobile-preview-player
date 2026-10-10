package top.liuwei.xbvr.ui.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import top.liuwei.xbvr.R;
import top.liuwei.xbvr.domain.ServerProfile;
import top.liuwei.xbvr.data.XbvrManagementApi;
import top.liuwei.xbvr.data.XbvrTaskMonitor;
import top.liuwei.xbvr.data.BitmapCoverRepository;
import top.liuwei.xbvr.data.HttpTransport;
import top.liuwei.xbvr.data.XbvrProtocol;
import top.liuwei.xbvr.domain.Models.Entry;
import okhttp3.OkHttpClient;
import top.liuwei.xbvr.ui.common.Ui;

/** Native server management. Requests and mutations remain bound to the captured profile. */
final class ManagementView {
    private final MainView view;
    private final Activity activity;
    private final Runnable leave;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService imageIo = Executors.newFixedThreadPool(2);
    private BitmapCoverRepository covers;
    private OkHttpClient coverClient;
    private long coverEpoch;
    private final HashMap<String,Integer> positions = new HashMap<>();
    private ServerProfile profile;
    private XbvrManagementApi api;
    private LinearLayout root, body;
    private ScrollView scroll;
    private EditText filter;
    private String route = "home", siteFilter = "", filename = "", query = "", notice = "", errorNotice = "";
    private boolean enabledOnly, matched, foreground, closed, loading, mutating, uncertain;
    private long selectedFile = -1;
    private int generation, monitorGeneration;
    private String pendingRestore, renderedRoute="home", filterRoute="", restoredScene="";
    private JSONObject capabilities=new JSONObject();
    private boolean recoveryRead, restoringScroll;
    private long updatedAt;
    private XbvrTaskMonitor monitor;
    private boolean connected;
    private final HashMap<String,Integer> taskStates = new HashMap<>();
    private final java.util.HashSet<String> submittedTasks = new java.util.HashSet<>();
    private JSONArray sites = new JSONArray(), files = new JSONArray(), scenes = new JSONArray();
    private JSONObject storage = new JSONObject(), state = new JSONObject(), file = new JSONObject(), scene = new JSONObject();
    private final Runnable poll = new Runnable() {
        public void run() {
            if (foreground && !closed && route.equals("tasks")) {
                if (!loading && !mutating) readTasks();
                main.postDelayed(this, 3000);
            }
        }
    };

    ManagementView(MainView view, Runnable back) {
        this.view = view; activity = view.activity(); leave = back;
        try { profile = view.actions().currentProfile(); api = new XbvrManagementApi(profile); }
        catch (Exception ignored) { }
    }
    View create() {
        capture(); bindProfile(); generation++; loading=false;
        if(pendingRestore!=null&&profile!=null) {String saved=pendingRestore;pendingRestore=null;restoreState(saved);}
        root = Ui.column(activity);
        TextView identity = view.label(view.tr(R.string.management_server, identity()), 13, view.colors().muted);
        identity.setPadding(dp(20), dp(12), dp(20), dp(12));
        root.addView(identity);
        scroll = new ScrollView(activity);restoringScroll=true;filter=null;
        body = Ui.column(activity); body.setPadding(dp(20), dp(4), dp(20), dp(24));
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        render(); load(); watch(); return root;
    }
    boolean back() {
        capture();
        if (route.equals("home")) return false;
        go(route.equals("compare") ? "search" : route.equals("search") ? "file" : route.equals("file") ? "matching" : "home");
        return true;
    }
    void resume() { foreground = true; main.removeCallbacks(poll); watch(); if (route.equals("tasks")) poll.run(); }
    void pause() { foreground = false; main.removeCallbacks(poll); stopWatch(); }
    void close() { closed = true; generation++; pause(); io.shutdownNow(); clearCovers(); imageIo.shutdownNow(); if(api!=null)api.close(); }
    private void clearCovers() {
        coverEpoch++;
        if(covers!=null)covers.clear();covers=null;
        if(coverClient!=null) {coverClient.dispatcher().cancelAll();coverClient.connectionPool().evictAll();}
        coverClient=null;
    }
    String savedState() {
        capture(); JSONObject saved = new JSONObject();
        try {
            saved.put("profile", profile == null ? "" : profile.id).put("route",route)
                .put("siteFilter",siteFilter).put("filename",filename).put("query",query)
                .put("enabledOnly",enabledOnly).put("matched",matched).put("file",selectedFile)
                .put("scene",scene.optString("scene_id")).put("uncertain", uncertain || mutating)
                .put("submittedScan",submittedTasks.contains("rescan")).put("submittedScrape",submittedTasks.contains("scrape"));
            JSONObject offsets = new JSONObject(); for (String key : positions.keySet()) offsets.put(key,positions.get(key));
            saved.put("scroll",offsets);
        } catch (Exception ignored) { }
        return saved.toString();
    }
    void restoreState(String value) {
        if(profile==null) {pendingRestore=value;return;}
        try {
            JSONObject saved = new JSONObject(value);
            if (profile == null || !profile.id.equals(saved.optString("profile"))) return;
            String next = saved.optString("route","home");
            if (java.util.Arrays.asList("home","maintenance","scrapers","matching","file","search","compare","tasks").contains(next)) route=next;
            siteFilter=saved.optString("siteFilter"); filename=saved.optString("filename"); query=saved.optString("query");
            enabledOnly=saved.optBoolean("enabledOnly"); matched=saved.optBoolean("matched"); selectedFile=saved.optLong("file",-1);
            uncertain=saved.optBoolean("uncertain");
            if(saved.optBoolean("submittedScan"))submittedTasks.add("rescan");if(saved.optBoolean("submittedScrape"))submittedTasks.add("scrape");
            // Re-read comparison metadata; retain only the selected external key in saved state.
            restoredScene=saved.optString("scene");
            JSONObject offsets=saved.optJSONObject("scroll");
            if (offsets!=null) for (java.util.Iterator<String> i=offsets.keys();i.hasNext();) { String key=i.next();positions.put(key,offsets.optInt(key)); }
        } catch(Exception ignored) { }
    }
    private void bindProfile() {
        ServerProfile next=null;try {next=view.actions().currentProfile();}catch(Exception ignored) { }
        if(next==null) {if(api!=null)api.close();api=null;profile=null;stopWatch();clearCovers();return;}
        if(sameProfile(profile,next)&&api!=null)return;
        generation++;stopWatch();clearCovers();if(api!=null)api.close();
        profile=next;
        try {api=new XbvrManagementApi(next);}
        catch(IllegalArgumentException invalid) {api=null;}
        route="home";loading=false;mutating=false;uncertain=false;recoveryRead=false;
        positions.clear();submittedTasks.clear();sites=new JSONArray();files=new JSONArray();scenes=new JSONArray();
        file=new JSONObject();scene=new JSONObject();storage=new JSONObject();state=new JSONObject();selectedFile=-1;
        filename="";siteFilter="";query="";notice="";errorNotice=api==null?view.tr(R.string.management_read_failed):"";capabilities=new JSONObject();
    }
    private boolean sameProfile(ServerProfile a,ServerProfile b) {
        return a!=null&&b!=null&&java.util.Objects.equals(a.id,b.id)&&java.util.Objects.equals(a.base,b.base)
            &&java.util.Objects.equals(a.basicUser,b.basicUser)&&java.util.Objects.equals(a.basicPassword,b.basicPassword)
            &&java.util.Objects.equals(a.user,b.user)&&java.util.Objects.equals(a.password,b.password);
    }
    private boolean isCurrent(ServerProfile captured) {
        try {return captured!=null&&captured.id.equals(view.actions().activeProfileId())&&sameProfile(captured,view.actions().currentProfile());}
        catch(Exception ignored) {return false;}
    }
    private String identity() {
        if (profile == null) return view.tr(R.string.management_no_server);
        try { java.net.URI u=new java.net.URI(profile.base);return new java.net.URI(u.getScheme(),null,u.getHost(),u.getPort(),u.getPath(),null,null).toString(); }
        catch(Exception e) { return view.tr(R.string.management_server_configured); }
    }
    private int dp(int n) { return Ui.dp(activity,n); }
    private void capture() {
        if (scroll!=null&&!restoringScroll) positions.put(renderedRoute,scroll.getScrollY());
        if (filter!=null) { String text=filter.getText().toString(); if(filterRoute.equals("scrapers"))siteFilter=text;else if(filterRoute.equals("matching"))filename=text;else if(filterRoute.equals("search"))query=text; }
    }
    private void go(String next) {
        capture(); generation++; loading=false; route=next; render(); load();
        main.removeCallbacks(poll); watch(); if(foreground&&route.equals("tasks")) main.postDelayed(poll,3000);
    }
    private boolean valid(int token) { return !closed && token==generation && profile!=null && isCurrent(profile); }
    private interface Work { Object run(XbvrManagementApi endpoint) throws Exception; }
    private interface Done { void apply(Object result) throws Exception; }
    private void read(Work work, Done done) { request(work,done,false); }
    private void request(Work work, Done done, boolean write) {
        if (closed||api==null||loading||mutating|| (write&&uncertain)) return;
        capture(); final int token=generation; final ServerProfile boundProfile=profile;final XbvrManagementApi boundApi=api; loading=!write; mutating=write;if(write)recoveryRead=false; render();
        io.execute(() -> {
            Object result=null; Exception failure=null;JSONObject discovered=null;
            try { if (!isCurrent(boundProfile)) return; if(!write&&capabilities.optJSONObject("paths")==null)try {discovered=boundApi.capabilities();}catch(Exception ignored) { } result=work.run(boundApi); }
            catch(Exception e) { failure=e; }
            final Object response=result; final Exception error=failure;final JSONObject discoveredCapabilities=discovered;
            main.post(() -> {
                // A mutation still owns its duplicate-click guard when the user changes routes.
                if(write && profile!=null && sameProfile(boundProfile,profile)) {
                    mutating=false;
                    if(!closed && isCurrent(profile) && token!=generation) {
                        uncertain=error!=null;if(error!=null)recoveryRead=false;notice=view.tr(error==null?R.string.management_submitted:R.string.management_uncertain);render();return;
                    }
                }
                if(!valid(token)) return;
                loading=false;if(discoveredCapabilities!=null)capabilities=discoveredCapabilities;updatedAt=System.currentTimeMillis();
                if(error!=null) {
                    if(write) { uncertain=true;recoveryRead=false;notice=view.tr(R.string.management_uncertain); }
                    else errorNotice=errorText(error);
                } else try { errorNotice=""; done.apply(response); } catch(Exception e) { errorNotice=view.tr(R.string.management_read_failed); }
                render();
            });
        });
    }
    private void load() {
        if(api==null)return;
        switch(route) {
            case "home": read(endpoint -> endpoint.capabilities(),r->capabilities=(JSONObject)r);break;
            case "maintenance": read(endpoint -> endpoint.storage(), r -> storage=(JSONObject)r); break;
            case "scrapers": read(endpoint -> endpoint.sites(), r -> sites=(JSONArray)r); break;
            case "matching": read(endpoint -> endpoint.files(matched,filename), r -> files=(JSONArray)r); break;
            case "file": read(endpoint -> endpoint.file(selectedFile), r -> file=(JSONObject)r); break;
            case "search": read(endpoint -> new Object[]{endpoint.file(selectedFile),endpoint.searchScenes(query)}, r -> {
                Object[] pair=(Object[])r;file=(JSONObject)pair[0]; scenes=((JSONObject)pair[1]).optJSONArray("scenes");if(scenes==null)scenes=new JSONArray(); });break;
            case "compare":
                if(scene.optString("scene_id").isEmpty()&&!restoredScene.isEmpty())read(endpoint -> new Object[]{endpoint.file(selectedFile),endpoint.searchScenes(query)},r->{
                    Object[] pair=(Object[])r;file=(JSONObject)pair[0];JSONArray found=((JSONObject)pair[1]).optJSONArray("scenes");
                    if(found!=null)for(int i=0;i<found.length();i++){JSONObject item=found.optJSONObject(i);if(item!=null&&restoredScene.equals(item.optString("scene_id"))){scene=item;break;}}
                });
                break;
            case "tasks": readTasks(); break;
            default:break;
        }
    }
    private void readTasks() { read(endpoint -> endpoint.state(), r -> state=(JSONObject)r); }
    private void render() {
        if(body==null)return;
        capture();
        if(covers!=null)covers.invalidate(coverEpoch);
        coverEpoch++;
        body.removeAllViews(); filter=null;renderedRoute=route;
        if (!route.equals("home")) button(R.string.management_back, () -> back(), false);
        if(profile==null) { text(view.tr(R.string.management_no_server));return; }
        if (!isCurrent(profile)) { text(view.tr(R.string.management_server_changed));button(R.string.management_back,leave,false);return; }
        if(!notice.isEmpty())text(notice);
        if(!errorNotice.isEmpty())text(errorNotice);
        if(uncertain) {text(view.tr(R.string.management_uncertain));button(R.string.management_reconcile,this::reconcile,false);
            if(recoveryRead)button(R.string.management_acknowledge,this::acknowledge,false);}
        if(loading||mutating)text(view.tr(R.string.management_loading));
        if(capabilities.optJSONObject("paths")==null)text(view.tr(R.string.management_capability_unknown));
        else if(!route.equals("home")&&!route.equals("tasks"))text(view.tr(R.string.management_capability_hint));
        switch(route) {
            case "home": home();break;
            case "maintenance": maintenance();break;
            case "scrapers": scrapers();break;
            case "matching": matching();break;
            case "file": detail();break;
            case "search": search();break;
            case "compare": compare();break;
            case "tasks": tasks();break;
        }
        final int y=positions.getOrDefault(route,0);final ScrollView mountedScroll=scroll;restoringScroll=true;scroll.post(() -> {if(scroll!=mountedScroll)return;scroll.scrollTo(0,y);restoringScroll=false;});
    }
    private void home() {
        button(R.string.management_check_capabilities,this::load,false);
        button(R.string.management_maintenance,()->go("maintenance"),false);
        button(R.string.management_scrapers,()->go("scrapers"),false);
        button(R.string.management_matching,()->go("matching"),false);
        button(R.string.management_tasks,()->go("tasks"),false);
    }
    private void maintenance() {
        text(view.tr(R.string.management_storage_readonly));
        button(R.string.management_read_storage,this::load,false);
        button(R.string.management_scan_all,()->confirm(R.string.management_scan_all,()->submit("rescan",endpoint -> {endpoint.rescan(null);return null;})),true);
        JSONArray volumes=storage.optJSONArray("volumes");
        if(volumes!=null)for(int i=0;i<volumes.length();i++) {
            JSONObject volume=volumes.optJSONObject(i);if(volume==null)continue;
            text(value(volume,"path","name"));
            if(volume.has("id")) { final int id=volume.optInt("id");button(R.string.management_scan_directory,()->confirm(R.string.management_scan_directory,()->submit("rescan",endpoint -> {endpoint.rescan(id);return null;})),true); }
        }
        button(R.string.management_refresh_scenes,()->confirm(R.string.management_refresh_scenes,()->submit("",endpoint -> {endpoint.refreshScenes();return null;})),true);
        text(view.tr(R.string.management_reload_hint));
        button(R.string.management_reload_app,()->view.actions().refresh(),false);
    }
    private void scrapers() {
        field(R.string.management_search_sites,siteFilter);
        CheckBox enabled=new CheckBox(activity);enabled.setText(view.tr(R.string.management_enabled_only));enabled.setTextColor(view.colors().text);enabled.setChecked(enabledOnly);
        enabled.setOnCheckedChangeListener((b,on)-> {capture();enabledOnly=on;render();});body.addView(enabled);
        button(R.string.management_apply,()-> {capture();render();},false);
        button(R.string.management_read_sites,this::load,false);
        button(R.string.management_scrape_all,()->confirm(R.string.management_scrape_all,()->submit("scrape",endpoint -> {endpoint.scrape(null);return null;})),true);
        int count=0;
        for(int i=0;i<sites.length();i++) {
            JSONObject site=sites.optJSONObject(i);if(site==null)continue;
            String name=value(site,"name","id"),id=site.optString("id");boolean on=site.optBoolean("is_enabled");
            if((enabledOnly&&!on)||!name.toLowerCase(Locale.ROOT).contains(siteFilter.toLowerCase(Locale.ROOT)))continue;
            count++;Switch toggle=new Switch(activity);toggle.setText(name);toggle.setTextColor(view.colors().text);toggle.setChecked(on);toggle.setMinHeight(dp(48));toggle.setEnabled(!loading&&!mutating&&!uncertain&&supports("/api/options/sites/_","put"));
            toggle.setOnCheckedChangeListener((b,desired)-> {
                if(desired==on)return;
                toggle.setOnCheckedChangeListener(null);toggle.setChecked(on);
                confirm(R.string.management_change_scraper,()->request(endpoint -> endpoint.toggleSite(id,desired),r -> {sites=(JSONArray)r;notice=view.tr(R.string.management_verified);},true));
            });body.addView(toggle);
            Button scrape=Ui.button(activity,view.tr(R.string.management_scrape_site,name),()->confirm(R.string.management_scrape_one,()->submit("scrape",endpoint -> {endpoint.scrape(id);return null;})));
            scrape.setEnabled(on&&!loading&&!mutating&&!uncertain&&supports("/api/task/scrape","get"));body.addView(scrape,margin());
        }
        if(count==0)text(view.tr(R.string.management_empty));
    }
    private void matching() {
        field(R.string.management_filename,filename);
        CheckBox check=new CheckBox(activity);check.setText(view.tr(R.string.management_matched));check.setTextColor(view.colors().text);check.setChecked(matched);check.setEnabled(!loading&&!mutating);
        check.setOnCheckedChangeListener((b,on)-> {capture();matched=on;load();});body.addView(check);
        button(R.string.management_search,()-> {capture();load();},false);
        if(files.length()==0&&!loading)text(view.tr(R.string.management_empty));
        for(int i=0;i<files.length();i++) {
            JSONObject item=files.optJSONObject(i);if(item==null)continue;
            Button row=Ui.button(activity,value(item,"filename","path"),()-> {selectedFile=item.optLong("id",-1);file=item;go("file");});body.addView(row,margin());
        }
    }
    private void detail() {
        text(view.tr(R.string.management_file_summary,value(file,"filename","path"),duration(file)));
        text(view.tr(R.string.management_match_id,file.optString("scene_id",view.tr(R.string.management_unknown))));
        button(R.string.management_find_scene,()->go("search"),false);
        if(file.optLong("scene_id",0)>0)button(R.string.management_unmatch,()->confirm(R.string.management_unmatch,this::unmatchSelected),true);
        button(R.string.management_read_file,this::load,false);
    }
    private void search() {
        field(R.string.management_scene_query,query);
        button(R.string.management_search,()-> {capture();load();},false);
        if(scenes.length()==0&&!loading)text(view.tr(R.string.management_empty));
        for(int i=0;i<scenes.length();i++) {
            JSONObject item=scenes.optJSONObject(i);if(item==null)continue;
            LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(8),dp(8),dp(8));
            row.addView(cover(item,false),new LinearLayout.LayoutParams(dp(120),dp(90)));
            TextView summary=view.label(sceneSummary(item),14,view.colors().text);
            summary.setPadding(dp(12),0,0,0);row.addView(summary,new LinearLayout.LayoutParams(0,-2,1));
            row.setBackground(Ui.button(activity,"",()->{}).getBackground());
            row.setFocusable(true);row.setOnClickListener(v->{scene=item;go("compare");});body.addView(row,margin());
        }
    }
    private void compare() {
        text(view.tr(R.string.management_compare));
        text(view.tr(R.string.management_file_summary,value(file,"filename","path"),duration(file)));
        body.addView(cover(scene,true),view.spacing(-1,dp(200),0,0,0,10));
        text(sceneSummary(scene));
        text(view.tr(R.string.management_match_warning));
        if(scene.optString("scene_id").isEmpty()) {text(view.tr(R.string.management_unknown));return;}
        button(R.string.management_confirm_match,()->confirm(R.string.management_confirm_match,this::matchSelected),true);
    }
    private View cover(JSONObject item,boolean large) {
        FrameLayout frame=new FrameLayout(activity);
        TextView placeholder=view.label(view.tr(R.string.management_cover_loading),12,view.colors().muted);
        placeholder.setGravity(android.view.Gravity.CENTER);frame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(view.tr(R.string.management_cover_description,value(item,"title","scene_id")));
        frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        Entry metadata=new Entry();XbvrProtocol.metadata(metadata,item,profile.base);
        if(metadata.posterCandidates.isEmpty()) {placeholder.setText(R.string.management_cover_missing);return frame;}
        if(covers==null) {
            coverClient=HttpTransport.client(profile.base,profile.basicUser,profile.basicPassword);
            covers=new BitmapCoverRepository(coverClient,imageIo,command->main.post(command));
        }
        final BitmapCoverRepository repository=covers;final long epoch=coverEpoch;
        final ServerProfile bound=profile;
        final String key=item.optString("id")+":"+metadata.posterCandidates.toString();
        Runnable request=()-> {
            placeholder.setText(R.string.management_cover_loading);repository.retry(key);
            repository.request(epoch,key,metadata.posterCandidates,(ignored,bitmap,width,height)-> {
                if(closed||epoch!=coverEpoch||repository!=covers||!isCurrent(bound))return;
                image.setImageBitmap(bitmap);placeholder.setVisibility(bitmap==null?View.VISIBLE:View.GONE);
                if(bitmap==null)placeholder.setText(R.string.management_cover_failed);
            });
        };
        Bitmap cached=repository.cached(key);
        if(cached!=null) {image.setImageBitmap(cached);placeholder.setVisibility(View.GONE);}else request.run();
        placeholder.setOnClickListener(v->request.run());
        return frame;
    }
    private void unmatchSelected() {
        final long id=selectedFile;
        request(endpoint->{endpoint.unmatch(id);return endpoint.file(id);},r->{file=(JSONObject)r;notice=view.tr(R.string.management_verified);},true);
    }
    private void matchSelected() {
        final long id=selectedFile;final String external=scene.optString("scene_id");final long internal=scene.optLong("id");
        request(endpoint->{endpoint.match(id,external,internal);return endpoint.file(id);},r->{file=(JSONObject)r;notice=view.tr(R.string.management_verified);go("file");},true);
    }
    private String sceneSummary(JSONObject item) { return view.tr(R.string.management_scene_summary,value(item,"title","scene_id"),value(item,"site","studio"),duration(item)); }
    private String duration(JSONObject item) { return item.has("video_duration")?item.optString("video_duration"):item.has("duration")?item.optString("duration"):view.tr(R.string.management_unknown); }
    private String value(JSONObject item,String first,String second) { String value=item.optString(first,"");return value.isEmpty()?item.optString(second,view.tr(R.string.management_unknown)):value; }
    private void tasks() {
        text(view.tr(R.string.management_tasks_hint));
        button(R.string.management_check_status,this::readTasks,false);
        text(taskStatus());
        JSONObject snapshot=state.optJSONObject("currentState");
        JSONObject migration=snapshot==null?null:snapshot.optJSONObject("migration");
        if(migration!=null && migration.opt("is_running") instanceof Boolean)
            text(view.tr(R.string.management_migration,view.tr(migration.optBoolean("is_running")?R.string.management_running:R.string.management_idle)));
        if(updatedAt>0)text(view.tr(R.string.management_updated,java.text.DateFormat.getTimeInstance().format(new java.util.Date(updatedAt))));
    }
    private void stopWatch() {
        monitorGeneration++; if(monitor!=null)monitor.close(); monitor=null; connected=false;
        taskStates.clear();
    }
    private void watch() {
        if(!foreground||closed||api==null) {stopWatch();return;}
        if(monitor!=null)return;
        final int token=++monitorGeneration;
        monitor=new XbvrTaskMonitor(profile,new XbvrTaskMonitor.Listener() {
            public void onTask(String task,boolean running,String message) {
                main.post(()-> {
                    if(token!=monitorGeneration||closed||!isCurrent(profile))return;
                    if(!task.equals("scrape")&&!task.equals("rescan"))return;
                    int previous=taskStates.getOrDefault(task,0);
                    taskStates.put(task,running?1:previous==1?3:2);submittedTasks.remove(task);updatedAt=System.currentTimeMillis();
                    render();
                });
            }
            public void onConnection(boolean value) {
                main.post(()-> {if(token!=monitorGeneration||closed||!isCurrent(profile))return;connected=value;if(!value)taskStates.clear();updatedAt=System.currentTimeMillis();render();});
            }
        });
        monitor.open();
    }
    private String taskStatus() {
        String scrape=view.tr(statusId(taskStates.getOrDefault("scrape",submittedTasks.contains("scrape")?4:0)));
        String scan=view.tr(statusId(taskStates.getOrDefault("rescan",submittedTasks.contains("rescan")?4:0)));
        return view.tr(R.string.management_task_summary,scrape,scan,view.tr(connected?R.string.management_ws_connected:R.string.management_ws_unavailable));
    }
    private int statusId(int status) {
        return status==1?R.string.management_running:status==2?R.string.management_idle:status==3?R.string.management_ended:status==4?R.string.management_submitted_task:R.string.management_unknown;
    }
    private String errorText(Exception error) {
        if(error instanceof XbvrManagementApi.ManagementException) {
            switch(((XbvrManagementApi.ManagementException)error).kind) {
                case AUTH:return view.tr(R.string.management_error_auth);
                case UNSUPPORTED:return view.tr(R.string.management_error_unsupported);
                case NETWORK:return view.tr(R.string.management_error_network);
                case VERIFICATION:return view.tr(R.string.management_error_verification);
                default:break;
            }
        }
        return view.tr(R.string.management_read_failed);
    }
    private void reconcile() {
        read(endpoint -> {
            JSONObject result=endpoint.state();JSONArray currentSites=endpoint.sites();JSONObject currentStorage=endpoint.storage();
            JSONObject currentFile=selectedFile>0?endpoint.file(selectedFile):new JSONObject();
            return new Object[]{result,currentSites,currentStorage,currentFile};
        },r->{Object[] current=(Object[])r;state=(JSONObject)current[0];sites=(JSONArray)current[1];storage=(JSONObject)current[2];file=(JSONObject)current[3];
            recoveryRead=true;notice=view.tr(R.string.management_recovery_read);});
    }
    private void acknowledge() {
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(R.string.management_acknowledge)
            .setMessage(R.string.management_recovery_warning).setNegativeButton(R.string.main_cancel,null)
            .setPositiveButton(R.string.common_ok,(d,w)->{uncertain=false;recoveryRead=false;notice=view.tr(R.string.management_recovery_read);render();}).create();
        dialog.show();Ui.decorateDialog(activity,dialog);
    }
    private void submit(String task,Work work) { request(work,r->{if(!task.isEmpty())submittedTasks.add(task);notice=view.tr(R.string.management_submitted);},true); }
    private void confirm(int title,Runnable action) {
        if(mutating||loading||uncertain)return;
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setMessage(view.tr(R.string.management_confirm_server,identity()))
            .setNegativeButton(R.string.main_cancel,null).setPositiveButton(R.string.common_ok,(d,w)->action.run()).create();dialog.setOnDismissListener(d -> render());dialog.show();Ui.decorateDialog(activity,dialog);
    }
    private void field(int hint,String value) { filterRoute=route;filter=new EditText(activity);filter.setSingleLine();filter.setTextColor(view.colors().text);filter.setHintTextColor(view.colors().muted);filter.setHint(hint);filter.setText(value);body.addView(filter,margin()); }
    private void text(String content) { body.addView(view.label(content,14,view.colors().text),margin()); }
    private LinearLayout.LayoutParams margin() { return view.spacing(-1,-2,0,0,0,10); }
    private boolean supportedAction(int title) {
        if(title==R.string.management_scan_all)return supports("/api/task/rescan","get");
        if(title==R.string.management_scan_directory)return supports("/api/task/rescan/_","get");
        if(title==R.string.management_refresh_scenes)return supports("/api/task/scene-refresh","get");
        if(title==R.string.management_scrape_all)return supports("/api/task/scrape","get");
        if(title==R.string.management_unmatch)return supports("/api/files/unmatch","post");
        if(title==R.string.management_confirm_match)return supports("/api/files/match","post");
        return false;
    }
    private boolean supports(String target,String method) {
        JSONObject paths=capabilities.optJSONObject("paths");if(paths==null)return false;
        for(java.util.Iterator<String> keys=paths.keys();keys.hasNext();) {
            String path=keys.next();String normalized=path.replaceAll("\\{[^/]+\\}","_");
            if(target.equals(normalized)&&paths.optJSONObject(path)!=null&&paths.optJSONObject(path).has(method))return true;
        }
        return false;
    }
    private void button(int title,Runnable action,boolean write) { Button button=Ui.button(activity,view.tr(title),action);button.setEnabled(!loading&&!mutating&&(!write||(!uncertain&&supportedAction(title))));body.addView(button,margin()); }
}


