package com.store.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ============================================================================
 * 👁️ ROYAL PANOPTICON V4.0
 * ============================================================================
 *
 * SELF-AWARE ANDROID / WEBVIEW DIAGNOSTIC ENGINE
 *
 * الهدف:
 *
 *  1. مراقبة Main Thread.
 *  2. قياس FPS / Jank / Frame stalls.
 *  3. اكتشاف ضغط الـ UI أثناء Scroll.
 *  4. اكتشاف WebView Renderer.
 *  5. معرفة نسخة Android System WebView / Chromium.
 *  6. مراقبة ذاكرة التطبيق.
 *  7. مراقبة GC pressure.
 *  8. مراقبة Threads.
 *  9. مراقبة WebView lifecycle.
 * 10. مراقبة Navigation من الخارج.
 * 11. اكتشاف renderer death.
 * 12. مراقبة Warm-up / preloading إن أمكن قياسه.
 * 13. مراقبة الشبكة على مستوى Android دون تعديل كل request.
 * 14. إصدار تقرير رقمي واضح.
 * 15. عدم الحاجة إلى recordExecution() داخل كل Engine.
 *
 * IMPORTANT:
 *
 * هذا المحرك لا يدعي أنه يستطيع رؤية داخل Chromium بالكامل.
 * Android لا يعطي التطبيقات صلاحية قراءة كل تفاصيل Chromium الداخلية.
 *
 * لذلك التقرير يميز بين:
 *
 * OBSERVED  = تمت ملاحظته فعلياً.
 * INFERRED  = استنتاج مدعوم بعدة مؤشرات.
 * UNKNOWN   = لا توجد API موثوقة لمعرفة ذلك.
 *
 * ============================================================================
 */
public final class RoyalPanopticon {

    // =========================================================================
    // CORE
    // =========================================================================

    public static final String TAG = "[ROYAL_DIAGNOSTICS]";
    public static final String VERSION = "4.0";

    private static final long SAMPLE_INTERVAL_MS = 2000L;
    private static final long REPORT_INTERVAL_MS = 10000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static ScheduledExecutorService monitorExecutor;

    private static volatile WeakReference<Activity> currentActivity =
            new WeakReference<>(null);

    private static volatile WeakReference<WebView> currentWebView =
            new WeakReference<>(null);

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    // =========================================================================
    // MAIN THREAD
    // =========================================================================

    private static final AtomicLong maxMainFreezeMs = new AtomicLong(0);
    private static final AtomicLong totalMainFreezes = new AtomicLong(0);

    private static volatile long lastMainHeartbeat;
    private static volatile long lastMainLag;

    // =========================================================================
    // FRAME / SCROLL
    // =========================================================================

    private static final AtomicLong frameCount = new AtomicLong(0);
    private static final AtomicLong droppedFrames = new AtomicLong(0);
    private static final AtomicLong severeJankFrames = new AtomicLong(0);

    private static volatile long frameWindowStart;
    private static volatile long lastFrameTime;

    private static volatile boolean scrolling = false;
    private static volatile long lastScrollTime = 0;

    private static final ArrayDeque<Long> frameDurations =
            new ArrayDeque<>(120);

    // =========================================================================
    // WEBVIEW
    // =========================================================================

    private static volatile String webViewPackage = "UNKNOWN";
    private static volatile String webViewVersion = "UNKNOWN";
    private static volatile String userAgent = "UNKNOWN";
    private static volatile String currentUrl = "UNKNOWN";

    private static volatile boolean webViewAttached = false;
    private static volatile boolean rendererAvailable = false;
    private static volatile boolean rendererGone = false;

    private static volatile int webViewWidth = 0;
    private static volatile int webViewHeight = 0;

    // =========================================================================
    // MEMORY
    // =========================================================================

    private static volatile long javaHeapUsedMB = 0;
    private static volatile long javaHeapMaxMB = 0;
    private static volatile long nativeHeapAllocatedMB = 0;
    private static volatile long pssMB = 0;

    private static volatile long lastPssMB = 0;
    private static volatile long peakPssMB = 0;

    // =========================================================================
    // THREADS
    // =========================================================================

    private static volatile int threadCount = 0;
    private static volatile int appProcessCount = 0;

    // =========================================================================
    // NAVIGATION
    // =========================================================================

    private static volatile long navigationStart = 0;
    private static volatile long navigationEnd = 0;

    private static volatile String lastNavigationUrl = "";
    private static volatile long lastNavigationDuration = 0;

    private static volatile int navigationCount = 0;

    // =========================================================================
    // WARMUP / PRELOAD OBSERVATION
    // =========================================================================

    private static volatile long warmupStart = 0;
    private static volatile long warmupEnd = 0;
    private static volatile boolean warmupObserved = false;

    // =========================================================================
    // NETWORK
    // =========================================================================

