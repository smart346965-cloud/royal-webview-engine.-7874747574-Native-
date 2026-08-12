package com.store.app;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class RoyalPanopticon {

    public static final String TAG = "[ROYAL_DIAGNOSTICS]";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String,EngineRecord> ENGINES = new ConcurrentHashMap<>();
    private static final Map<String,Set<String>> DEPS = new ConcurrentHashMap<>();
    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();
    private static final AtomicLong MAX_FREEZE = new AtomicLong();
    private static final AtomicLong TOTAL_FREEZE = new AtomicLong();
    private static final AtomicLong FREEZE_COUNT = new AtomicLong();

    private static ScheduledExecutorService EXEC;
    private static Context context;
    private static volatile boolean running;
    private static volatile long lastHeartbeat = System.currentTimeMillis();
    private static volatile long lastAnalysis;
    private static volatile long lastHeap;
    private static volatile long peakHeap;
    private static volatile long lastCpu;
    private static volatile long cpuStamp;

    private static final BrowserState BROWSER = new BrowserState();
    private static final NavigationMetric NAV = new NavigationMetric();
    private static final List<Anomaly> ANOMALIES = new CopyOnWriteArrayList<>();

    private static final Thread.UncaughtExceptionHandler ORIGINAL_HANDLER =
            Thread.getDefaultUncaughtExceptionHandler();

    private RoyalPanopticon(){}

    private static final class BrowserState {
        volatile int dom;
        volatile int fps = 60;
        volatile long memory;
        volatile int longTasks;
        volatile long lastUpdate = System.currentTimeMillis();
        final long[] fpsH = new long[12];
        final long[] domH = new long[12];
        final long[] memH = new long[12];
        int fi,di,mi,fc,dc,mc;

        synchronized void update(int d,int f,long m,int l){
            dom=d; fps=f; memory=m; longTasks=l;
            lastUpdate=System.currentTimeMillis();
            fpsH[fi]=f; fi=(fi+1)%fpsH.length; if(fc<fpsH.length)fc++;
            domH[di]=d; di=(di+1)%domH.length; if(dc<domH.length)dc++;
            memH[mi]=m; mi=(mi+1)%memH.length; if(mc<memH.length)mc++;
        }

        synchronized long avg(long[] a,int n,int c){
            if(c==0)return 0;
            long s=0;
            for(int i=0;i<c;i++)s+=a[i];
            return s/c;
        }

        synchronized long min(long[] a,int c){
            if(c==0)return 0;
            long x=Long.MAX_VALUE;
            for(int i=0;i<c;i++)x=Math.min(x,a[i]);
            return x;
        }

        synchronized boolean falling(long[] a,int c){
            if(c<4)return false;
            int drops=0;
            for(int i=1;i<c;i++)if(a[i]<a[i-1])drops++;
            return drops>=c/2;
        }

        synchronized boolean rising(long[] a,int c){
            if(c<4)return false;
            int rises=0;
            for(int i=1;i<c;i++)if(a[i]>a[i-1])rises++;
            return rises>=c/2;
        }
    }

    public static final class NavigationMetric {
        public String url="";
        public long clickTimestamp;
        public long requestSentTimestamp;
        public long firstByteTimestamp;
        public long domInteractiveTimestamp;
        public long domCompleteTimestamp;
        public long uiThreadBlockMs;

        synchronized void reset(String u){
            url=u==null?"":u;
            clickTimestamp=System.currentTimeMillis();
            requestSentTimestamp=0;
            firstByteTimestamp=0;
            domInteractiveTimestamp=0;
            domCompleteTimestamp=0;
            uiThreadBlockMs=0;
        }
    }

    private static final class EngineRecord {
        final String name;
        final AtomicLong ops=new AtomicLong();
        final AtomicLong failures=new AtomicLong();
        final AtomicLong latency=new AtomicLong();
        final AtomicLong peakMemory=new AtomicLong();
        volatile long lastPulse=System.currentTimeMillis();

        EngineRecord(String n){name=n;}

        double health(){
            long o=ops.get();
            return o==0?100:Math.max(0,100-(failures.get()*100.0/o));
        }

        double avg(){
            long o=ops.get();
            return o==0?0:latency.get()/(double)o;
        }
    }

    private static final class Anomaly {
        final String area;
        final String level;
        final String reason;
        final String evidence;
        Anomaly(String a,String l,String r,String e){
            area=a;level=l;reason=r;evidence=e;
        }
    }

    public static synchronized void startAwareness(){
        if(running)return;
        running=true;
        context=tryContext();

        Thread.setDefaultUncaughtExceptionHandler((t,e)->{
            log("💀 FATAL "+t.getName()+" "+e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));
            if(ORIGINAL_HANDLER!=null){
                try{ORIGINAL_HANDLER.uncaughtException(t,e);}catch(Throwable ignored){}
            }
        });

        EXEC=Executors.newSingleThreadScheduledExecutor(r->{
            Thread t=new Thread(r,"Panopticon-AI");
            t.setDaemon(true);
            return t;
        });

        EXEC.scheduleAtFixedRate(RoyalPanopticon::cycle,1,2,TimeUnit.SECONDS);

        log("👁 PANOPTICON ONLINE");
        log("Runtime="+Build.VERSION.RELEASE+" API="+Build.VERSION.SDK_INT);
        log("Process="+Process.myPid());
        logWebViewProvider();
    }

    public static synchronized void stopAwareness(){
        running=false;
        if(EXEC!=null){
            EXEC.shutdownNow();
            EXEC=null;
        }
        log("PANOPTICON OFFLINE");
    }

    private static void cycle(){
        if(!running)return;

        final long posted=System.nanoTime();

        MAIN.post(()->{
            long lag=(System.nanoTime()-posted)/1_000_000L;
            lastHeartbeat=System.currentTimeMillis();

            if(lag>=32){
                FREEZE_COUNT.incrementAndGet();
                TOTAL_FREEZE.addAndGet(lag);
                MAX_FREEZE.accumulateAndGet(lag,Math::max);

                synchronized(NAV){
                    if(NAV.clickTimestamp>0 && NAV.domCompleteTimestamp==0)
                        NAV.uiThreadBlockMs+=lag;
                }

                if(lag>=100)
                    log("UI_FREEZE "+lag+"ms");
            }
        });

        analyze();
    }

    private static void analyze(){
        ANOMALIES.clear();

        long now=System.currentTimeMillis();
        long silence=now-BROWSER.lastUpdate;

        long heap=Debug.getPss()/1024L;
        lastHeap=heap;
        peakHeap=Math.max(peakHeap,heap);

        if(heap>256)
            ANOMALIES.add(new Anomaly(
                    "MEMORY","HIGH",
                    "Application memory pressure is elevated.",
                    "PSS="+heap+"MB peak="+peakHeap+"MB"
            ));

        long freeze=MAX_FREEZE.get();

        if(freeze>=250)
            ANOMALIES.add(new Anomaly(
                    "MAIN_THREAD","CRITICAL",
                    "Main thread experienced a severe scheduling delay.",
                    "maxFreeze="+freeze+"ms count="+FREEZE_COUNT.get()
            ));
        else if(freeze>=100)
            ANOMALIES.add(new Anomaly(
                    "MAIN_THREAD","HIGH",
                    "Main thread experienced noticeable blocking.",
                    "maxFreeze="+freeze+"ms"
            ));

        synchronized(BROWSER){
            if(BROWSER.fps<45)
                ANOMALIES.add(new Anomaly(
                        "WEBVIEW_RENDER","HIGH",
                        "Reported WebView frame rate is low.",
                        "fps="+BROWSER.fps+" avg="+BROWSER.avg(BROWSER.fpsH,BROWSER.fc)
                ));

            if(BROWSER.longTasks>0)
                ANOMALIES.add(new Anomaly(
                        "JAVASCRIPT","MEDIUM",
                        "Long JavaScript tasks were reported by the Web layer.",
                        "longTasks="+BROWSER.longTasks
                ));

            if(BROWSER.dom>2000)
                ANOMALIES.add(new Anomaly(
                        "DOM","MEDIUM",
                        "DOM size is relatively high.",
                        "nodes="+BROWSER.dom
                ));

            if(BROWSER.fps<50 && BROWSER.longTasks>2)
                ANOMALIES.add(new Anomaly(
                        "RENDERING","HIGH",
                        "Low FPS correlates with JavaScript long tasks.",
                        "fps="+BROWSER.fps+" longTasks="+BROWSER.longTasks
                ));

            if(BROWSER.dom>0 && silence>15000)
                ANOMALIES.add(new Anomaly(
                        "WEBVIEW_BRIDGE","HIGH",
                        "Browser telemetry heartbeat has stopped.",
                        "silence="+silence+"ms dom="+BROWSER.dom
                ));
        }

        analyzeNavigation();
        analyzeEngines();
        analyzeThreads();

        lastAnalysis=now;
    }

    private static void analyzeNavigation(){
        synchronized(NAV){
            if(NAV.clickTimestamp==0 || NAV.domCompleteTimestamp==0)return;

            long total=NAV.domCompleteTimestamp-NAV.clickTimestamp;
            long bridge=validDiff(NAV.requestSentTimestamp,NAV.clickTimestamp);
            long server=validDiff(NAV.firstByteTimestamp,NAV.requestSentTimestamp);
            long render=validDiff(NAV.domCompleteTimestamp,NAV.firstByteTimestamp);

            if(total>=3000){
                if(server>total*.60)
                    ANOMALIES.add(new Anomaly(
                            "NETWORK","HIGH",
                            "Server/network delay dominates navigation.",
                            "total="+total+"ms server="+server+"ms"
                    ));
                else if(render>total*.60)
                    ANOMALIES.add(new Anomaly(
                            "WEBVIEW_RENDER","HIGH",
                            "Client rendering dominates navigation.",
                            "total="+total+"ms render="+render+"ms"
                    ));
                else if(bridge>500)
                    ANOMALIES.add(new Anomaly(
                            "BRIDGE","HIGH",
                            "Navigation bridge/transit delay is unusually high.",
                            "bridge="+bridge+"ms"
                    ));
            }
        }
    }

    private static long validDiff(long a,long b){
        return a>0&&b>0&&a>=b?a-b:0;
    }

    private static void analyzeEngines(){
        for(EngineRecord r:ENGINES.values()){
            long o=r.ops.get();
            if(o==0)continue;

            double avg=r.avg();

            if(r.failures.get()>0)
                ANOMALIES.add(new Anomaly(
                        r.name,"ERROR",
                        "Recorded engine failures.",
                        "ops="+o+" failures="+r.failures.get()
                ));

            if(avg>=100)
                ANOMALIES.add(new Anomaly(
                        r.name,"HIGH",
                        "Average recorded execution latency is high.",
                        "avg="+String.format(Locale.US,"%.1f",avg)+"ms"
                ));
        }
    }

    private static void analyzeThreads(){
        if(context==null)return;

        try{
            ActivityManager am=(ActivityManager)
                    context.getSystemService(Context.ACTIVITY_SERVICE);

            if(am==null)return;

            List<ActivityManager.RunningAppProcessInfo> ps=
                    am.getRunningAppProcesses();

            if(ps==null)return;

            int own=0;
            StringBuilder s=new StringBuilder();

            for(ActivityManager.RunningAppProcessInfo p:ps){
                if(p.pid==Process.myPid()||
                   (p.processName!=null&&
                    p.processName.startsWith(context.getPackageName()))){
                    own++;
                    if(s.length()<500)s.append(p.processName).append(" ");
                }
            }

            if(own>8)
                ANOMALIES.add(new Anomaly(
                        "THREAD_PROCESS","MEDIUM",
                        "Multiple application processes are active.",
                        "processes="+own+" "+s
                ));
        }catch(Throwable ignored){}
    }

    private static void logWebViewProvider(){
        try{
            if(Build.VERSION.SDK_INT>=26){
                android.content.pm.PackageInfo p=
                        WebView.getCurrentWebViewPackage();

                if(p!=null)
                    log("WEBVIEW_PROVIDER "+
                            p.packageName+" "+p.versionName);
            }else{
                log("WEBVIEW_PROVIDER "+WebViewFactory.getLoadedPackageInfo());
            }
        }catch(Throwable e){
            log("WEBVIEW_PROVIDER unavailable");
        }
    }

    public static void syncBrowserState(
            int nodes,int fps,long memory,int tasks){
        BROWSER.update(nodes,fps,memory,tasks);
    }

    public static void recordMetric(String name,long value){
        if(name==null)return;
        log("METRIC "+name+"="+value+"ms");
    }

    public static void recordUserClick(String url){
        synchronized(NAV){NAV.reset(url);}
        pulse("Navigation");
    }

    public static void recordRequestSent(){
        synchronized(NAV){
            if(NAV.clickTimestamp>0)
                NAV.requestSentTimestamp=System.currentTimeMillis();
        }
    }

    public static void recordFirstByteReceived(){
        synchronized(NAV){
            if(NAV.requestSentTimestamp>0)
                NAV.firstByteTimestamp=System.currentTimeMillis();
        }
    }

    public static void recordDomInteractive(){
        synchronized(NAV){
            if(NAV.clickTimestamp>0)
                NAV.domInteractiveTimestamp=System.currentTimeMillis();
        }
    }

    public static void recordNavigationComplete(){
        synchronized(NAV){
            if(NAV.clickTimestamp>0){
                NAV.domCompleteTimestamp=System.currentTimeMillis();
                printTransition();
            }
        }
    }

    public static void pulse(String name){
        if(name==null)return;
        get(name).lastPulse=System.currentTimeMillis();
    }

    public static void recordExecution(
            String name,long latencyMs,boolean success,long memoryBytes){
        if(name==null)return;

        EngineRecord r=get(name);
        r.ops.incrementAndGet();
        r.latency.addAndGet(Math.max(0,latencyMs));

        if(!success)r.failures.incrementAndGet();

        r.peakMemory.accumulateAndGet(
                Math.max(0,memoryBytes),Math::max);
    }

    public static void registerDependency(String parent,String child){
        if(parent==null||child==null)return;

        DEPS.computeIfAbsent(
                parent,
                k->ConcurrentHashMap.newKeySet()
        ).add(child);

        get(parent);
        get(child);
    }

    private static EngineRecord get(String name){
        return ENGINES.computeIfAbsent(name,EngineRecord::new);
    }

    public static void printFullDiagnosticsReport(){
        Log.i(TAG,buildReport());
    }

    public static String buildReport(){
        analyze();

        StringBuilder s=new StringBuilder(4096);

        s.append("\n=== ROYAL PANOPTICON V5 ===\n");

        s.append("SYSTEM\n");
        s.append("API=").append(Build.VERSION.SDK_INT)
         .append(" Android=").append(Build.VERSION.RELEASE).append("\n");

        s.append("PSS=").append(lastHeap)
         .append("MB Peak=").append(peakHeap).append("MB\n");

        s.append("UI\n");
        s.append("MaxFreeze=").append(MAX_FREEZE.get())
         .append("ms Count=").append(FREEZE_COUNT.get()).append("\n");

        s.append("WEBVIEW\n");
        s.append("FPS=").append(BROWSER.fps)
         .append(" AvgFPS=")
         .append(BROWSER.avg(BROWSER.fpsH,BROWSER.fc))
         .append(" MinFPS=")
         .append(BROWSER.min(BROWSER.fpsH,BROWSER.fc)).append("\n");

        s.append("DOM=").append(BROWSER.dom)
         .append(" JSHeap=").append(BROWSER.memory)
         .append("MB LongTasks=").append(BROWSER.longTasks).append("\n");

        s.append("BridgeSilence=")
         .append(System.currentTimeMillis()-BROWSER.lastUpdate)
         .append("ms\n");

        synchronized(NAV){
            if(NAV.clickTimestamp>0){
                long total=validDiff(
                        NAV.domCompleteTimestamp,
                        NAV.clickTimestamp);

                long bridge=validDiff(
                        NAV.requestSentTimestamp,
                        NAV.clickTimestamp);

                long server=validDiff(
                        NAV.firstByteTimestamp,
                        NAV.requestSentTimestamp);

                long render=validDiff(
                        NAV.domCompleteTimestamp,
                        NAV.firstByteTimestamp);

                s.append("NAV\n");
                s.append("URL=").append(NAV.url).append("\n");
                s.append("Total=").append(total)
                 .append("ms Bridge=").append(bridge)
                 .append("ms Server=").append(server)
                 .append("ms Render=").append(render)
                 .append("ms UIBlock=").append(NAV.uiThreadBlockMs)
                 .append("ms\n");
            }
        }

        s.append("ENGINES\n");

        for(EngineRecord r:ENGINES.values()){
            s.append(r.name)
             .append(" ops=").append(r.ops.get())
             .append(" fail=").append(r.failures.get())
             .append(" avg=").append(
                     String.format(Locale.US,"%.1f",r.avg()))
             .append("ms health=")
             .append(String.format(Locale.US,"%.1f",r.health()))
             .append("%\n");
        }

        s.append("ANOMALIES=").append(ANOMALIES.size()).append("\n");

        if(ANOMALIES.isEmpty()){
            s.append("STATUS=HEALTHY\n");
        }else{
            int i=1;
            for(Anomaly a:ANOMALIES){
                s.append(i++).append(". ")
                 .append(a.level)
                 .append(" | ")
                 .append(a.area)
                 .append(" | ")
                 .append(a.reason)
                 .append(" | ")
                 .append(a.evidence)
                 .append("\n");
            }
        }

        s.append("=== END ===\n");
        return s.toString();
    }

    private static void printTransition(){
        synchronized(NAV){
            long total=validDiff(
                    NAV.domCompleteTimestamp,
                    NAV.clickTimestamp);

            long bridge=validDiff(
                    NAV.requestSentTimestamp,
                    NAV.clickTimestamp);

            long server=validDiff(
                    NAV.firstByteTimestamp,
                    NAV.requestSentTimestamp);

            long render=validDiff(
                    NAV.domCompleteTimestamp,
                    NAV.firstByteTimestamp);

            log("NAV total="+total+
                    "ms bridge="+bridge+
                    "ms server="+server+
                    "ms render="+render+
                    "ms ui="+NAV.uiThreadBlockMs+"ms");
        }
    }

    private static void log(String s){
        Log.i(TAG,s);
    }

    private static Context tryContext(){
        try{
            return (Context)Class
                    .forName("android.app.ActivityThread")
                    .getMethod("currentApplication")
                    .invoke(null);
        }catch(Throwable ignored){
            return null;
        }
    }
    }
