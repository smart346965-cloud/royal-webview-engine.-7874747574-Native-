package com.store.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * =========================================================================
 * 👁️ ROYAL PANOPTICON - THE SUPREME DIAGNOSTIC ENGINE (V3.0)
 * =========================================================================
 * - Designed specifically for LogFox instant-search filter: [ROYAL_DIAGNOSTICS]
 * - Detects UI Thread freezes & App Lag causes instantly.
 * - Profiles Network Latency vs JS/DOM Render bottleneck.
 */
public final class RoyalPanopticon {

    public static final String TAG = "[ROYAL_DIAGNOSTICS]";

    private static final Map<String, EngineRecord> engineRecords = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> dependencies = new ConcurrentHashMap<>();
    private static ScheduledExecutorService analyzerThread;

    // =====================================================================
    // 🛡️ THE RING BUFFER (ذاكرة دائرية بدون استهلاك للرام)
    // =====================================================================
    private static class RingBuffer {
        private final long[] data;
        private int head = 0;
        private int count = 0;
        private final int capacity;

        RingBuffer(int capacity) {
            this.capacity = capacity;
            this.data = new long[capacity];
        }

        synchronized void push(long value) {
            data[head] = value;
            head = (head + 1) % capacity;
            if (count < capacity) count++;
        }

        synchronized boolean isDropping() {
            if (count < 3) return false;
            long oldest = data[(head - count + capacity) % capacity];
            long newest = data[(head - 1 + capacity) % capacity];
            return newest < oldest;
        }

        synchronized boolean isIncreasing() {
            if (count < 3) return false;
            long oldest = data[(head - count + capacity) % capacity];
            long newest = data[(head - 1 + capacity) % capacity];
            return newest > oldest;
        }
    }

    // =====================================================================
    // 🌟 THE INTROSPECTION & PROFILING LAYER
    // =====================================================================
    private static class BrowserState {
        volatile int domNodes = 0;
        volatile int fps = 60;
        volatile long jsMemoryMB = 0;
        volatile int longTasks = 0;
        volatile long lastUpdate = System.currentTimeMillis();

        final RingBuffer fpsHistory = new RingBuffer(10);
        final RingBuffer domHistory = new RingBuffer(10);
        final RingBuffer memoryHistory = new RingBuffer(10);
    }
    private static final BrowserState browserState = new BrowserState();

    // هيكل بيانات لتتبع النقرات وسرعة تحميل الصفحات بدقة متناهية
    public static class NavigationMetric {
        public String url = "";
        public long clickTimestamp = 0;
        public long requestSentTimestamp = 0;
        public long firstByteTimestamp = 0;      // وقت استجابة السيرفر الأولى (TTFB)
        public long domInteractiveTimestamp = 0; // وقت استجابة المتصفح الأولى
        public long domCompleteTimestamp = 0;    // نهاية رندر الصفحة بالكامل
        public long uiThreadBlockMs = 0;         // مدة تجمد الواجهة خلال تحميل الصفحة
    }
    private static final NavigationMetric currentNav = new NavigationMetric();