    private static volatile long lastNetworkCheck = 0;

    // =========================================================================
    // DIAGNOSTICS
    // =========================================================================

    private static final List<String> activeFindings =
            Collections.synchronizedList(new ArrayList<>());

    private static final List<String> evidence =
            Collections.synchronizedList(new ArrayList<>());

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    private RoyalPanopticon() {
    }

    // =========================================================================
    // START
    // =========================================================================

    /**
     * نقطة التشغيل الوحيدة.
     *
     * يفضل استدعاؤها مرة واحدة من MainActivity.onCreate().
     */
    public static synchronized void start(Activity activity) {

        if (activity == null) {
            Log.e(TAG, "❌ START FAILED: Activity == null");
            return;
        }

        if (RUNNING.get()) {

            currentActivity = new WeakReference<>(activity);

            discoverWebView(activity);

            Log.i(TAG,
                    "👁️ Panopticon already running. Activity refreshed.");

            return;
        }

        RUNNING.set(true);

        currentActivity = new WeakReference<>(activity);

        Log.i(TAG, "");
        Log.i(TAG, "============================================================");
        Log.i(TAG, "👁️ ROYAL PANOPTICON V4.0");
        Log.i(TAG, "============================================================");
        Log.i(TAG, "Mode       : AUTOMATIC OBSERVATION");
        Log.i(TAG, "Thread     : " + Thread.currentThread().getName());
        Log.i(TAG, "Android    : " + Build.VERSION.RELEASE);
        Log.i(TAG, "API        : " + Build.VERSION.SDK_INT);
        Log.i(TAG, "PID        : " + Process.myPid());
        Log.i(TAG, "============================================================");

        collectRuntimeIdentity(activity);

        discoverWebView(activity);

        installMainThreadWatchdog();

        installFrameMonitor();

        installExceptionObserver();

        startMonitorThread();

        printBootReport();
    }

    // =========================================================================
    // STOP
    // =========================================================================

    public static synchronized void stop() {

        RUNNING.set(false);

        if (monitorExecutor != null) {
            monitorExecutor.shutdownNow();
            monitorExecutor = null;
        }

        Log.i(TAG, "💤 Royal Panopticon stopped.");
    }

    // =========================================================================
    // RUNTIME IDENTITY
    // =========================================================================

