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

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class RoyalPanopticon {

    public static final String TAG = "[ROYAL_DIAGNOSTICS]";

    private static final Handler MAIN =
            new Handler(Looper.getMainLooper());

    private static final Map<String, EngineRecord> ENGINES =
            new ConcurrentHashMap<>();

    private static final Map<String, Set<String>> DEPS =
            new ConcurrentHashMap<>();

    private static final List<Anomaly> ANOMALIES =
            new CopyOnWriteArrayList<>();

    private static final AtomicLong MAX_FREEZE =
            new AtomicLong(0);

    private static final AtomicLong TOTAL_FREEZE =
            new AtomicLong(0);

    private static final AtomicLong FREEZE_COUNT =
            new AtomicLong(0);

    private static ScheduledExecutorService EXEC;
    private static Context context;

    private static volatile boolean running;
    private static volatile long lastHeartbeat =
            System.currentTimeMillis();

    private static volatile long lastAnalysis;
    private static volatile long lastHeap;
    private static volatile long peakHeap;

    private static final BrowserState BROWSER =
            new BrowserState();

    private static final NavigationMetric NAV =
            new NavigationMetric();

    private static final Thread.UncaughtExceptionHandler
            ORIGINAL_HANDLER =
            Thread.getDefaultUncaughtExceptionHandler();

    private RoyalPanopticon() {}

    private static final class BrowserState {

        volatile int dom;
        volatile int fps = 60;
        volatile long memory;
        volatile int longTasks;

        volatile long lastUpdate =
                System.currentTimeMillis();

        final long[] fpsH = new long[12];
        final long[] domH = new long[12];
        final long[] memH = new long[12];

        int fi;
        int di;
        int mi;

        int fc;
        int dc;
        int mc;

        synchronized void update(
                int d,
                int f,
                long m,
                int l) {

            dom = Math.max(0, d);
            fps = Math.max(0, f);
            memory = Math.max(0, m);
            longTasks = Math.max(0, l);

            lastUpdate = System.currentTimeMillis();

            fpsH[fi] = fps;
            fi = (fi + 1) % fpsH.length;
            if (fc < fpsH.length) fc++;

            domH[di] = dom;
            di = (di + 1) % domH.length;
            if (dc < domH.length) dc++;

            memH[mi] = memory;
            mi = (mi + 1) % memH.length;
            if (mc < memH.length) mc++;
        }

        synchronized long avg(
                long[] data,
                int count) {

            if (count <= 0) return 0;

            long sum = 0;

            for (int i = 0; i < count; i++) {
                sum += data[i];
            }

            return sum / count;
        }

        synchronized long min(
                long[] data,
                int count) {

            if (count <= 0) return 0;

            long value = Long.MAX_VALUE;

            for (int i = 0; i < count; i++) {
                value = Math.min(value, data[i]);
            }

            return value;
        }

        synchronized boolean falling(
                long[] data,
                int count) {

            if (count < 4) return false;

            int drops = 0;

            for (int i = 1; i < count; i++) {
                if (data[i] < data[i - 1]) {
                    drops++;
                }
            }

            return drops >= Math.max(2, count / 2);
        }

        synchronized boolean rising(
                long[] data,
                int count) {

            if (count < 4) return false;

            int rises = 0;

            for (int i = 1; i < count; i++) {
                if (data[i] > data[i - 1]) {
                    rises++;
                }
            }

            return rises >= Math.max(2, count / 2);
        }
    }

    public static final class NavigationMetric {

        public String url = "";

        public long clickTimestamp;
        public long requestSentTimestamp;
        public long firstByteTimestamp;
        public long domInteractiveTimestamp;
        public long domCompleteTimestamp;
        public long uiThreadBlockMs;

        synchronized void reset(String value) {

            url = value == null ? "" : value;

            clickTimestamp =
                    System.currentTimeMillis();

            requestSentTimestamp = 0;
            firstByteTimestamp = 0;
            domInteractiveTimestamp = 0;
            domCompleteTimestamp = 0;
            uiThreadBlockMs = 0;
        }
    }

    private static final class EngineRecord {

        final String name;

        final AtomicLong operations =
                new AtomicLong(0);

        final AtomicLong failures =
                new AtomicLong(0);

        final AtomicLong latency =
                new AtomicLong(0);

        final AtomicLong peakMemory =
                new AtomicLong(0);

        volatile long lastPulse =
                System.currentTimeMillis();

        EngineRecord(String value) {
            name = value;
        }

        double health() {

            long total = operations.get();

            if (total == 0) {
                return 100.0;
            }

            double failed =
                    failures.get() * 100.0 / total;

            return Math.max(
                    0.0,
                    Math.min(100.0, 100.0 - failed)
            );
        }

        double averageLatency() {

            long total = operations.get();

            if (total == 0) {
                return 0.0;
            }

            return latency.get() / (double) total;
        }
    }

    private static final class Anomaly {

        final String area;
        final String level;
        final String reason;
        final String evidence;

        Anomaly(
                String a,
                String l,
                String r,
                String e) {

            area = a;
            level = l;
            reason = r;
            evidence = e;
        }
    }

    public static synchronized void startAwareness() {

        if (running) {
            return;
        }

        running = true;

        context = tryContext();

        installCrashObserver();

        EXEC = Executors.newSingleThreadScheduledExecutor(
                r -> {

                    Thread t =
                            new Thread(
                                    r,
                                    "Panopticon-AI"
                            );

                    t.setDaemon(true);

                    return t;
                }
        );

        EXEC.scheduleAtFixedRate(
                RoyalPanopticon::cycle,
                1,
                2,
                TimeUnit.SECONDS
        );

        log("PANOPTICON ONLINE");

        log(
                "ANDROID API=" +
                Build.VERSION.SDK_INT +
                " RELEASE=" +
                Build.VERSION.RELEASE
        );

        log(
                "PROCESS PID=" +
                Process.myPid()
        );

        logWebViewProvider();
    }

    public static synchronized void stopAwareness() {

        running = false;

        if (EXEC != null) {

            EXEC.shutdownNow();
            EXEC = null;
        }

        log("PANOPTICON OFFLINE");
    }

    private static void installCrashObserver() {

        try {

            Thread.setDefaultUncaughtExceptionHandler(
                    (thread, throwable) -> {

                        log(
                                "FATAL_EXCEPTION thread=" +
                                thread.getName() +
                                " type=" +
                                throwable.getClass().getSimpleName() +
                                " message=" +
                                String.valueOf(
                                        throwable.getMessage()
                                )
                        );

                        if (ORIGINAL_HANDLER != null) {

                            try {
                                ORIGINAL_HANDLER
                                        .uncaughtException(
                                                thread,
                                                throwable
                                        );
                            } catch (Throwable ignored) {
                            }
                        }
                    }
            );

        } catch (Throwable ignored) {
        }
    }

    private static void cycle() {

        if (!running) {
            return;
        }

        final long posted =
                System.nanoTime();

        MAIN.post(() -> {

            long lag =
                    (System.nanoTime() - posted)
                            / 1_000_000L;

            lastHeartbeat =
                    System.currentTimeMillis();

            if (lag >= 16) {

                FREEZE_COUNT.incrementAndGet();

                TOTAL_FREEZE.addAndGet(lag);

                MAX_FREEZE.accumulateAndGet(
                        lag,
                        Math::max
                );

                synchronized (NAV) {

                    if (
                            NAV.clickTimestamp > 0 &&
                            NAV.domCompleteTimestamp == 0
                    ) {

                        NAV.uiThreadBlockMs += lag;
                    }
                }

                if (lag >= 100) {

                    log(
                            "STEP UI_THREAD lag=" +
                            lag +
                            "ms"
                    );
                }
            }
        });

        analyze();
    }

    private static void analyze() {

        ANOMALIES.clear();

        long now =
                System.currentTimeMillis();

        long heap;

        try {

            heap =
                    Debug.getPss() / 1024L;

        } catch (Throwable ignored) {

            heap = 0;
        }

        lastHeap = heap;

        if (heap > peakHeap) {
            peakHeap = heap;
        }

        analyzeMemory(heap);
        analyzeMainThread();
        analyzeWebView();
        analyzeNavigation();
        analyzeEngines();
        analyzeProcesses();

        lastAnalysis = now;
    }

    private static void analyzeMemory(long heap) {

        if (heap <= 0) {
            return;
        }

        if (heap >= 512) {

            ANOMALIES.add(
                    new Anomaly(
                            "MEMORY",
                            "CRITICAL",
                            "Very high application PSS detected.",
                            "PSS=" + heap +
                            "MB peak=" +
                            peakHeap + "MB"
                    )
            );

        } else if (heap >= 256) {

            ANOMALIES.add(
                    new Anomaly(
                            "MEMORY",
                            "HIGH",
                            "Application memory pressure is elevated.",
                            "PSS=" + heap +
                            "MB peak=" +
                            peakHeap + "MB"
                    )
            );
        }
    }

    private static void analyzeMainThread() {

        long freeze =
                MAX_FREEZE.get();

        if (freeze >= 1000) {

            ANOMALIES.add(
                    new Anomaly(
                            "MAIN_THREAD",
                            "CRITICAL",
                            "Main thread suffered a severe scheduling delay.",
                            "max=" +
                            freeze +
                            "ms count=" +
                            FREEZE_COUNT.get()
                    )
            );

        } else if (freeze >= 250) {

            ANOMALIES.add(
                    new Anomaly(
                            "MAIN_THREAD",
                            "HIGH",
                            "Main thread experienced heavy blocking.",
                            "max=" +
                            freeze +
                            "ms count=" +
                            FREEZE_COUNT.get()
                    )
            );

        } else if (freeze >= 100) {

            ANOMALIES.add(
                    new Anomaly(
                            "MAIN_THREAD",
                            "MEDIUM",
                            "Main thread experienced noticeable delay.",
                            "max=" +
                            freeze +
                            "ms"
                    )
            );
        }
    }

    private static void analyzeWebView() {

        synchronized (BROWSER) {

            long avgFps =
                    BROWSER.avg(
                            BROWSER.fpsH,
                            BROWSER.fc
                    );

            long minFps =
                    BROWSER.min(
                            BROWSER.fpsH,
                            BROWSER.fc
                    );

            if (BROWSER.fps > 0 &&
                    BROWSER.fps < 45) {

                ANOMALIES.add(
                        new Anomaly(
                                "WEBVIEW_RENDER",
                                "HIGH",
                                "Reported WebView FPS is low.",
                                "fps=" +
                                BROWSER.fps +
                                " avg=" +
                                avgFps +
                                " min=" +
                                minFps
                        )
                );
            }

            if (BROWSER.longTasks >= 5) {

                ANOMALIES.add(
                        new Anomaly(
                                "JAVASCRIPT",
                                "HIGH",
                                "Multiple long JavaScript tasks were reported.",
                                "longTasks=" +
                                BROWSER.longTasks
                        )
                );

            } else if (BROWSER.longTasks > 0) {

                ANOMALIES.add(
                        new Anomaly(
                                "JAVASCRIPT",
                                "MEDIUM",
                                "JavaScript long tasks were reported.",
                                "longTasks=" +
                                BROWSER.longTasks
                        )
                );
            }

            if (BROWSER.dom >= 5000) {

                ANOMALIES.add(
                        new Anomaly(
                                "DOM",
                                "HIGH",
                                "DOM node count is very high.",
                                "nodes=" +
                                BROWSER.dom
                        )
                );

            } else if (BROWSER.dom >= 2000) {

                ANOMALIES.add(
                        new Anomaly(
                                "DOM",
                                "MEDIUM",
                                "DOM node count is relatively high.",
                                "nodes=" +
                                BROWSER.dom
                        )
                );
            }

            if (
                    BROWSER.fps > 0 &&
                    BROWSER.fps < 50 &&
                    BROWSER.longTasks >= 2
            ) {

                ANOMALIES.add(
                        new Anomaly(
                                "RENDERING",
                                "HIGH",
                                "Low FPS correlates with JavaScript long tasks.",
                                "fps=" +
                                BROWSER.fps +
                                " longTasks=" +
                                BROWSER.longTasks
                        )
                );
            }

            long silence =
                    System.currentTimeMillis() -
                    BROWSER.lastUpdate;

            if (
                    BROWSER.dom > 0 &&
                    silence > 15000
            ) {

                ANOMALIES.add(
                        new Anomaly(
                                "WEBVIEW_BRIDGE",
                                "HIGH",
                                "Web telemetry heartbeat has stopped.",
                                "silence=" +
                                silence +
                                "ms dom=" +
                                BROWSER.dom
                        )
                );
            }
        }
    }

    private static void analyzeNavigation() {

        synchronized (NAV) {

            if (
                    NAV.clickTimestamp == 0 ||
                    NAV.domCompleteTimestamp == 0
            ) {
                return;
            }

            long total =
                    diff(
                            NAV.domCompleteTimestamp,
                            NAV.clickTimestamp
                    );

            long bridge =
                    diff(
                            NAV.requestSentTimestamp,
                            NAV.clickTimestamp
                    );

            long server =
                    diff(
                            NAV.firstByteTimestamp,
                            NAV.requestSentTimestamp
                    );

            long render =
                    diff(
                            NAV.domCompleteTimestamp,
                            NAV.firstByteTimestamp
                    );

            if (total < 3000) {
                return;
            }

            if (
                    server > 0 &&
                    server >= total * 0.60
            ) {

                ANOMALIES.add(
                        new Anomaly(
                                "NETWORK",
                                "HIGH",
                                "Server/network delay dominates navigation.",
                                "total=" +
                                total +
                                "ms server=" +
                                server +
                                "ms"
                        )
                );

            } else if (
                    render > 0 &&
                    render >= total * 0.60
            ) {

                ANOMALIES.add(
                        new Anomaly(
                                "WEBVIEW_RENDER",
                                "HIGH",
                                "Client rendering dominates navigation.",
                                "total=" +
                                total +
                                "ms render=" +
                                render +
                                "ms"
                        )
                );

            } else if (bridge >= 500) {

                ANOMALIES.add(
                        new Anomaly(
                                "BRIDGE",
                                "HIGH",
                                "Navigation bridge delay is unusually high.",
                                "bridge=" +
                                bridge +
                                "ms"
                        )
                );
            }
        }
    }

    private static void analyzeEngines() {

        for (EngineRecord r : ENGINES.values()) {

            long operations =
                    r.operations.get();

            if (operations == 0) {
                continue;
            }

            long failures =
                    r.failures.get();

            double avg =
                    r.averageLatency();

            if (failures > 0) {

                ANOMALIES.add(
                        new Anomaly(
                                r.name,
                                "ERROR",
                                "Recorded engine failures detected.",
                                "ops=" +
                                operations +
                                " failures=" +
                                failures
                        )
                );
            }

            if (avg >= 250) {

                ANOMALIES.add(
                        new Anomaly(
                                r.name,
                                "HIGH",
                                "Average execution latency is very high.",
                                "avg=" +
                                format(avg) +
                                "ms"
                        )
                );

            } else if (avg >= 100) {

                ANOMALIES.add(
                        new Anomaly(
                                r.name,
                                "MEDIUM",
                                "Average execution latency is elevated.",
                                "avg=" +
                                format(avg) +
                                "ms"
                        )
                );
            }
        }
    }

    private static void analyzeProcesses() {

        if (context == null) {
            return;
        }

        try {

            ActivityManager manager =
                    (ActivityManager)
                            context.getSystemService(
                                    Context.ACTIVITY_SERVICE
                            );

            if (manager == null) {
                return;
            }

            List<ActivityManager.RunningAppProcessInfo>
                    processes =
                    manager.getRunningAppProcesses();

            if (processes == null) {
                return;
            }

            int ownProcesses = 0;

            for (
                    ActivityManager.RunningAppProcessInfo p :
                    processes
            ) {

                if (
                        p.pid == Process.myPid() ||
                        (
                                p.processName != null &&
                                p.processName.startsWith(
                                        context.getPackageName()
                                )
                        )
                ) {

                    ownProcesses++;
                }
            }

            if (ownProcesses > 4) {

                ANOMALIES.add(
                        new Anomaly(
                                "PROCESS",
                                "MEDIUM",
                                "Multiple application processes detected.",
                                "processes=" +
                                ownProcesses
                        )
                );
            }

        } catch (Throwable ignored) {
        }
    }

    private static void logWebViewProvider() {

        try {

            if (Build.VERSION.SDK_INT >= 26) {

                android.content.pm.PackageInfo info =
                        WebView.getCurrentWebViewPackage();

                if (info != null) {

                    log(
                            "WEBVIEW_PROVIDER " +
                            info.packageName +
                            " version=" +
                            info.versionName
                    );

                } else {

                    log(
                            "WEBVIEW_PROVIDER unavailable"
                    );
                }

            } else {

                log(
                        "WEBVIEW_PROVIDER legacy_api_" +
                        Build.VERSION.SDK_INT
                );
            }

        } catch (Throwable e) {

            log(
                    "WEBVIEW_PROVIDER unavailable"
            );
        }
    }

    public static void syncBrowserState(
            int nodes,
            int fps,
            long memory,
            int tasks) {

        BROWSER.update(
                nodes,
                fps,
                memory,
                tasks
        );
    }

    public static void recordMetric(
            String name,
            long value) {

        if (name == null) {
            return;
        }

        log(
                "METRIC " +
                name +
                "=" +
                value +
                "ms"
        );
    }

    public static void recordUserClick(
            String url) {

        synchronized (NAV) {
            NAV.reset(url);
        }

        pulse("Navigation");
    }

    public static void recordRequestSent() {

        synchronized (NAV) {

            if (NAV.clickTimestamp > 0) {

                NAV.requestSentTimestamp =
                        System.currentTimeMillis();
            }
        }
    }

    public static void recordFirstByteReceived() {

        synchronized (NAV) {

            if (NAV.requestSentTimestamp > 0) {

                NAV.firstByteTimestamp =
                        System.currentTimeMillis();
            }
        }
    }

    public static void recordDomInteractive() {

        synchronized (NAV) {

            if (NAV.clickTimestamp > 0) {

                NAV.domInteractiveTimestamp =
                        System.currentTimeMillis();
            }
        }
    }

    public static void recordNavigationComplete() {

        synchronized (NAV) {

            if (NAV.clickTimestamp > 0) {

                NAV.domCompleteTimestamp =
                        System.currentTimeMillis();

                printTransition();
            }
        }
    }

    public static void pulse(
            String name) {

        if (name == null) {
            return;
        }

        get(name).lastPulse =
                System.currentTimeMillis();
    }

    public static void recordExecution(
            String name,
            long latencyMs,
            boolean success,
            long memoryBytes) {

        if (name == null) {
            return;
        }

        EngineRecord r =
                get(name);

        r.operations.incrementAndGet();

        r.latency.addAndGet(
                Math.max(0, latencyMs)
        );

        if (!success) {
            r.failures.incrementAndGet();
        }

        r.peakMemory.accumulateAndGet(
                Math.max(0, memoryBytes),
                Math::max
        );
    }

    public static void registerDependency(
            String parent,
            String child) {

        if (
                parent == null ||
                child == null
        ) {
            return;
        }

        DEPS.computeIfAbsent(
                parent,
                k -> ConcurrentHashMap.newKeySet()
        ).add(child);

        get(parent);
        get(child);
    }

    private static EngineRecord get(
            String name) {

        return ENGINES.computeIfAbsent(
                name,
                EngineRecord::new
        );
    }

    public static void printFullDiagnosticsReport() {

        Log.i(
                TAG,
                buildReport()
        );
    }

    public static String buildReport() {

        analyze();

        StringBuilder s =
                new StringBuilder(8192);

        s.append(
                "\n=== ROYAL PANOPTICON V5.1 ===\n"
        );

        s.append(
                "STEP 01 | SYSTEM\n"
        );

        s.append("API=")
                .append(Build.VERSION.SDK_INT)
                .append(" Android=")
                .append(Build.VERSION.RELEASE)
                .append("\n");

        s.append("PID=")
                .append(Process.myPid())
                .append("\n");

        s.append(
                "STEP 02 | WEBVIEW PROVIDER\n"
        );

        appendWebViewProvider(s);

        s.append(
                "STEP 03 | MEMORY\n"
        );

        s.append("PSS=")
                .append(lastHeap)
                .append("MB Peak=")
                .append(peakHeap)
                .append("MB\n");

        s.append(
                "STEP 04 | MAIN THREAD\n"
        );

        s.append("MaxFreeze=")
                .append(MAX_FREEZE.get())
                .append("ms Count=")
                .append(FREEZE_COUNT.get())
                .append("\n");

        long averageFreeze = 0;

        long count =
                FREEZE_COUNT.get();

        if (count > 0) {

            averageFreeze =
                    TOTAL_FREEZE.get() / count;
        }

        s.append("AvgFreeze=")
                .append(averageFreeze)
                .append("ms\n");

        s.append(
                "STEP 05 | WEBVIEW\n"
        );

        synchronized (BROWSER) {

            s.append("FPS=")
                    .append(BROWSER.fps)
                    .append(" AvgFPS=")
                    .append(
                            BROWSER.avg(
                                    BROWSER.fpsH,
                                    BROWSER.fc
                            )
                    )
                    .append(" MinFPS=")
                    .append(
                            BROWSER.min(
                                    BROWSER.fpsH,
                                    BROWSER.fc
                            )
                    )
                    .append("\n");

            s.append("DOM=")
                    .append(BROWSER.dom)
                    .append(" JSHeap=")
                    .append(BROWSER.memory)
                    .append("MB LongTasks=")
                    .append(BROWSER.longTasks)
                    .append("\n");

            s.append("BridgeSilence=")
                    .append(
                            System.currentTimeMillis()
                            - BROWSER.lastUpdate
                    )
                    .append("ms\n");
        }

        s.append(
                "STEP 06 | NAVIGATION\n"
        );

        synchronized (NAV) {

            if (NAV.clickTimestamp > 0) {

                long total =
                        diff(
                                NAV.domCompleteTimestamp,
                                NAV.clickTimestamp
                        );

                long bridge =
                        diff(
                                NAV.requestSentTimestamp,
                                NAV.clickTimestamp
                        );

                long server =
                        diff(
                                NAV.firstByteTimestamp,
                                NAV.requestSentTimestamp
                        );

                long render =
                        diff(
                                NAV.domCompleteTimestamp,
                                NAV.firstByteTimestamp
                        );

                s.append("URL=")
                        .append(NAV.url)
                        .append("\n");

                s.append("Total=")
                        .append(total)
                        .append("ms Bridge=")
                        .append(bridge)
                        .append("ms Server=")
                        .append(server)
                        .append("ms Render=")
                        .append(render)
                        .append("ms UIBlock=")
                        .append(NAV.uiThreadBlockMs)
                        .append("ms\n");

            } else {

                s.append(
                        "No navigation sample\n"
                );
            }
        }

        s.append(
                "STEP 07 | ENGINES\n"
        );

        for (EngineRecord r :
                ENGINES.values()) {

            s.append(r.name)
                    .append(" ops=")
                    .append(r.operations.get())
                    .append(" fail=")
                    .append(r.failures.get())
                    .append(" avg=")
                    .append(
                            format(
                                    r.averageLatency()
                            )
                    )
                    .append("ms health=")
                    .append(
                            format(
                                    r.health()
                            )
                    )
                    .append("%\n");
        }

        s.append(
                "STEP 08 | ANOMALIES\n"
        );

        if (ANOMALIES.isEmpty()) {

            s.append(
                    "STATUS=HEALTHY\n"
            );

        } else {

            int index = 1;

            for (Anomaly a :
                    ANOMALIES) {

                s.append(index++)
                        .append(". ")
                        .append(a.level)
                        .append(" | ")
                        .append(a.area)
                        .append("\n");

                s.append("   reason=")
                        .append(a.reason)
                        .append("\n");

                s.append("   evidence=")
                        .append(a.evidence)
                        .append("\n");
            }
        }

        s.append(
                "STEP 09 | STATUS\n"
        );

        s.append("ANOMALIES=")
                .append(ANOMALIES.size())
                .append("\n");

        s.append(
                "LastAnalysis=")
                .append(lastAnalysis)
                .append("\n");

        s.append(
                "=== END PANOPTICON ===\n"
        );

        return s.toString();
    }

    private static void appendWebViewProvider(
            StringBuilder s) {

        try {

            if (Build.VERSION.SDK_INT >= 26) {

                android.content.pm.PackageInfo info =
                        WebView.getCurrentWebViewPackage();

                if (info != null) {

                    s.append("Package=")
                            .append(info.packageName)
                            .append("\n");

                    s.append("Version=")
                            .append(info.versionName)
                            .append("\n");

                } else {

                    s.append(
                            "Provider=UNKNOWN\n"
                    );
                }

            } else {

                s.append(
                        "Provider=LEGACY_API\n"
                );
            }

        } catch (Throwable e) {

            s.append(
                    "Provider=UNKNOWN\n"
            );
        }
    }

    private static void printTransition() {

        synchronized (NAV) {

            long total =
                    diff(
                            NAV.domCompleteTimestamp,
                            NAV.clickTimestamp
                    );

            long bridge =
                    diff(
                            NAV.requestSentTimestamp,
                            NAV.clickTimestamp
                    );

            long server =
                    diff(
                            NAV.firstByteTimestamp,
                            NAV.requestSentTimestamp
                    );

            long render =
                    diff(
                            NAV.domCompleteTimestamp,
                            NAV.firstByteTimestamp
                    );

            log(
                    "NAV total=" +
                    total +
                    "ms bridge=" +
                    bridge +
                    "ms server=" +
                    server +
                    "ms render=" +
                    render +
                    "ms ui=" +
                    NAV.uiThreadBlockMs +
                    "ms"
            );
        }
    }

    private static long diff(
            long a,
            long b) {

        if (
                a <= 0 ||
                b <= 0 ||
                a < b
        ) {
            return 0;
        }

        return a - b;
    }

    private static String format(
            double value) {

        return String.format(
                Locale.US,
                "%.1f",
                value
        );
    }

    private static void log(
            String value) {

        Log.i(
                TAG,
                value == null ? "" : value
        );
    }

    private static Context tryContext() {

        try {

            return (Context)
                    Class.forName(
                            "android.app.ActivityThread"
                    )
                    .getMethod(
                            "currentApplication"
                    )
                    .invoke(null);

        } catch (Throwable ignored) {

            return null;
        }
    }
    }
