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
    private TextView heading, taskSummary, taskUpdated, migrationSummary;
    private LinearLayout statusPanel;
    private final HashMap<String,Integer> pages = new HashMap<>();
    private java.util.concurrent.Future<?> readFuture;
    private JSONObject cachedCapabilityPaths;
    private final java.util.HashSet<String> supportedMethods=new java.util.HashSet<>();
    private boolean capabilitiesAttempted;
    private final java.util.ArrayList<Runnable> visibleCovers = new java.util.ArrayList<>();
    private final HashMap<String,Integer> positions = new HashMap<>();
    private ServerProfile profile;
    private XbvrManagementApi api;
    private LinearLayout root, body;
    private ManagementLayout.Mode layoutMode=ManagementLayout.Mode.PHONE;
    private ManagementNavigation navigation;
    private ScrollView scroll;
    private EditText filter;
    private ImageButton refreshSites;
    private LinearLayout scraperResults;
    private TextView scraperCount;
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
    private boolean reloadAfterScan;
    private final Runnable automaticReload=new Runnable(){public void run(){
        if(!foreground||closed||!reloadAfterScan||!isCurrent(profile))return;
        if(view.library().state().busy||view.library().state().metadataBusy){main.postDelayed(this,1000);return;}
        reloadAfterScan=false;view.actions().refresh();
    }};
    private final HashMap<String,Integer> taskStates = new HashMap<>();
    private final java.util.HashSet<String> submittedTasks = new java.util.HashSet<>();
    private JSONArray sites = new JSONArray(), files = new JSONArray(), scenes = new JSONArray();
    private final HashMap<TextView,Long> scanLabels=new HashMap<>();
    private final Runnable scanClock=new Runnable(){public void run(){
        if(!foreground||closed)return;
        for(java.util.Map.Entry<TextView,Long> entry:scanLabels.entrySet())
            entry.getKey().setText(scanTime(entry.getValue(),Boolean.TRUE.equals(entry.getKey().getTag())));
        main.postDelayed(this,60000);
    }};
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
    void heading(TextView value) {heading=value;updateHeading();}
    void refreshButton(ImageButton value){refreshSites=value;updateHeading();}
    void refreshSites(){if(route.equals("scrapers")||route.equals("maintenance")){capture();if(capabilities.optJSONObject("paths")==null)capabilitiesAttempted=false;load();}}
    void navigationMenu(View anchor) {
        PopupMenu menu=new PopupMenu(activity,anchor);
        String[] routes={"maintenance","scrapers","matching","tasks"};
        int[] labels={R.string.management_maintenance,R.string.management_scrapers,R.string.management_matching,R.string.management_tasks};
        for(int i=0;i<routes.length;i++)menu.getMenu().add(0,i,i,view.tr(labels[i]));
        menu.setOnMenuItemClickListener(item->{go(routes[item.getItemId()]);return true;});menu.show();
    }
    private void updateHeading() {
        if(refreshSites!=null){refreshSites.setVisibility(route.equals("scrapers")||route.equals("maintenance")?View.VISIBLE:View.GONE);refreshSites.setEnabled(!loading&&!mutating);refreshSites.setContentDescription(view.tr(route.equals("maintenance")?R.string.management_read_storage:R.string.management_read_sites));}
        if(heading==null)return;
        int title=route.equals("maintenance")?R.string.management_maintenance:route.equals("scrapers")?R.string.management_scrapers
            :route.equals("matching")?R.string.management_matching:route.equals("file")?R.string.management_file_details
            :route.equals("search")?R.string.management_candidates:route.equals("compare")?R.string.management_confirm_match
            :route.equals("tasks")?R.string.management_tasks:R.string.management_title;
        heading.setText(title);
    }
    View create() {
        cancelRead();
        capture(); bindProfile(); generation++; loading=false;
        if(pendingRestore!=null&&profile!=null) {String saved=pendingRestore;pendingRestore=null;restoreState(saved);}
        android.content.res.Configuration config=activity.getResources().getConfiguration();
        layoutMode=ManagementLayout.mode(config.smallestScreenWidthDp,config.screenWidthDp,config.screenHeightDp);
        if(tablet()&&route.equals("home"))route="maintenance";
        root = Ui.column(activity);
        LinearLayout content=root;
        navigation=null;
        if(tablet()) {
            navigation=new ManagementNavigation(view,layoutMode,next->{if(!next.equals(route))go(next);},leave);
            root.addView(navigation.root,new LinearLayout.LayoutParams(-1,0,1));content=navigation.content;
        }
        TextView identity = view.label(view.tr(R.string.management_server, identity()), 13, view.colors().muted);
        boolean shortWindow=!tablet()&&config.screenWidthDp>config.screenHeightDp;
        identity.setPadding(dp(tablet()?24:20), dp(shortWindow?4:12), dp(tablet()?24:20), dp(shortWindow?4:12));
        content.addView(identity);
        scroll = new ScrollView(activity);restoringScroll=true;filter=null;
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->loadVisibleCovers());
        body = Ui.column(activity); body.setPadding(dp(tablet()?24:20), dp(4), dp(tablet()?24:20), dp(24));
        final LinearLayout measuredBody=body;
        body.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(r-l>0&&r-l!=or-ol&&body==measuredBody&&!closed&&route.equals("maintenance")) {
                capture();render();
            }
        });
        scroll.addView(body); content.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        render(); load(); watch(); return root;
    }
    boolean back() {
        capture();
        String target=ManagementLayout.backTarget(route,tablet());
        if(target==null)return false;
        go(target);
        return true;
    }
    void resume() { foreground = true;main.removeCallbacks(automaticReload);automaticReload.run(); main.removeCallbacks(poll);main.removeCallbacks(scanClock);scanClock.run(); watch(); if (route.equals("tasks")) poll.run();else if(route.equals("maintenance")&&!loading&&!mutating)load(); }
    void pause() { foreground = false;main.removeCallbacks(automaticReload); main.removeCallbacks(poll);main.removeCallbacks(scanClock); stopWatch(); }
    void close() { closed = true; generation++; pause(); io.shutdownNow(); clearCovers(); imageIo.shutdownNow(); if(api!=null)api.close(); }
    private void clearCovers() {
        coverEpoch++;
        if(covers!=null)covers.clear();covers=null;
        if(coverClient!=null) HttpTransport.closeAsync(null,coverClient);
        coverClient=null;
    }
    String savedState() {
        capture(); JSONObject saved = new JSONObject();
        try {
            saved.put("profile", profile == null ? "" : profile.id).put("route",route)
                .put("siteFilter",siteFilter).put("filename",filename).put("query",query)
                .put("enabledOnly",enabledOnly).put("matched",matched).put("file",selectedFile)
                .put("scene",scene.optString("scene_id")).put("uncertain", uncertain || mutating)
                .put("submittedScan",submittedTasks.contains("rescan")).put("submittedScrape",submittedTasks.contains("scrape")).put("reloadAfterScan",reloadAfterScan);
            JSONObject offsets = new JSONObject(); for (String key : positions.keySet()) offsets.put(key,positions.get(key));
            saved.put("scroll",offsets);
            JSONObject pageState=new JSONObject();for(String key:pages.keySet())pageState.put(key,pages.get(key));saved.put("pages",pageState);
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
            reloadAfterScan=saved.optBoolean("reloadAfterScan");
            if(saved.optBoolean("submittedScan"))submittedTasks.add("rescan");if(saved.optBoolean("submittedScrape"))submittedTasks.add("scrape");
            // Re-read comparison metadata; retain only the selected external key in saved state.
            restoredScene=saved.optString("scene");
            JSONObject offsets=saved.optJSONObject("scroll");
            if (offsets!=null) for (java.util.Iterator<String> i=offsets.keys();i.hasNext();) { String key=i.next();positions.put(key,offsets.optInt(key)); }
            JSONObject pageState=saved.optJSONObject("pages");
            if(pageState!=null)for(java.util.Iterator<String> i=pageState.keys();i.hasNext();){String key=i.next();pages.put(key,Math.max(0,pageState.optInt(key)));}
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
        route="home";loading=false;mutating=false;uncertain=false;recoveryRead=false;reloadAfterScan=false;main.removeCallbacks(automaticReload);
        positions.clear();pages.clear();capabilitiesAttempted=false;submittedTasks.clear();sites=new JSONArray();files=new JSONArray();scenes=new JSONArray();
        file=new JSONObject();scene=new JSONObject();storage=new JSONObject();state=new JSONObject();selectedFile=-1;
        filename="";siteFilter="";query="";notice="";errorNotice=api==null?view.tr(R.string.management_read_failed):"";capabilities=new JSONObject();
    }
    private boolean sameProfile(ServerProfile a,ServerProfile b) {
        return a!=null&&b!=null&&java.util.Objects.equals(a.id,b.id)&&java.util.Objects.equals(a.base,b.base)
            &&java.util.Objects.equals(a.basicUser,b.basicUser)&&java.util.Objects.equals(a.basicPassword,b.basicPassword)
            &&java.util.Objects.equals(a.user,b.user)&&java.util.Objects.equals(a.password,b.password);
    }
    private boolean isCurrent(ServerProfile captured) {
        try {return captured!=null&&captured.id.equals(view.actions().activeProfileId())&&sameProfile(captured,view.library().state().profile);}
        catch(Exception ignored) {return false;}
    }
    private String identity() {
        if (profile == null) return view.tr(R.string.management_no_server);
        try { java.net.URI u=new java.net.URI(profile.base);return new java.net.URI(u.getScheme(),null,u.getHost(),u.getPort(),u.getPath(),null,null).toString(); }
        catch(Exception e) { return view.tr(R.string.management_server_configured); }
    }
    private int dp(int n) { return Ui.dp(activity,n); }
    private boolean tablet(){return layoutMode!=ManagementLayout.Mode.PHONE;}
    private void capture() {
        if (scroll!=null&&!restoringScroll) positions.put(renderedRoute,scroll.getScrollY());
        if (filter!=null) { String text=filter.getText().toString(); if(filterRoute.equals("scrapers"))siteFilter=text;else if(filterRoute.equals("matching"))filename=text;else if(filterRoute.equals("search"))query=text; }
    }
    private void go(String next) {
        if(coverClient!=null)coverClient.dispatcher().cancelAll();
        capture();cancelRead(); generation++; loading=false; route=next; render(); load();
        main.removeCallbacks(poll); watch(); if(foreground&&route.equals("tasks")) main.postDelayed(poll,3000);
    }
    private void cancelRead() {if(readFuture!=null)readFuture.cancel(true);readFuture=null;if(api!=null&&!mutating)api.cancelReads();if(capabilities.optJSONObject("paths")==null)capabilitiesAttempted=false;}
    private boolean valid(int token) { return !closed && token==generation && profile!=null && isCurrent(profile); }
    private interface Work { Object run(XbvrManagementApi endpoint) throws Exception; }
    private interface Done { void apply(Object result) throws Exception; }
    private void read(Work work, Done done) { request(work,done,false); }
    private void request(Work work, Done done, boolean write) {
        if (closed||api==null||loading||mutating|| (write&&uncertain)) return;
        capture(); final int token=generation; final ServerProfile boundProfile=profile;final XbvrManagementApi boundApi=api;final boolean discover=!route.equals("home")&&!capabilitiesAttempted;
        if(!write)capabilitiesAttempted=true;
        loading=!write; mutating=write;if(write)recoveryRead=false; updateChrome();disableControls(body);
        java.util.concurrent.Future<?> pending=io.submit(() -> {
            Object result=null; Exception failure=null;JSONObject discovered=null;
            try { if (Thread.currentThread().isInterrupted()||!isCurrent(boundProfile)) return; if(!write&&discover)try {discovered=boundApi.capabilities();}catch(Exception ignored) { } if(Thread.currentThread().isInterrupted())return;result=work.run(boundApi); }
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
                if(route.equals("tasks")) {updateChrome();updateTasks();enableStatusRead();}else render();
            });
        });
        if(!write)readFuture=pending;
    }
    private void load() {
        if(api==null)return;
        switch(route) {
            case "home": read(endpoint -> endpoint.capabilities(),r->capabilities=(JSONObject)r);break;
            case "maintenance": read(endpoint -> endpoint.storage(), r -> storage=(JSONObject)r); break;
            case "scrapers": read(endpoint -> endpoint.sites(), r -> sites=(JSONArray)r); break;
            case "matching": read(endpoint -> endpoint.files(matched,filename), r -> files=(JSONArray)r); break;
            case "file": read(endpoint -> endpoint.file(selectedFile), r -> file=(JSONObject)r); break;
            case "search":
                final String searched=query; final long fileId=selectedFile;
                read(endpoint -> new Object[]{endpoint.file(fileId),searched.isBlank()?new JSONObject().put("scenes",new JSONArray()):endpoint.searchScenes(searched),
                        files.length()==0?endpoint.files(matched,filename):files}, r -> {
                    Object[] pair=(Object[])r;file=(JSONObject)pair[0];files=(JSONArray)pair[2];
                    scenes=((JSONObject)pair[1]).optJSONArray("scenes");if(scenes==null)scenes=new JSONArray(); });break;
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
        visibleCovers.clear();taskSummary=null;taskUpdated=null;migrationSummary=null;updateHeading();
        if(navigation!=null)navigation.select(route);
        body.removeAllViews();scanLabels.clear(); filter=null;renderedRoute=route;
        statusPanel=Ui.column(activity);body.addView(statusPanel);updateChrome();
        if(profile==null) { text(view.tr(R.string.management_no_server));return; }
        if (!isCurrent(profile)) { text(view.tr(R.string.management_server_changed));button(R.string.management_back,leave,false);return; }
        if(uncertain) {button(R.string.management_reconcile,this::reconcile,false);
            if(recoveryRead)button(R.string.management_acknowledge,this::acknowledge,false);}
        if(capabilities.optJSONObject("paths")==null)text(view.tr(R.string.management_capability_unknown));
        else if(!route.equals("home")&&!route.equals("tasks")&&(!route.equals("maintenance")||!supports("/api/task/rescan","get")||!supports("/api/task/rescan/_","get")||!supports("/api/task/scene-refresh","get"))&&(!route.equals("scrapers")||!supports("/api/task/scrape","get")||!supports("/api/options/sites/_","put")))text(view.tr(R.string.management_capability_hint));
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
        final int y=positions.getOrDefault(route,0);final ScrollView mountedScroll=scroll;restoringScroll=true;scroll.post(() -> {if(scroll!=mountedScroll)return;scroll.scrollTo(0,y);restoringScroll=false;loadVisibleCovers();});
    }
    private void updateChrome() {
        if(refreshSites!=null)refreshSites.setEnabled(!loading&&!mutating);
        if(statusPanel==null)return;statusPanel.removeAllViews();
        if(!notice.isEmpty()&&!uncertain)statusPanel.addView(view.label(notice,14,view.colors().text),margin());
        if(!errorNotice.isEmpty())statusPanel.addView(view.label(errorNotice,14,view.colors().text),margin());
        if(uncertain)statusPanel.addView(view.label(view.tr(R.string.management_uncertain),14,view.colors().text),margin());
        if(loading||mutating)statusPanel.addView(view.label(view.tr(R.string.management_loading),14,view.colors().text),margin());
    }
    private void disableControls(android.view.ViewGroup group) {
        if(group==null)return;
        for(int i=0;i<group.getChildCount();i++) {
            View child=group.getChildAt(i);
            if((child instanceof Button||child instanceof ImageButton) && !(Boolean.TRUE.equals(child.getTag())&&!mutating))child.setEnabled(false);
            if(child instanceof android.view.ViewGroup)disableControls((android.view.ViewGroup)child);
        }
    }
    private void loadVisibleCovers() {for(Runnable load:visibleCovers)load.run();}
    private int pageStart(int total,int size) {
        return pageStart(total,size,body);
    }
    private int pageStart(int total,int size,LinearLayout container) {
        int page=Math.min(pages.getOrDefault(route,0),Math.max(0,(total-1)/size));pages.put(route,page);
        if(total>size) {
            LinearLayout row=Ui.row(activity);int current=page;
            Button previous=Ui.button(activity,view.tr(R.string.management_previous_page),()->changePage(current-1));
            previous.setEnabled(page>0&&!loading&&!mutating);row.addView(previous,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(view.label(view.tr(R.string.management_page,page+1,(total+size-1)/size),13,view.colors().muted));
            Button next=Ui.button(activity,view.tr(R.string.management_next_page),()->changePage(current+1));
            next.setEnabled((page+1)*size<total&&!loading&&!mutating);row.addView(next,new LinearLayout.LayoutParams(0,-2,1));container.addView(row,margin());
        }
        return page*size;
    }
    private void changePage(int page) {capture();pages.put(route,page);scroll.scrollTo(0,0);positions.put(route,0);if(route.equals("scrapers"))renderScraperResults();else render();}
    private void home() {
        button(R.string.management_check_capabilities,this::load,false);
        button(R.string.management_maintenance,()->go("maintenance"),false);
        button(R.string.management_scrapers,()->go("scrapers"),false);
        button(R.string.management_matching,()->go("matching"),false);
        button(R.string.management_tasks,()->go("tasks"),false);
    }
    private void maintenance() {
        top.liuwei.xbvr.domain.StorageSummary snapshot=top.liuwei.xbvr.domain.StorageSummary.fromJson(storage);
        android.content.res.Configuration config=activity.getResources().getConfiguration();
        int available=storageContentWidth(config);
        ManagementLayout.StorageMode mode=ManagementLayout.storageMode(config.smallestScreenWidthDp,config.screenWidthDp,
            config.screenHeightDp,available,config.fontScale);
        boolean compact=mode==ManagementLayout.StorageMode.COMPACT;
        boolean wide=tablet()&&available>=620*Math.max(1f,config.fontScale);
        StorageDirectoryViews directories=new StorageDirectoryViews(view,this::directoryScan,this::directoryDetails,
            volume->scanTime(top.liuwei.xbvr.domain.ScanTimestamp.parse(volume.lastScan),mode==ManagementLayout.StorageMode.TABLE));
        Button add=maintenanceButton(R.string.storage_add,R.string.storage_add,this::addDirectory,true,false);
        Button all=maintenanceButton(R.string.management_scan_all,R.string.management_scan_all,()->confirm(R.string.management_scan_all,
            ()->submit("rescan",endpoint->{endpoint.rescan(null);return null;})),true,true);
        Button matching=maintenanceButton(R.string.storage_open_matching,R.string.storage_open_matching,()->go("matching"),false,false);
        actionIcon(all,"play_circle",true);actionIcon(matching,"link",false);
        new StorageOverviewViews(view,directories).addTo(body,snapshot,available,storage.optJSONArray("volumes")!=null,compact,wide,add,all,matching);
        LinearLayout directoryList=Ui.column(activity);
        directoryList.setBackground(Ui.rounded(view.colors().surface,12,activity));body.addView(directoryList);
        directories.addTo(directoryList,snapshot,mode);
        scanLabels.putAll(directories.scanLabels);
        if(snapshot.volumes.isEmpty())directoryList.addView(view.label(view.tr(storage.has("volumes")?R.string.storage_empty:R.string.management_loading),14,view.colors().muted),margin());
        if(compact){
            matching.setText(view.tr(R.string.storage_matching_count,directories.count(snapshot.unmatchedFiles)));
            matching.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
            body.addView(matching,view.spacing(-1,-2,0,8,0,8));
        }
        TextView syncHeading=view.label(view.tr(R.string.management_sync_heading),20,view.colors().text);
        syncHeading.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
        body.addView(syncHeading,view.spacing(-1,-2,0,24,0,16));
        boolean stacked=layoutMode!=ManagementLayout.Mode.SIDEBAR;
        LinearLayout updates=stacked?Ui.column(activity):Ui.row(activity);
        updates.addView(maintenanceTile(R.string.management_refresh_scenes,R.string.management_scene_hint,()->confirm(R.string.management_refresh_scenes,
            ()->submit("",endpoint->{endpoint.refreshScenes();return null;})),true),stacked?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,1));
        updates.addView(maintenanceTile(R.string.management_reload_app,R.string.management_reload_short_hint,()->view.actions().refresh(),false),
            stacked?view.spacing(-1,-2,0,10,0,0):view.spacing(0,-2,10,0,0,0));
        if(!stacked)((LinearLayout.LayoutParams)updates.getChildAt(1).getLayoutParams()).weight=1;
        body.addView(updates,margin());
        body.addView(view.label(view.tr(R.string.management_scan_hint),13,view.colors().muted),view.spacing(-1,-2,0,12,0,16));
        Button tasks=maintenanceButton(R.string.management_tasks,R.string.management_tasks,()->go("tasks"),false,false);
        body.addView(tasks,margin());
    }
    private int storageContentWidth(android.content.res.Configuration config) {
        if(body!=null&&body.getWidth()>0)
            return Math.max(0,Math.round((body.getWidth()-body.getPaddingLeft()-body.getPaddingRight())/activity.getResources().getDisplayMetrics().density));
        return config.screenWidthDp-(layoutMode==ManagementLayout.Mode.SIDEBAR?217:0)-(tablet()?48:40);
    }
    private Button directoryScan(top.liuwei.xbvr.domain.StorageSummary.Volume volume) {
        Button scan=maintenanceButton(R.string.management_scan_short,R.string.management_scan_directory,()->{
            final ServerProfile bound=profile;
            if(mutating||loading||uncertain||!isCurrent(bound))return;
            AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(view.tr(R.string.management_scan_directory))
                .setMessage(volume.path+"\n\n"+identity()).setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(android.R.string.ok,(d,w)->{
                    if(!sameProfile(bound,profile)||!isCurrent(bound))return;
                    submit("rescan",endpoint->{endpoint.rescan(volume.id);return null;});
                }).create();dialog.show();Ui.decorateDialog(activity,dialog);
        },true,true);
        scan.setEnabled(scan.isEnabled()&&volume.id>0);
        android.graphics.drawable.GradientDrawable outline=new android.graphics.drawable.GradientDrawable();
        outline.setColor(android.graphics.Color.TRANSPARENT);outline.setCornerRadius(dp(8));
        outline.setStroke(dp(1),scan.isEnabled()?view.colors().accent:view.colors().border);
        android.graphics.drawable.Drawable ripple=new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x2465D6AE),outline,null);
        scan.setBackground(new android.graphics.drawable.InsetDrawable(ripple,dp(4),dp(6),dp(4),dp(6)));
        scan.setPadding(dp(12),0,dp(12),0);
        actionIcon(scan,"play",false);
        scan.setContentDescription(view.tr(R.string.management_scan_path,volume.path));return scan;
    }
    private void directoryDetails(top.liuwei.xbvr.domain.StorageSummary.Volume volume) {
        if(loading||mutating)return;
        StorageDirectoryViews format=new StorageDirectoryViews(view,this::directoryScan,this::directoryDetails,v->"");
        long timestamp=top.liuwei.xbvr.domain.ScanTimestamp.parse(volume.lastScan);
        String last=timestamp<0?view.tr(R.string.management_scan_unknown):view.tr(R.string.management_last_scan,
            java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(timestamp)));
        String status=view.tr(volume.available==null?R.string.management_unknown:Boolean.TRUE.equals(volume.available)?R.string.storage_available:R.string.storage_unavailable);
        final ServerProfile bound=profile;
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(view.tr(R.string.storage_details))
            .setMessage(view.tr(R.string.storage_details_body,volume.path,status,format.count(volume.totalFiles),
                format.count(volume.unmatchedFiles),format.bytes(volume.totalBytes),last))
            .setPositiveButton(android.R.string.ok,null).setNeutralButton(R.string.storage_open_matching,(d,w)->{
                if(sameProfile(bound,profile)&&isCurrent(bound))go("matching");
            }).setNegativeButton(R.string.storage_remove,(d,w)->{
                if(sameProfile(bound,profile)&&isCurrent(bound))removeDirectory(volume);
            }).create();
        dialog.show();dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(volume.id>0&&!uncertain&&supports("/api/options/storage/_","delete"));
        Ui.decorateDialog(activity,dialog);
    }
    private void addDirectory() {
        if(loading||mutating||uncertain||!supportedAction(R.string.storage_add))return;
        final ServerProfile bound=profile;
        LinearLayout fields=Ui.column(activity);fields.setPadding(dp(20),dp(8),dp(20),0);
        fields.addView(view.label(view.tr(R.string.storage_path_explanation,identity()),14,view.colors().muted));
        EditText path=new EditText(activity);path.setTextColor(view.colors().text);path.setHintTextColor(view.colors().muted);
        path.setHint(view.tr(R.string.storage_path_hint));path.setSingleLine(false);path.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        fields.addView(path,new LinearLayout.LayoutParams(-1,-2));
        TextView pathError=view.label(view.tr(R.string.storage_path_required),13,Ui.dark(activity)?0xFFFF8A80:0xFFB3261E);
        pathError.setVisibility(View.GONE);pathError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        fields.addView(pathError,view.spacing(-1,-2,0,4,0,8));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(view.tr(R.string.storage_add)).setView(fields)
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.storage_add,null).create();
        dialog.show();Ui.decorateDialog(activity,dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String supplied=path.getText().toString().trim();
            if(!StoragePathInput.absolute(supplied)){pathError.setVisibility(View.VISIBLE);return;}
            if(!sameProfile(bound,profile)||!isCurrent(bound)){dialog.dismiss();return;}
            dialog.dismiss();request(endpoint->endpoint.addStorage(supplied),result->{storage=(JSONObject)result;notice=view.tr(R.string.storage_added);},true);
        });
    }
    private void removeDirectory(top.liuwei.xbvr.domain.StorageSummary.Volume volume) {
        if(loading||mutating||uncertain||!supports("/api/options/storage/_","delete"))return;
        final ServerProfile bound=profile;
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(view.tr(R.string.storage_remove))
            .setMessage(view.tr(R.string.storage_remove_warning,volume.path,identity()))
            .setNegativeButton(android.R.string.cancel,null).setPositiveButton(R.string.storage_remove,(d,w)->{
                if(!sameProfile(bound,profile)||!isCurrent(bound))return;
                request(endpoint->{
                    JSONObject result=endpoint.removeStorage(volume.id,volume.path);
                    // This verified mutation invalidates library data even if this management
                    // route was left while the request was running. Do not rely on a WS event.
                    main.post(()->refreshAfterDirectoryRemoval(bound));return result;
                },result->{
                    storage=(JSONObject)result;notice=view.tr(R.string.storage_removed);
                },true);
            }).create();dialog.show();Ui.decorateDialog(activity,dialog);
    }
    private void refreshAfterDirectoryRemoval(ServerProfile bound) {
        if(activity.isFinishing()||activity.isDestroyed()||!isCurrent(bound))return;
        if(view.library().state().busy||view.library().state().metadataBusy){
            main.postDelayed(()->refreshAfterDirectoryRemoval(bound),1000);return;
        }
        view.actions().refresh();
    }
    private String scanTime(long timestamp){return scanTime(timestamp,false);}
    private String scanTime(long timestamp,boolean ageOnly){
        if(timestamp<0)return view.tr(R.string.management_scan_unknown);
        long minutes=top.liuwei.xbvr.domain.ScanTimestamp.ageMinutes(timestamp,System.currentTimeMillis());
        String age=minutes<1?view.tr(R.string.management_time_now):minutes<60?view.tr(R.string.management_time_minutes,minutes):minutes<1440?view.tr(R.string.management_time_hours,minutes/60):view.tr(R.string.management_time_days,minutes/1440);
        return ageOnly?age:view.tr(R.string.management_last_scan,age);
    }
    private Button maintenanceButton(int label,int capability,Runnable action,boolean write,boolean outline){
        Button button=Ui.button(activity,view.tr(label),action);button.setMinHeight(dp(48));button.setPadding(dp(12),0,dp(12),0);
        button.setTextSize(14);button.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));
        button.setIncludeFontPadding(false);
        button.setBackground(Ui.ripple(activity,view.colors().soft,12));
        boolean enabled=!loading&&!mutating&&(!write||(!uncertain&&supportedAction(capability)));button.setEnabled(enabled);
        if(outline){android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(android.graphics.Color.TRANSPARENT);background.setCornerRadius(dp(12));background.setStroke(dp(1),enabled?view.colors().accent:view.colors().border);button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x2465D6AE),background,null));}
        button.setTextColor(enabled?view.colors().accent:view.colors().muted);return button;
    }
    private void actionIcon(Button button,String name,boolean filled) {
        int color=button.isEnabled()?(filled?view.colors().onAccent:view.colors().accent):view.colors().muted;
        android.graphics.drawable.Drawable icon=name.equals("link")?activity.getDrawable(R.drawable.ic_storage_link).mutate():
            name.equals("play_circle")?activity.getDrawable(R.drawable.ic_storage_play_circle).mutate():Ui.iconDrawable(name,color);
        icon.setTint(color);icon.setBounds(0,0,dp(24),dp(24));
        button.setCompoundDrawablesRelative(icon,null,null,null);button.setCompoundDrawablePadding(dp(8));
        button.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            if(r-l>0&&r-l!=or-ol){
                int group=Math.round(button.getPaint().measureText(button.getText().toString()))+dp(32);
                int padding=Math.max(dp(12),(r-l-group)/2);
                button.setPadding(padding,button.getPaddingTop(),padding,button.getPaddingBottom());
            }
        });
    }
    private View maintenanceTile(int title,int hint,Runnable action,boolean write){
        Button actionButton=maintenanceButton(title,title,action,write,false);String label=view.tr(write?R.string.management_refresh_short:R.string.management_reload_short),description=view.tr(hint);
        android.text.SpannableString content=new android.text.SpannableString(label+"\n"+description);content.setSpan(new android.text.style.ForegroundColorSpan(view.colors().muted),label.length()+1,content.length(),0);content.setSpan(new android.text.style.AbsoluteSizeSpan(12,true),label.length()+1,content.length(),0);
        actionButton.setText(content);actionButton.setAllCaps(false);actionButton.setTextSize(15);actionButton.setMinHeight(dp(80));actionButton.setPadding(dp(12),dp(12),dp(12),dp(12));actionButton.setGravity(android.view.Gravity.CENTER);actionButton.setContentDescription(label+", "+description);
        android.graphics.drawable.Drawable icon=Ui.iconDrawable(write?"refresh":"library",view.colors().accent);icon.setBounds(0,0,dp(20),dp(20));actionButton.setCompoundDrawables(icon,null,null,null);actionButton.setCompoundDrawablePadding(dp(6));
        actionButton.setBackground(Ui.ripple(activity,view.colors().surface,12));actionButton.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.START);actionButton.setPadding(dp(20),dp(16),dp(20),dp(16));actionButton.setCompoundDrawablePadding(dp(16));
        return actionButton;
    }
    private void scrapers() {
        field(R.string.management_search_sites,siteFilter);
        filter.setBackground(Ui.rounded(view.colors().surface,12,activity));filter.setPadding(dp(16),dp(10),dp(16),dp(10));filter.setMinHeight(dp(48));
        android.graphics.drawable.Drawable searchIcon=Ui.iconDrawable("search",view.colors().muted);searchIcon.setBounds(0,0,dp(20),dp(20));filter.setCompoundDrawables(searchIcon,null,null,null);filter.setCompoundDrawablePadding(dp(10));
        LinearLayout options=Ui.row(activity);
        RadioGroup segments=new RadioGroup(activity);segments.setOrientation(LinearLayout.HORIZONTAL);segments.setBackground(Ui.rounded(view.colors().surface,12,activity));
        RadioButton all=siteSegment(R.string.management_sites_all,!enabledOnly),enabled=siteSegment(R.string.management_sites_enabled,enabledOnly);
        segments.addView(all,new RadioGroup.LayoutParams(0,-2,1));segments.addView(enabled,new RadioGroup.LayoutParams(0,-2,1));
        segments.check(enabledOnly?enabled.getId():all.getId());
        segments.setOnCheckedChangeListener((group,id)->{capture();enabledOnly=id==enabled.getId();all.setBackground(Ui.rounded(!enabledOnly?view.colors().soft:view.colors().surface,12,activity));enabled.setBackground(Ui.rounded(enabledOnly?view.colors().soft:view.colors().surface,12,activity));all.setTextColor(!enabledOnly?view.colors().accent:view.colors().text);enabled.setTextColor(enabledOnly?view.colors().accent:view.colors().text);pages.put("scrapers",0);renderScraperResults();});
        options.addView(segments,new LinearLayout.LayoutParams(0,-2,1));
        scraperCount=view.label("",12,view.colors().muted);scraperCount.setPadding(dp(12),0,0,0);options.addView(scraperCount);body.addView(options,margin());
        button(R.string.management_scrape_all,()->confirm(R.string.management_scrape_all,()->submit("scrape",endpoint -> {endpoint.scrape(null);return null;})),true);
        scraperResults=Ui.column(activity);body.addView(scraperResults);renderScraperResults();
        filter.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){siteFilter=s.toString();pages.put("scrapers",0);renderScraperResults();}
            public void afterTextChanged(android.text.Editable s){}
        });
    }
    private RadioButton siteSegment(int title,boolean selected){
        RadioButton button=new RadioButton(activity);button.setId(View.generateViewId());button.setButtonDrawable(null);button.setText(title);button.setTextSize(14);button.setTextColor(selected?view.colors().accent:view.colors().text);button.setGravity(android.view.Gravity.CENTER);button.setPadding(dp(8),0,dp(8),0);button.setMinHeight(dp(48));button.setBackground(Ui.rounded(selected?view.colors().soft:view.colors().surface,12,activity));return button;
    }
    private void renderScraperResults() {
        if(scraperResults==null||!route.equals("scrapers"))return;
        scraperResults.removeAllViews();int enabledCount=0;
        java.util.ArrayList<JSONObject> filtered=new java.util.ArrayList<>();
        for(int i=0;i<sites.length();i++) {
            JSONObject site=sites.optJSONObject(i);if(site==null)continue;
            String name=value(site,"name","id"),id=site.optString("id");boolean on=site.optBoolean("is_enabled");
            if(on)enabledCount++;
            if((enabledOnly&&!on)||!name.toLowerCase(Locale.ROOT).contains(siteFilter.toLowerCase(Locale.ROOT)))continue;
            filtered.add(site);
        }
        scraperCount.setText(view.tr(R.string.management_sites_count,enabledCount));
        int start=pageStart(filtered.size(),20,scraperResults);
        for(int i=start;i<Math.min(start+20,filtered.size());i++) {
            JSONObject site=filtered.get(i);String name=value(site,"name","id"),id=site.optString("id");boolean on=site.optBoolean("is_enabled");
            LinearLayout row=Ui.row(activity);row.setMinimumHeight(dp(72));row.setPadding(0,dp(10),0,dp(10));
            LinearLayout labels=Ui.column(activity);TextView title=view.label(name,16,view.colors().text);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(title);
            labels.addView(view.label(view.tr(on?R.string.management_site_enabled:R.string.management_site_disabled),12,on?view.colors().accent:view.colors().muted));row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            Switch toggle=new Switch(activity);toggle.setContentDescription(view.tr(R.string.management_site_toggle,name));toggle.setChecked(on);toggle.setMinHeight(dp(48));toggle.setEnabled(!loading&&!mutating&&!uncertain&&supports("/api/options/sites/_","put"));
            toggle.setOnCheckedChangeListener((b,desired)-> {
                if(desired==on)return;
                toggle.setOnCheckedChangeListener(null);toggle.setChecked(on);
                confirm(R.string.management_change_scraper,()->request(endpoint -> endpoint.toggleSite(id,desired),r -> {sites=(JSONArray)r;notice=view.tr(R.string.management_verified);},true));
            });
            Button scrape=Ui.button(activity,view.tr(R.string.management_scrape_short),()->confirmSiteScrape(id,name));scrape.setContentDescription(view.tr(R.string.management_scrape_site,name));scrape.setPadding(dp(8),0,dp(8),0);
            boolean canScrape=on&&!loading&&!mutating&&!uncertain&&supports("/api/task/scrape","get");scrape.setEnabled(canScrape);scrape.setTextColor(canScrape?view.colors().accent:view.colors().muted);
            android.graphics.drawable.GradientDrawable outline=new android.graphics.drawable.GradientDrawable();outline.setColor(android.graphics.Color.TRANSPARENT);outline.setCornerRadius(dp(12));outline.setStroke(dp(1),canScrape?view.colors().accent:view.colors().border);
            scrape.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x2465D6AE),outline,null));
            int buttonWidth=dp(Math.round(68*Math.min(1.4f,activity.getResources().getConfiguration().fontScale)));
            LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(buttonWidth,-2);buttonParams.setMargins(dp(12),0,dp(12),0);row.addView(scrape,buttonParams);row.addView(toggle,new LinearLayout.LayoutParams(dp(52),dp(48)));
            scraperResults.addView(row);scraperResults.addView(view.divider(),new LinearLayout.LayoutParams(-1,dp(1)));
        }
        if(filtered.isEmpty())scraperResults.addView(view.label(view.tr(R.string.management_empty),14,view.colors().muted),margin());
    }
    private void confirmSiteScrape(String id,String name){
        if(mutating||loading||uncertain)return;
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(view.tr(R.string.management_scrape_site,name))
            .setMessage(view.tr(R.string.management_confirm_server,identity())).setNegativeButton(R.string.main_cancel,null)
            .setPositiveButton(R.string.common_ok,(d,w)->submit("scrape",endpoint->{endpoint.scrape(id);return null;})).create();
        dialog.setOnDismissListener(d->render());dialog.show();Ui.decorateDialog(activity,dialog);
    }
    private void matching() {
        field(R.string.management_filename,filename);
        CheckBox check=new CheckBox(activity);check.setText(view.tr(R.string.management_matched));check.setTextColor(view.colors().text);check.setChecked(matched);check.setEnabled(!loading&&!mutating);
        check.setOnCheckedChangeListener((b,on)-> {capture();matched=on;load();});body.addView(check);
        button(R.string.management_search,()-> {capture();load();},false);
        if(files.length()==0&&!loading)text(view.tr(R.string.management_empty));
        int start=pageStart(files.length(),20);
        for(int i=start;i<Math.min(start+20,files.length());i++) {
            JSONObject item=files.optJSONObject(i);if(item==null)continue;
            Button row=Ui.button(activity,value(item,"filename","path"),()-> {if(matched){selectedFile=item.optLong("id",-1);file=item;go("file");}else beginMatching(item);});body.addView(row,margin());
        }
    }
    private void detail() {
        text(view.tr(R.string.management_file_summary,value(file,"filename","path"),duration(file,true)));
        previewButton();
        text(view.tr(R.string.management_match_id,file.optString("scene_id",view.tr(R.string.management_unknown))));
        button(R.string.management_find_scene,()->beginMatching(file),false);nextFileButton();
        if(file.optLong("scene_id",0)>0)button(R.string.management_unmatch,()->confirm(R.string.management_unmatch,this::unmatchSelected),true);
        button(R.string.management_read_file,this::load,false);
    }
    private void search() {
        text(view.tr(R.string.management_file_summary,value(file,"filename","path"),duration(file,true)));
        previewButton();
        nextFileButton();
        field(R.string.management_scene_query,query);
        button(R.string.management_search,()-> {capture();load();},false);
        if(query.isBlank())text(view.tr(R.string.management_query_empty));
        else if(scenes.length()==0&&!loading)text(view.tr(R.string.management_no_candidates));
        int start=pageStart(scenes.length(),12);
        for(int i=start;i<Math.min(start+12,scenes.length());i++) {
            JSONObject item=scenes.optJSONObject(i);if(item==null)continue;
            LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(8),dp(8),dp(8));
            row.addView(cover(item,false),new LinearLayout.LayoutParams(dp(120),dp(90)));
            TextView summary=view.label(sceneSummary(item),14,view.colors().text);
            summary.setPadding(dp(12),0,0,0);row.addView(summary,new LinearLayout.LayoutParams(0,-2,1));
            row.setBackground(Ui.ripple(activity,view.colors().soft,16));
            row.setFocusable(true);row.setOnClickListener(v->{scene=item;go("compare");});body.addView(row,margin());
        }
    }
    private void compare() {
        previewButton();
        nextFileButton();
        text(view.tr(R.string.management_compare));
        text(view.tr(R.string.management_file_summary,value(file,"filename","path"),duration(file,true)));
        body.addView(cover(scene,true),view.spacing(-1,dp(200),0,0,0,10));
        text(sceneSummary(scene));
        text(view.tr(R.string.management_match_warning));
        if(scene.optString("scene_id").isEmpty()) {text(view.tr(R.string.management_unknown));return;}
        button(R.string.management_confirm_match,()->confirm(R.string.management_confirm_match,this::matchSelected),true);
    }
    private void previewButton() {
        if(selectedFile<=0)return;
        button(R.string.management_preview_file,()-> {
            if(profile==null||!isCurrent(profile))return;
            capture();
            android.content.Intent intent=new android.content.Intent(activity,top.liuwei.xbvr.PlayerActivity.class);
            intent.putExtra("profile",profile.id);
            intent.putExtra("url",profile.base+"/deovr/file/"+selectedFile);
            intent.putExtra("title",value(file,"filename","path"));
            intent.putExtra("filePreview",true);
            activity.startActivity(intent);
        },false);
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
        if(cached!=null) {image.setImageBitmap(cached);placeholder.setVisibility(View.GONE);}else {
            boolean[] started={false};
            visibleCovers.add(()->{if(!started[0]&&frame.isAttachedToWindow()&&frame.getGlobalVisibleRect(new android.graphics.Rect())) {started[0]=true;request.run();}});
        }
        placeholder.setOnClickListener(v->request.run());
        return frame;
    }
    private void beginMatching(JSONObject next) {
        capture();filter=null;file=next;selectedFile=next.optLong("id",-1);query=MatchingWorkflow.query(next.optString("filename"));
        scene=new JSONObject();scenes=new JSONArray();restoredScene="";pages.put("search",0);positions.put("search",0);positions.put("compare",0);
        if(scroll!=null)scroll.scrollTo(0,0);
        go("search");
    }
    private void nextFileButton() {
        int index=MatchingWorkflow.nextIndex(files,selectedFile);
        Button next=Ui.button(activity,view.tr(R.string.management_skip_next),()-> {if(index>=0)beginMatching(files.optJSONObject(index));});
        next.setTag(Boolean.TRUE);next.setEnabled(index>=0&&!mutating);body.addView(next,margin());
        if(index<0&&!loading)text(view.tr(R.string.management_queue_end));
    }
    private void unmatchSelected() {
        final long id=selectedFile;
        request(endpoint->{endpoint.unmatch(id);return endpoint.file(id);},r->{file=(JSONObject)r;notice=view.tr(R.string.management_verified);},true);
    }
    private void matchSelected() {
        final long id=selectedFile;final String external=scene.optString("scene_id");final long internal=scene.optLong("id");
        request(endpoint->{endpoint.match(id,external,internal);return endpoint.file(id);},r->{file=(JSONObject)r;notice=view.tr(R.string.management_verified);go("file");},true);
    }
    private String sceneSummary(JSONObject item) { return view.tr(R.string.management_scene_summary,value(item,"title","scene_id"),value(item,"site","studio"),duration(item,false)); }
    private String duration(JSONObject item,boolean seconds) { double value=item.has("video_duration")?item.optDouble("video_duration",Double.NaN):item.optDouble("duration",Double.NaN);String minutes=top.liuwei.xbvr.domain.DurationMinutes.format(value,seconds);return minutes==null?view.tr(R.string.management_unknown):view.tr(R.string.management_duration_minutes,minutes); }
    private String value(JSONObject item,String first,String second) { String value=item.optString(first,"");return value.isEmpty()?item.optString(second,view.tr(R.string.management_unknown)):value; }
    private void tasks() {
        text(view.tr(R.string.management_tasks_hint));
        button(R.string.management_check_status,this::readTasks,false);
        taskSummary=view.label(taskStatus(),14,view.colors().text);body.addView(taskSummary,margin());
        migrationSummary=view.label("",14,view.colors().text);body.addView(migrationSummary,margin());
        taskUpdated=view.label("",14,view.colors().text);body.addView(taskUpdated,margin());updateTasks();
    }
    private void enableStatusRead() {for(int i=0;i<body.getChildCount();i++){View child=body.getChildAt(i);if(child instanceof Button)child.setEnabled(!loading&&!mutating);}}
    private void updateTasks() {
        if(!route.equals("tasks")||taskSummary==null)return;taskSummary.setText(taskStatus());
        JSONObject snapshot=state.optJSONObject("currentState");
        JSONObject migration=snapshot==null?null:snapshot.optJSONObject("migration");
        if(migration!=null && migration.opt("is_running") instanceof Boolean)
            migrationSummary.setText(view.tr(R.string.management_migration,view.tr(migration.optBoolean("is_running")?R.string.management_running:R.string.management_idle)));
        if(updatedAt>0)taskUpdated.setText(view.tr(R.string.management_updated,java.text.DateFormat.getTimeInstance().format(new java.util.Date(updatedAt))));
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
                    updateTasks();
                    if(task.equals("rescan")&&!running&&previous==1){
                        reloadAfterScan=true;main.removeCallbacks(automaticReload);automaticReload.run();
                        if(route.equals("maintenance")&&!loading&&!mutating)load();
                    }
                });
            }
            public void onConnection(boolean value) {
                main.post(()-> {if(token!=monitorGeneration||closed||!isCurrent(profile))return;connected=value;if(!value)taskStates.clear();updatedAt=System.currentTimeMillis();updateTasks();});
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
        if(title==R.string.storage_add)return supports("/api/options/storage","post");
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
        if(paths!=cachedCapabilityPaths) {
            cachedCapabilityPaths=paths;supportedMethods.clear();
            for(java.util.Iterator<String> keys=paths.keys();keys.hasNext();) {
                String path=keys.next();String normalized=path.replaceAll("\\{[^/]+\\}","_");JSONObject methods=paths.optJSONObject(path);
                if(methods!=null)for(java.util.Iterator<String> verbs=methods.keys();verbs.hasNext();)supportedMethods.add(verbs.next()+" "+normalized);
            }
        }
        return supportedMethods.contains(method+" "+target);
    }
    private void button(int title,Runnable action,boolean write) { Button button=Ui.button(activity,view.tr(title),action);button.setEnabled(!loading&&!mutating&&(!write||(!uncertain&&supportedAction(title))));body.addView(button,margin()); }
}