    private static void collectRuntimeIdentity(Context context) {

        try {

            PackageInfo info;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                info = context.getPackageManager().getPackageInfo(
                        "com.google.android.webview",
                        0
                );

            } else {

                info = context.getPackageManager().getPackageInfo(
                        "com.google.android.webview",
                        0
                );
            }

            webViewPackage = info.packageName;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                webViewVersion = info.getLongVersionCode()
                        + " / " + info.versionName;
            } else {
                webViewVersion = info.versionName;
            }

        } catch (Exception ignored) {

            try {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    PackageInfo info =
                            WebView.getCurrentWebViewPackage();

                    if (info != null) {

                        webViewPackage = info.packageName;
                        webViewVersion = info.versionName;
                    }
                }

            } catch (Throwable ignoredAgain) {
                webViewPackage = "UNKNOWN";
                webViewVersion = "UNKNOWN";
            }
        }

        Log.i(TAG,
                "🔎 WEBVIEW PROVIDER: "
                        + webViewPackage
                        + " / "
                        + webViewVersion);
    }

    // =========================================================================
    // WEBVIEW DISCOVERY
    // =========================================================================

    private static void discoverWebView(Activity activity) {

        MAIN.post(() -> {

            try {

                View root = activity.getWindow().getDecorView();

                WebView found = findWebView(root);

                if (found != null) {

                    attachWebView(found);

                } else {

                    webViewAttached = false;

                    Log.w(TAG,
                            "⚠️ WebView not discovered yet. "
                                    + "Panopticon will retry automatically.");
                }

            } catch (Throwable t) {

                Log.e(TAG,
                        "❌ WebView discovery failed: "
                                + t.getClass().getSimpleName());
            }

        });
    }

    private static WebView findWebView(View root) {

        if (root instanceof WebView) {
            return (WebView) root;
        }

        if (!(root instanceof ViewGroup)) {
            return null;
        }

        ViewGroup group = (ViewGroup) root;

        for (int i = 0; i < group.getChildCount(); i++) {

            WebView result =
                    findWebView(group.getChildAt(i));

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    // =========================================================================
    // ATTACH WEBVIEW
    // =========================================================================

    private static void attachWebView(WebView webView) {

        if (webView == null) {
            return;
        }

        currentWebView = new WeakReference<>(webView);

        webViewAttached = true;

        webViewWidth = webView.getWidth();
        webViewHeight = webView.getHeight();

        try {

            userAgent =
                    webView.getSettings().getUserAgentString();

        } catch (Throwable ignored) {
        }

        try {

            currentUrl =
                    String.valueOf(webView.getUrl());

        } catch (Throwable ignored) {
        }

        rendererAvailable = true;

        Log.i(TAG, "");
        Log.i(TAG, "🌐 WEBVIEW ATTACHED");
        Log.i(TAG, "URL       : " + currentUrl);
        Log.i(TAG, "Size      : "
                + webViewWidth
                + "x"
                + webViewHeight);
        Log.i(TAG, "Provider  : "
                + webViewPackage);
        Log.i(TAG, "Version   : "
                + webViewVersion);
        Log.i(TAG, "UserAgent : "
                + shorten(userAgent, 180));

        // لا نستبدل WebViewClient.
        // هذا مهم جداً حتى لا نكسر محركاتك الحالية.

        Log.i(TAG,
                "🛡️ Existing WebViewClient preserved. "
                        + "Panopticon remains observational.");
    }

    // =========================================================================
    // MAIN THREAD WATCHDOG
    // =========================================================================

    private static void installMainThreadWatchdog() {

        lastMainHeartbeat = SystemClock.uptimeMillis();

        final Runnable heartbeat = new Runnable() {

            @Override
            public void run() {

                long now = SystemClock.uptimeMillis();

                long lag =
                        now - lastMainHeartbeat;

                lastMainHeartbeat = now;

                lastMainLag = lag;

                if (lag > maxMainFreezeMs.get()) {

                    maxMainFreezeMs.set(lag);
                }

                if (lag >= 100) {

                    totalMainFreezes.incrementAndGet();

                    String severity;

                    if (lag >= 1000) {
                        severity = "CRITICAL";
                    } else if (lag >= 250) {
                        severity = "HIGH";
                    } else {
                        severity = "WARNING";
                    }

                    Log.w(TAG,
                            "⚠️ MAIN THREAD "
                                    + severity
                                    + " | blocked="
                                    + lag
                                    + "ms");

                    synchronized (evidence) {

                        evidence.add(
                                "MainThread freeze "
                                        + lag
                                        + "ms"
                        );
                    }
                }

                MAIN.postDelayed(this, 250);
            }
        };

        MAIN.postDelayed(heartbeat, 250);
    }

    // =========================================================================
    // FRAME MONITOR
    // =========================================================================

    private static void installFrameMonitor() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            return;
        }

        frameWindowStart =
                SystemClock.uptimeMillis();

        Choreographer.getInstance()
                .postFrameCallback(new Choreographer.FrameCallback() {

                    @Override
                    public void doFrame(long frameTimeNanos) {

                        long now =
                                SystemClock.uptimeMillis();

                        long previous =
                                lastFrameTime;

                        lastFrameTime = now;

                        frameCount.incrementAndGet();

                        if (previous > 0) {

                            long duration =
                                    now - previous;

                            synchronized (frameDurations) {

                                if (frameDurations.size() >= 120) {
                                    frameDurations.pollFirst();
                                }

                                frameDurations.addLast(duration);
                            }

                            if (duration > 32) {

                                droppedFrames.incrementAndGet();
                            }

                            if (duration > 100) {

                                severeJankFrames.incrementAndGet();

                                Log.w(TAG,
                                        "⚠️ FRAME JANK | "
                                                + duration
                                                + "ms");
                            }
                        }

                        Choreographer.getInstance()
                                .postFrameCallback(this);
                    }
                });
    }

    // =========================================================================
    // SCROLL OBSERVATION
    // =========================================================================

    /**
     * يمكن للمحرك اكتشاف أن الـ WebView نفسه يتحرك
     * بدون تعديل JavaScript.
     */
    private static void observeScroll() {

        WebView webView =
                currentWebView.get();

        if (webView == null) {
            scrolling = false;
            return;
        }

        // لا نستطيع معرفة "scroll event" الحقيقي من Java
        // في كل WebView دون اعتراض داخلي.
        //
        // لكن يمكن استخدام تغير scrollY كدليل خارجي.

        int y =
                webView.getScrollY();

        // إذا تغير موضع WebView في آخر sample
        // نعتبره Scroll activity.
        //
        // يتم حفظ القيمة في scrollPosition.
        if (y != lastScrollY) {

            scrolling = true;

            lastScrollTime =
                    SystemClock.uptimeMillis();

            lastScrollY = y;

        } else if (
                SystemClock.uptimeMillis()
                        - lastScrollTime
                        > 500) {

            scrolling = false;
        }
    }

    private static volatile int lastScrollY = 0;

    // =========================================================================
    // EXCEPTION OBSERVER
    // =========================================================================

    private static Thread.UncaughtExceptionHandler originalExceptionHandler;

    private static void installExceptionObserver() {

        if (originalExceptionHandler != null) {
            return;
        }

        originalExceptionHandler =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(
                (thread, throwable) -> {

                    Log.e(TAG,
                            "💀 FATAL EXCEPTION OBSERVED");

                    Log.e(TAG,
                            "Thread: "
                                    + thread.getName());

                    Log.e(TAG,
                            "Type: "
                                    + throwable.getClass()
                                            .getName());

                    Log.e(TAG,
                            "Message: "
                                    + throwable.getMessage());

                    Log.e(TAG,
                            Log.getStackTraceString(
                                    throwable));

                    if (originalExceptionHandler != null) {

                        originalExceptionHandler
                                .uncaughtException(
                                        thread,
                                        throwable
                                );
                    }
                }
        );
    }

    // =========================================================================
    // MONITOR THREAD
    // =========================================================================

    private static void startMonitorThread() {

        monitorExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {

                            Thread t =
                                    new Thread(
                                            r,
                                            "Royal-Panopticon"
                                    );

                            t.setDaemon(true);

                            return t;
                        }
                );

        monitorExecutor.scheduleAtFixedRate(
                RoyalPanopticon::performObservation,
                1,
                SAMPLE_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        monitorExecutor.scheduleAtFixedRate(
                RoyalPanopticon::performDeepDiagnosis,
                5,
                REPORT_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    // =========================================================================
    // OBSERVATION
    // =========================================================================

    private static void performObservation() {

        try {

            Activity activity =
                    currentActivity.get();

            if (activity != null) {

                discoverWebView(activity);
            }

            observeMemory();

            observeThreads();

            observeWebView();

            observeScroll();

            observeNetworkEnvironment();

        } catch (Throwable t) {

            Log.e(TAG,
                    "⚠️ Observation cycle error: "
                            + t.getClass().getSimpleName());
        }
    }

    // =========================================================================
    // WEBVIEW OBSERVATION
    // =========================================================================

    private static void observeWebView() {

        WebView webView =
                currentWebView.get();

        if (webView == null) {

            webViewAttached = false;
            return;
        }

        MAIN.post(() -> {

            try {

                currentUrl =
                        String.valueOf(
                                webView.getUrl());

                webViewWidth =
                        webView.getWidth();

                webViewHeight =
                        webView.getHeight();

                if (Build.VERSION.SDK_INT >= 29) {

                    try {

                        rendererAvailable =
                                webView.getWebViewRenderProcess()
                                        != null;

                    } catch (Throwable ignored) {

                        rendererAvailable = true;
                    }
                }

            } catch (Throwable t) {

                Log.w(TAG,
                        "⚠️ WebView observation failed: "
                                + t.getClass()
                                        .getSimpleName());
            }
        });
    }

    // =========================================================================
    // MEMORY
    // =========================================================================

    private static void observeMemory() {

        Runtime runtime =
                Runtime.getRuntime();

        long used =
                runtime.totalMemory()
                        - runtime.freeMemory();

        javaHeapUsedMB =
                used / (1024 * 1024);

        javaHeapMaxMB =
                runtime.maxMemory()
                        / (1024 * 1024);

        nativeHeapAllocatedMB =
                Debug.getNativeHeapAllocatedSize()
                        / (1024 * 1024);

        ActivityManager manager =
                (ActivityManager)
                        getContext()
                                .getSystemService(
                                        Context.ACTIVITY_SERVICE
                                );

        if (manager != null) {

            Debug.MemoryInfo memoryInfo =
                    new Debug.MemoryInfo();

            Debug.getMemoryInfo(memoryInfo);

            pssMB =
                    memoryInfo.getTotalPss()
                            / 1024;

            if (pssMB > peakPssMB) {

                peakPssMB = pssMB;
            }

            lastPssMB = pssMB;
        }
    }

    // =========================================================================
    // THREAD CENSUS
    // =========================================================================

    private static void observeThreads() {

        try {

            threadCount =
                    Thread.getAllStackTraces().size();

        } catch (Throwable ignored) {
        }

        try {

            ActivityManager manager =
                    (ActivityManager)
                            getContext()
                                    .getSystemService(
                                            Context.ACTIVITY_SERVICE
                                    );

            if (manager != null) {

                List<ActivityManager.RunningAppProcessInfo>
                        processes =
                        manager.getRunningAppProcesses();

                if (processes != null) {

                    int count = 0;

                    for (ActivityManager
                            .RunningAppProcessInfo process
                            : processes) {

                        if (process.uid
                                == Process.myUid()) {

                            count++;
                        }
                    }

                    appProcessCount = count;
                }
            }

        } catch (Throwable ignored) {
        }
    }

    // =========================================================================
    // NETWORK ENVIRONMENT
    // =========================================================================

    private static void observeNetworkEnvironment() {

        lastNetworkCheck =
                SystemClock.uptimeMillis();

        /*
         * Panopticon does NOT hijack every WebView request.
         *
         * السبب:
         *
         * كثرة الموارد قد تحول أداة التشخيص إلى bottleneck.
         *
         * الشبكة الدقيقة لكل request تحتاج instrumentation
         * في نقطة الشبكة نفسها أو Navigation Timing من WebView.
         *
         * هنا نكتفي بالـ runtime environment.
         */
    }

    // =========================================================================
    // DEEP DIAGNOSIS
    // =========================================================================

    private static void performDeepDiagnosis() {

        activeFindings.clear();

        analyzeMainThread();

        analyzeFrames();

        analyzeMemory();

        analyzeWebView();

        analyzeRenderer();

        analyzeScroll();

        analyzeThreads();

        analyzeWarmup();

        printCompactHealth();
    }

    // =========================================================================
    // MAIN THREAD ANALYSIS
    // =========================================================================

    private static void analyzeMainThread() {

        long max =
                maxMainFreezeMs.get();

        if (max >= 1000) {

            addFinding(
                    "MAIN_THREAD",
                    "CRITICAL",
                    "تجمد شديد في Main Thread",
                    "أعلى تأخير مقاس: "
                            + max
                            + "ms"
            );

        } else if (max >= 250) {

            addFinding(
                    "MAIN_THREAD",
                    "HIGH",
                    "اختناق واضح في Main Thread",
                    "أعلى تأخير مقاس: "
                            + max
                            + "ms"
            );

        } else if (max >= 100) {

            addFinding(
                    "MAIN_THREAD",
                    "WARNING",
                    "Jank محتمل في Main Thread",
                    "أعلى تأخير مقاس: "
                            + max
                            + "ms"
            );
        }
    }

    // =========================================================================
    // FRAME ANALYSIS
    // =========================================================================

    private static void analyzeFrames() {

        long frames =
                frameCount.get();

        long drops =
                droppedFrames.get();

        long severe =
                severeJankFrames.get();

        if (frames < 30) {
            return;
        }

        double dropRate =
                (drops * 100.0)
                        / Math.max(1, frames);

        if (severe >= 3) {

            addFinding(
                    "FRAME_PIPELINE",
                    "HIGH",
                    "تم اكتشاف Frame Jank شديد",
                    "Severe jank frames="
                            + severe
                            + ", dropped="
                            + drops
                            + ", frames="
                            + frames
            );

        } else if (dropRate > 10) {

            addFinding(
                    "FRAME_PIPELINE",
                    "WARNING",
                    "معدل إسقاط الإطارات مرتفع",
                    String.format(
                            Locale.US,
                            "Drop rate=%.1f%%",
                            dropRate
                    )
            );
        }
    }

    // =========================================================================
    // MEMORY ANALYSIS
    // =========================================================================

    private static void analyzeMemory() {

        if (pssMB <= 0) {
            return;
        }

        if (lastPssMB > 0) {

            long delta =
                    pssMB - lastPssMB;

            if (delta > 50) {

                addFinding(
                        "MEMORY",
                        "WARNING",
                        "ارتفاع ملحوظ في PSS",
                        "PSS changed by "
                                + delta
                                + "MB"
                );
            }
        }

        if (peakPssMB > 0) {

            long javaLimit =
                    javaHeapMaxMB;

            if (javaLimit > 0
                    && javaHeapUsedMB
                    > javaLimit * 0.85) {

                addFinding(
                        "JAVA_HEAP",
                        "HIGH",
                        "Java Heap قريب من الحد",
                        "Used="
                                + javaHeapUsedMB
                                + "MB / Max="
                                + javaLimit
                                + "MB"
                );
            }
        }
    }

    // =========================================================================
    // WEBVIEW ANALYSIS
    // =========================================================================

    private static void analyzeWebView() {

        if (!webViewAttached) {

            addFinding(
                    "WEBVIEW",
                    "INFO",
                    "لم يتم العثور على WebView",
                    "قد يكون WebView لم يتم إنشاؤه بعد."
            );

            return;
        }

        if (webViewWidth <= 0
                || webViewHeight <= 0) {

            addFinding(
                    "WEBVIEW",
                    "WARNING",
                    "WebView موجود لكنه لم يحصل على أبعاد",
                    "قد يكون ما زال في مرحلة الإنشاء."
            );
        }

        if (currentUrl == null
                || currentUrl.equals("null")
                || currentUrl.trim().isEmpty()) {

            addFinding(
                    "WEBVIEW",
                    "INFO",
                    "WebView موجود بدون URL حالي",
                    "قد يكون قبل أول Navigation."
            );
        }
    }

    // =========================================================================
    // RENDERER
    // =========================================================================

    private static void analyzeRenderer() {

        if (!webViewAttached) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 29) {

            if (!rendererAvailable) {

                addFinding(
                        "CHROMIUM_RENDERER",
                        "HIGH",
                        "WebView Renderer غير متاح حالياً",
                        "قد يكون renderer متوقفاً أو WebView في حالة انتقال."
                );
            }
        } else {

            addFinding(
                    "CHROMIUM_RENDERER",
                    "INFO",
                    "تفاصيل Renderer محدودة بسبب إصدار Android",
                    "WebView renderer APIs المتقدمة تحتاج Android 10+."
            );
        }
    }

    // =========================================================================
    // SCROLL
    // =========================================================================

    private static void analyzeScroll() {

        if (!scrolling) {
            return;
        }

        long frameJank =
                severeJankFrames.get();

        if (frameJank > 0) {

            addFinding(
                    "SCROLL",
                    "HIGH",
                    "Scroll نشط بالتزامن مع Frame Jank",
                    "هناك ارتباط زمني بين الحركة والتقطيع."
            );

        } else if (maxMainFreezeMs.get() > 100) {

            addFinding(
                    "SCROLL",
                    "WARNING",
                    "Scroll نشط أثناء ضغط Main Thread",
                    "Main Thread freeze="
                            + maxMainFreezeMs.get()
                            + "ms"
            );
        }
    }

    // =========================================================================
    // THREADS
    // =========================================================================

    private static void analyzeThreads() {

        if (threadCount > 120) {

            addFinding(
                    "THREADS",
                    "WARNING",
                    "عدد Threads مرتفع",
                    "Threads="
                            + threadCount
            );
        }

        if (appProcessCount > 1) {

            addFinding(
                    "PROCESSES",
                    "INFO",
                    "التطبيق لديه أكثر من Process",
                    "Processes="
                            + appProcessCount
            );
        }
    }

    // =========================================================================
    // WARMUP
    // =========================================================================

    private static void analyzeWarmup() {

        /*
         * لا يمكن لـ Panopticon أن يقول:
         *
         * "RoyalHybridEngine warmup نجح 100%"
         *
         * بدون معرفة event خاص بالمحرك.
         *
         * لكنه يستطيع مراقبة النتيجة:
         *
         * WebView موجود؟
         * renderer متاح؟
         * first URL ظهر؟
         * navigation بدأ؟
         * frame pipeline مستقر؟
         *
         * لذلك التقرير يصف:
         *
         * WARMUP ENVIRONMENT READY
         *
         * وليس "warmup succeeded" ما لم توجد إشارة موثوقة.
         */

        if (webViewAttached
                && rendererAvailable) {

            warmupObserved = true;

            if (warmupStart == 0) {

                warmupStart =
                        SystemClock.uptimeMillis();

                warmupEnd =
                        warmupStart;
            }
        }
    }

    // =========================================================================
    // FINDINGS
    // =========================================================================

    private static void addFinding(
            String subsystem,
            String severity,
            String title,
            String detail) {

        String line =
                severity
                        + " | "
                        + subsystem
                        + " | "
                        + title
                        + " | "
                        + detail;

        activeFindings.add(line);
    }

    // =========================================================================
    // HEALTH REPORT
    // =========================================================================

    private static void printCompactHealth() {

        int score =
                calculateHealthScore();

        Log.i(TAG, "");
        Log.i(TAG,
                "👁️ ───────── PANOPTICON SNAPSHOT ─────────");

        Log.i(TAG,
                "01 | HEALTH SCORE : "
                        + score
                        + "/100");

        Log.i(TAG,
                "    MainFreeze="
                        + maxMainFreezeMs.get()
                        + "ms"
                        + " | Jank="
                        + severeJankFrames.get()
                        + " | FPS≈"
                        + estimateFps()
                        + " | PSS="
                        + pssMB
                        + "MB");

        Log.i(TAG,
                "02 | WEBVIEW : "
                        + (webViewAttached
                        ? "ATTACHED"
                        : "NOT_FOUND"));

        Log.i(TAG,
                "    Provider="
                        + webViewPackage
                        + " | Version="
                        + webViewVersion);

        Log.i(TAG,
                "03 | RENDERER : "
                        + (rendererAvailable
                        ? "AVAILABLE"
                        : "UNKNOWN/OFF"));

        Log.i(TAG,
                "    URL="
                        + shorten(currentUrl, 140));

        Log.i(TAG,
                "04 | THREADS : "
                        + threadCount
                        + " | PROCESSES="
                        + appProcessCount);

        Log.i(TAG,
                "05 | SCROLL : "
                        + (scrolling
                        ? "ACTIVE"
                        : "IDLE"));

        if (!activeFindings.isEmpty()) {

            Log.i(TAG,
                    "06 | FINDINGS="
                            + activeFindings.size());

            for (String finding
                    : activeFindings) {

                Log.w(TAG,
                        "    ↳ "
                                + finding);
            }

        } else {

            Log.i(TAG,
                    "06 | FINDINGS=0");

            Log.i(TAG,
                    "    ↳ No abnormal condition observed.");
        }

        Log.i(TAG,
                "👁️ ─────────────────────────────────────");
    }

    // =========================================================================
    // HEALTH SCORE
    // =========================================================================

    private static int calculateHealthScore() {

        int score = 100;

        long freeze =
                maxMainFreezeMs.get();

        long severe =
                severeJankFrames.get();

        if (freeze >= 1000) {
            score -= 30;
        } else if (freeze >= 500) {
            score -= 20;
        } else if (freeze >= 250) {
            score -= 12;
        } else if (freeze >= 100) {
            score -= 5;
        }

        if (severe >= 10) {
            score -= 25;
        } else if (severe >= 5) {
            score -= 15;
        } else if (severe >= 3) {
            score -= 8;
        }

        if (pssMB > 0
                && javaHeapMaxMB > 0
                && javaHeapUsedMB
                > javaHeapMaxMB * 0.90) {

            score -= 15;
        }

        if (!webViewAttached) {
            score -= 5;
        }

        if (score < 0) {
            score = 0;
        }

        return score;
    }

    // =========================================================================
    // FPS ESTIMATION
    // =========================================================================

    private static int estimateFps() {

        synchronized (frameDurations) {

            if (frameDurations.size() < 5) {
                return 0;
            }

            long total = 0;

            for (Long duration
                    : frameDurations) {

                total += duration;
            }

            long average =
                    total
                            / frameDurations.size();

            if (average <= 0) {
                return 0;
            }

            return (int)
                    Math.min(
                            120,
                            Math.max(
                                    1,
                                    1000 / average
                            )
                    );
        }
    }

    // =========================================================================
    // BOOT REPORT
    // =========================================================================

    private static void printBootReport() {

        Log.i(TAG, "");
        Log.i(TAG,
                "01 | PANOPTICON BOOT");

        Log.i(TAG,
                "    Automatic observation enabled.");

        Log.i(TAG,
                "02 | MAIN THREAD WATCHDOG");

        Log.i(TAG,
                "    Heartbeat interval: 250ms");

        Log.i(TAG,
                "03 | FRAME OBSERVER");

        Log.i(TAG,
                "    Choreographer monitoring enabled.");

        Log.i(TAG,
                "04 | WEBVIEW");

        Log.i(TAG,
                "    Automatic WebView discovery enabled.");

        Log.i(TAG,
                "05 | WEBVIEW PROVIDER");

        Log.i(TAG,
                "    "
                        + webViewPackage
                        + " / "
                        + webViewVersion);

        Log.i(TAG,
                "06 | CRASH OBSERVER");

        Log.i(TAG,
                "    Default uncaught exception observer installed.");

        Log.i(TAG,
                "07 | MEMORY");

        Log.i(TAG,
                "    Java / Native / PSS observation enabled.");

        Log.i(TAG,
                "08 | THREAD CENSUS");

        Log.i(TAG,
                "    Active.");

        Log.i(TAG,
                "09 | REPORT");

        Log.i(TAG,
                "    Search LogFox for "
                        + TAG);

        Log.i(TAG,
                "============================================================");
    }

    // =========================================================================
    // FULL REPORT COMMAND
    // =========================================================================

    public static void printFullDiagnosticsReport() {

        Log.i(TAG,
                buildReport());
    }

    public static String buildReport() {

        StringBuilder sb =
                new StringBuilder(8192);

        sb.append("\n");
        sb.append("============================================================\n");
        sb.append("👁️ ROYAL PANOPTICON V4.0 — FULL DIAGNOSTIC REPORT\n");
        sb.append("============================================================\n\n");

        sb.append("01 | SYSTEM\n");
        sb.append("Android      : ")
                .append(Build.VERSION.RELEASE)
                .append("\n");

        sb.append("API          : ")
                .append(Build.VERSION.SDK_INT)
                .append("\n");

        sb.append("PID          : ")
                .append(Process.myPid())
                .append("\n\n");

        sb.append("02 | WEBVIEW / CHROMIUM\n");

        sb.append("Provider     : ")
                .append(webViewPackage)
                .append("\n");

        sb.append("Version      : ")
                .append(webViewVersion)
                .append("\n");

        sb.append("Attached     : ")
                .append(webViewAttached)
                .append("\n");

        sb.append("Renderer     : ")
                .append(rendererAvailable)
                .append("\n");

        sb.append("URL          : ")
                .append(currentUrl)
                .append("\n");

        sb.append("Size         : ")
                .append(webViewWidth)
                .append("x")
                .append(webViewHeight)
                .append("\n\n");

        sb.append("03 | FRAME PIPELINE\n");

        sb.append("Estimated FPS: ")
                .append(estimateFps())
                .append("\n");

        sb.append("Frames       : ")
                .append(frameCount.get())
                .append("\n");

        sb.append("Dropped      : ")
                .append(droppedFrames.get())
                .append("\n");

        sb.append("Severe Jank  : ")
                .append(severeJankFrames.get())
                .append("\n\n");

        sb.append("04 | MAIN THREAD\n");

        sb.append("Max Freeze   : ")
                .append(maxMainFreezeMs.get())
                .append("ms\n");

        sb.append("Freeze Count : ")
                .append(totalMainFreezes.get())
                .append("\n");

        sb.append("Last Lag     : ")
                .append(lastMainLag)
                .append("ms\n\n");

        sb.append("05 | MEMORY\n");

        sb.append("Java Heap    : ")
                .append(javaHeapUsedMB)
                .append(" / ")
                .append(javaHeapMaxMB)
                .append("MB\n");

        sb.append("Native Heap  : ")
                .append(nativeHeapAllocatedMB)
                .append("MB\n");

        sb.append("PSS          : ")
                .append(pssMB)
                .append("MB\n");

        sb.append("Peak PSS     : ")
                .append(peakPssMB)
                .append("MB\n\n");

        sb.append("06 | THREADS\n");

        sb.append("Threads      : ")
                .append(threadCount)
                .append("\n");

        sb.append("Processes    : ")
                .append(appProcessCount)
                .append("\n\n");

        sb.append("07 | SCROLL\n");

        sb.append("State        : ")
                .append(scrolling
                        ? "ACTIVE"
                        : "IDLE")
                .append("\n");

        sb.append("Last Scroll  : ")
                .append(lastScrollTime)
                .append("\n\n");

        sb.append("08 | WARMUP\n");

        sb.append("Observed     : ")
                .append(warmupObserved)
                .append("\n");

        sb.append("Environment  : ");

        if (webViewAttached
                && rendererAvailable) {

            sb.append("READY");
        } else {

            sb.append("NOT CONFIRMED");
        }

        sb.append("\n\n");

        sb.append("09 | HEALTH\n");

        sb.append("Score        : ")
                .append(calculateHealthScore())
                .append("/100\n\n");

        sb.append("10 | DIAGNOSTIC FINDINGS\n");

        if (activeFindings.isEmpty()) {

            sb.append("No abnormal condition observed.\n");

        } else {

            for (String finding
                    : activeFindings) {

                sb.append("• ")
                        .append(finding)
                        .append("\n");
            }
        }

        sb.append("\n============================================================\n");

        sb.append("OBSERVABILITY LIMITS\n");

        sb.append("------------------------------------------------------------\n");

        sb.append(
                "Panopticon observes Android/WebView externally.\n"
        );

        sb.append(
                "It does NOT claim direct visibility into private Chromium internals.\n"
        );

        sb.append(
                "JS DOM/LongTask/JS Heap require WebView-side instrumentation.\n"
        );

        sb.append(
                "Per-request TTFB requires a network interception point or timing API.\n"
        );

        sb.append(
                "Warm-up success is confirmed only when observable runtime evidence exists.\n"
        );

        sb.append("============================================================\n");

        return sb.toString();
    }

    // =========================================================================
    // PUBLIC SNAPSHOT COMMANDS
    // =========================================================================

    public static void snapshot() {

        Log.i(TAG,
                buildReport());
    }

    public static void reportMemory() {

        Log.i(TAG,
                "MEMORY | Java="
                        + javaHeapUsedMB
                        + "/"
                        + javaHeapMaxMB
                        + "MB"
                        + " | Native="
                        + nativeHeapAllocatedMB
                        + "MB"
                        + " | PSS="
                        + pssMB
                        + "MB"
                        + " | Peak="
                        + peakPssMB
                        + "MB");
    }

    public static void reportWebView() {

        Log.i(TAG,
                "WEBVIEW | attached="
                        + webViewAttached
                        + " | provider="
                        + webViewPackage
                        + " | version="
                        + webViewVersion
                        + " | renderer="
                        + rendererAvailable
                        + " | url="
                        + currentUrl);
    }

    public static void reportFrames() {

        Log.i(TAG,
                "FRAMES | estimatedFPS="
                        + estimateFps()
                        + " | frames="
                        + frameCount.get()
                        + " | dropped="
                        + droppedFrames.get()
                        + " | severeJank="
                        + severeJankFrames.get());
    }

    public static void reportMainThread() {

        Log.i(TAG,
                "MAIN | maxFreeze="
                        + maxMainFreezeMs.get()
                        + "ms"
                        + " | freezes="
                        + totalMainFreezes.get()
                        + " | lastLag="
                        + lastMainLag
                        + "ms");
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    private static Context getContext() {

        Activity activity =
                currentActivity.get();

        if (activity != null) {
            return activity.getApplicationContext();
        }

        return null;
    }

    private static String shorten(
            String value,
            int max) {

        if (value == null) {
            return "UNKNOWN";
        }

        if (value.length() <= max) {
            return value;
        }

        return value.substring(0, max)
                + "...";
    }
    }