    // مراقب تجمد خيط الواجهة الرئيسي (UI Thread Watchdog)
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());
    private static long lastUiHeartbeat = System.currentTimeMillis();
    private static final AtomicLong maxUiFreezeDetected = new AtomicLong(0);

    public static void syncBrowserState(int nodes, int fps, long memory, int tasks) {
        browserState.domNodes = nodes;
        browserState.fps = fps;
        browserState.jsMemoryMB = memory;
        browserState.longTasks = tasks;
        browserState.lastUpdate = System.currentTimeMillis();

        browserState.fpsHistory.push(fps);
        browserState.domHistory.push(nodes);
        browserState.memoryHistory.push(memory);
    }

    private RoyalPanopticon() {}

    // =====================================================================
    // 1. ENGINE RECORD (سجلات كفاءة المحركات الفرعية)
    // =====================================================================
    private static class EngineRecord {
        final String name;
        volatile long lastPulse = System.currentTimeMillis();
        final AtomicLong activeTimeMs = new AtomicLong(0);
        final AtomicLong totalOps = new AtomicLong(0);
        final AtomicLong failedOps = new AtomicLong(0);
        final AtomicLong totalLatency = new AtomicLong(0);
        final AtomicLong memoryPeak = new AtomicLong(0);

        EngineRecord(String name) { this.name = name; }

        double getHealthRate() {
            long total = totalOps.get();
            return total == 0 ? 100.0 : Math.max(0.0, 100.0 - ((failedOps.get() * 100.0) / total));
        }

        double getEfficiencyRate() {
            long total = totalOps.get();
            if (total == 0) return 100.0;
            double avgLatency = (double) totalLatency.get() / total;
            double score = 100.0 - ((avgLatency / 16.0) * 10.0);
            return Math.max(0.0, Math.min(100.0, score));
        }
    }

    private static class Anomaly {
        final String engine, severity, deduction, recommendation;
        final List<String> evidence = new ArrayList<>();

        Anomaly(String e, String s, String d, String r) {
            engine = e; severity = s; deduction = d; recommendation = r;
        }
    }

    private static final List<Anomaly> activeAnomalies = Collections.synchronizedList(new ArrayList<>());
    private static final long creationTime = System.currentTimeMillis();

    // =====================================================================
    // 2. THE CHRONO-SENSORS API (تتبع النقرات والتأخير)
    // =====================================================================
    
    // يُستدعى فوراً عند نقر المستخدم على زر أو رابط في التطبيق
    public static void recordUserClick(String destinationUrl) {
        synchronized (currentNav) {
            currentNav.url = destinationUrl;
            currentNav.clickTimestamp = System.currentTimeMillis();
            currentNav.requestSentTimestamp = 0;
            currentNav.firstByteTimestamp = 0;
            currentNav.domInteractiveTimestamp = 0;
            currentNav.domCompleteTimestamp = 0;
            currentNav.uiThreadBlockMs = 0;
        }
        pulse("UserClick");
    }

    // يُستدعى عندما يبدأ محرك الشبكة بإرسال الطلب للسيرفر
    public static void recordRequestSent() {
        synchronized (currentNav) {
            if (currentNav.clickTimestamp > 0) {
                currentNav.requestSentTimestamp = System.currentTimeMillis();
            }
        }
    }

    // يُستدعى عندما يستقبل التطبيق أول بايت (Response Headers) من السيرفر
    public static void recordFirstByteReceived() {
        synchronized (currentNav) {
            if (currentNav.requestSentTimestamp > 0) {
                currentNav.firstByteTimestamp = System.currentTimeMillis();
            }
        }
    }

    // يُستدعى عندما تصبح عناصر الصفحة تفاعلية (JS Ready)
    public static void recordDomInteractive() {
        synchronized (currentNav) {
            if (currentNav.clickTimestamp > 0) {
                currentNav.domInteractiveTimestamp = System.currentTimeMillis();
            }
        }
    }

    // يُستدعى عند اكتمال رندر الصفحة بالكامل واختفاء لودر التحميل
    public static void recordNavigationComplete() {
        synchronized (currentNav) {
            if (currentNav.clickTimestamp > 0) {
                currentNav.domCompleteTimestamp = System.currentTimeMillis();
                // طباعة تشخيص فوري بمجرد اكتمال العملية تسهيلاً للقراءة
                printInstantTransitionAnalysis();
            }
        }
    }

    public static void registerDependency(String parentEngine, String childEngine) {
        dependencies.computeIfAbsent(parentEngine, k -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(childEngine);
    }

    public static void pulse(String engineName) {
        getRecord(engineName).lastPulse = System.currentTimeMillis();
    }

    public static void recordExecution(String engineName, long latencyMs, boolean isSuccess, long memoryBytes) {
        EngineRecord r = getRecord(engineName);
        r.totalOps.incrementAndGet();
        r.activeTimeMs.addAndGet(latencyMs);
        r.totalLatency.addAndGet(latencyMs);
        if (!isSuccess) r.failedOps.incrementAndGet();
        if (memoryBytes > r.memoryPeak.get()) r.memoryPeak.set(memoryBytes);
    }

    private static EngineRecord getRecord(String name) {
        return engineRecords.computeIfAbsent(name, EngineRecord::new);
    }

    // =====================================================================
    // 3. THE DEDUCTIVE AI BRAIN & FREEZE MONITOR
    // =====================================================================
    public static synchronized void startAwareness() {
        if (analyzerThread != null && !analyzerThread.isShutdown()) return;
        
        Log.i(TAG, "👁️ Royal Panopticon Awakened. LogFox Search Keyword: [ROYAL_DIAGNOSTICS]");
        
        analyzerThread = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Panopticon-AI");
            t.setDaemon(true);
            return t;
        });
        
        // فحص دوري كل 5 ثوانٍ للبحث عن شلل في واجهة المستخدم (Thread Freezes)
        analyzerThread.scheduleAtFixedRate(RoyalPanopticon::monitorUiHealthAndAnalyze, 5, 5, TimeUnit.SECONDS);
    }
    
    public static synchronized void stopAwareness() {
        if (analyzerThread != null) {
            analyzerThread.shutdownNow();
            Log.i(TAG, "💤 Royal Panopticon entered Sleep Mode.");
        }
    }

    private static void monitorUiHealthAndAnalyze() {
        // 1. مراقبة تجمد واجهة المستخدم (UI Thread Sluggishness Detector)
        final long checkStart = System.currentTimeMillis();
        uiHandler.post(() -> {
            long now = System.currentTimeMillis();
            long executionLag = now - checkStart;
            lastUiHeartbeat = now;
            
            // إذا استغرق تشغيل دالة بسيطة جداً على الواجهة أكثر من 100ms، فهناك تجمد بسبب كود جافا أو متصفح ثقيل
            if (executionLag > 100) {
                maxUiFreezeDetected.set(Math.max(maxUiFreezeDetected.get(), executionLag));
                synchronized (currentNav) {
                    if (currentNav.domCompleteTimestamp == 0 && currentNav.clickTimestamp > 0) {
                        currentNav.uiThreadBlockMs += executionLag;
                    }
                }
                Log.e(TAG, "⚠️ [CRITICAL FREEZE DETECTED] UI Thread blocked for " + executionLag + " ms! User experienced lag.");
            }
        });

        // 2. تشخيص المسببات الذكي (AI Deduction)
        thinkAndDeduce();
    }

    private static void thinkAndDeduce() {
        activeAnomalies.clear();

        boolean fpsDropping = browserState.fpsHistory.isDropping();
        boolean domGrowing = browserState.domHistory.isIncreasing();
        boolean memoryGrowing = browserState.memoryHistory.isIncreasing();

        // تحليل تأخير التحميل عند النقر
        synchronized (currentNav) {
            if (currentNav.clickTimestamp > 0 && currentNav.domCompleteTimestamp > 0) {
                long totalTime = currentNav.domCompleteTimestamp - currentNav.clickTimestamp;
                long serverTime = currentNav.firstByteTimestamp - currentNav.requestSentTimestamp;
                long renderingTime = currentNav.domCompleteTimestamp - currentNav.firstByteTimestamp;

                if (totalTime > 3000) { // لو التحميل أخذ أكثر من 3 ثوانٍ
                    if (serverTime > (totalTime * 0.6)) {
                        Anomaly a = new Anomaly("Backend Server", "HIGH DELAY ⚠️", 
                            "عنق زجاجة في السيرفر (Slow Response / TTFB).", 
                            "السيرفر استغرق " + serverTime + "ms للاستجابة. افحص قواعد البيانات وقوة السيرفر أو تفعيل RoyalCache.");
                        a.evidence.add("Total load time: " + totalTime + " ms");
                        a.evidence.add("Server Wait Time (TTFB): " + serverTime + " ms");
                        activeAnomalies.add(a);
                    } else if (renderingTime > (totalTime * 0.6)) {
                        Anomaly a = new Anomaly("React/WebView DOM", "CRITICAL SLOWDOWN 🔴", 
                            "تأخير بسبب ثقل الواجهات (Heavy Client-side Render).", 
                            "السيرفر استجاب بسرعة، لكن المتصفح تجمّد في بناء شجرة الـ DOM وعناصر الـ JS.");
                        a.evidence.add("DOM Interactive Time: " + (currentNav.domCompleteTimestamp - currentNav.domInteractiveTimestamp) + " ms");
                        a.evidence.add("JS Memory: " + browserState.jsMemoryMB + " MB");
                        activeAnomalies.add(a);
                    }
                }
            }
        }

        // كشف تجمد خيط الأندرويد الرئيسي (Android Thread Freeze)
        if (maxUiFreezeDetected.get() > 250) {
            Anomaly a = new Anomaly("Android UI Thread", "ANR WARNING 💀", 
                "خيط المعالجة الرئيسي للأندرويد تجمّد بشكل متكرر (" + maxUiFreezeDetected.get() + "ms).", 
                "تجنب استدعاء دوال ثقيلة أو مزامنة محلية (Synchronous SQL/File IO) على الـ Main Thread.");
            activeAnomalies.add(a);
        }

        // كشف موت الاتصال وجفاف النبضات
        long now = System.currentTimeMillis();
        if (now - browserState.lastUpdate > 15000 && browserState.domNodes > 0) {
            Anomaly a = new Anomaly("Bridge Connection", "CRITICAL 💀", 
                "الجافاسكريبت توقف عن النبض تماماً (Death Silence).", 
                "إما أن صفحة الويب انهارت (Crash) أو أن الـ Main Thread دخل في حلقة تكرار لانهائية.");
            activeAnomalies.add(a);
        }
    }

    // =====================================================================
    // 4. REPORTING & LOGFOX OUTPUT
    // =====================================================================
    
    // يطبع تقرير فوري مخصص بمجرد انتقال العميل من صفحة لصفحة
    private static void printInstantTransitionAnalysis() {
        synchronized (currentNav) {
            long totalTime = currentNav.domCompleteTimestamp - currentNav.clickTimestamp;
            long transitDelay = currentNav.requestSentTimestamp - currentNav.clickTimestamp;
            long serverDelay = currentNav.firstByteTimestamp - currentNav.requestSentTimestamp;
            long renderDelay = currentNav.domCompleteTimestamp - currentNav.firstByteTimestamp;

            Log.i(TAG, "⚡─────────────────────────────────────────────────────────");
            Log.i(TAG, "⚡ [PAGE TRANSITION PROFILER REPORT]");
            Log.i(TAG, "⚡ Destination URL: " + currentNav.url);
            Log.i(TAG, "⚡ Total Duration: " + totalTime + " ms");
            Log.i(TAG, "⚡ ── [Breakdown] ──");
            Log.i(TAG, "⚡ 1. Bridge/Transit Lag (من النقرة لبداية الطلب) : " + (transitDelay > 0 ? transitDelay : 0) + " ms");
            Log.i(TAG, "⚡ 2. Server Response (الشبكة واستجابة السيرفر TTFB): " + (serverDelay > 0 ? serverDelay : 0) + " ms");
            Log.i(TAG, "⚡ 3. Browser Rendering (تحليل الجافا سكريبت والرندر): " + (renderDelay > 0 ? renderDelay : 0) + " ms");
            Log.i(TAG, "⚡ 4. UI Thread Frozen Time during this page load : " + currentNav.uiThreadBlockMs + " ms");
            
            // استنتاج فوري للمشكلة
            if (transitDelay > 500) {
                Log.e(TAG, "⚡ [CONCLUSION]: البطء سببه تأخر استجابة الجسر أو حدث الـ Click في JS!");
            } else if (serverDelay > 1500) {
                Log.e(TAG, "⚡ [CONCLUSION]: البطء سببه السيرفر / قاعدة البيانات! (Slow API / Server Response)");
            } else if (renderDelay > 1500) {
                Log.e(TAG, "⚡ [CONCLUSION]: البطء سببه المتصفح وجافاسكريبت الصفحة! (Heavy DOM / React render)");
            } else {
                Log.i(TAG, "⚡ [CONCLUSION]: عملية الانتقال ممتازة وبأداء سريع وصحي! (Healthy Transition)");
            }
            Log.i(TAG, "⚡─────────────────────────────────────────────────────────");
        }
    }

    // بناء وطباعة التقرير الشامل عند كتابة الكلمة في LogFox
    public static void printFullDiagnosticsReport() {
        Log.i(TAG, buildReport());
    }

    public static String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=========================================================\n");
        sb.append("👁️ ROYAL PANOPTICON DIAGNOSTIC REPORT\n");
        sb.append("=========================================================\n\n");
        
        sb.append("📈 CURRENT BROWSER ENGINE METRICS:\n");
        sb.append("   ├─ Live DOM Nodes  : ").append(browserState.domNodes).append("\n");
        sb.append("   ├─ UI Frame Rate   : ").append(browserState.fps).append(" FPS\n");
        sb.append("   ├─ JS Heap Memory  : ").append(browserState.jsMemoryMB).append(" MB\n");
        sb.append("   └─ Long Tasks (>50ms): ").append(browserState.longTasks).append("\n\n");

        sb.append("🚨 MAX DETECTED MAIN-THREAD FREEZE: ").append(maxUiFreezeDetected.get()).append(" ms\n\n");

        sb.append("🌲 DEPENDENCY TREE:\n");
        for (String parent : dependencies.keySet()) printNode(sb, parent, 0);

        sb.append("\n=========================================================\n");
        sb.append("🧠 DEDUCTIVE AI INSIGHTS & BOTTLENECK DETECTOR:\n");
        
        // جلب عينات الأخطاء الحية
        thinkAndDeduce();
        
        if (activeAnomalies.isEmpty()) {
            sb.append("✅ [SUCCESS] كل المحركات تعمل بكفاءة مثالية خالية من العوائق.\n");
        } else {
            List<Anomaly> copy = new ArrayList<>(activeAnomalies);
            for (Anomaly a : copy) {
                sb.append(a.severity).append(" [").append(a.engine).append("]\n");
                sb.append("   👉 المشكلة: ").append(a.deduction).append("\n");
                for (String ev : a.evidence) sb.append("   📊 دليل:   ").append(ev).append("\n");
                sb.append("   💡 الحل المقترح: ").append(a.recommendation).append("\n\n");
            }
        }
        sb.append("=========================================================\n");
        return sb.toString();
    }

    private static void printNode(StringBuilder sb, String engine, int depth) {
        EngineRecord r = engineRecords.get(engine);
        if (r == null) return;
        String indent = new String(new char[depth * 2]).replace('\0', ' ');
        String prefix = depth == 0 ? "👑 " : "├─ ";
        sb.append(indent).append(prefix).append(engine).append("\n");
        sb.append(indent).append("   ├─ Health Rate : ").append(String.format(Locale.US, "%.1f%%\n", r.getHealthRate()));
        sb.append(indent).append("   └─ Effic. Rate : ").append(String.format(Locale.US, "%.1f%%\n", r.getEfficiencyRate()));

        Set<String> children = dependencies.get(engine);
        if (children != null) {
            for (String child : children) printNode(sb, child, depth + 1);
        }
    }
}
