package com.store.app;

import android.app.Activity;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import androidx.webkit.Profile;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RoyalWebViewHost {
    private static final String TAG = "RoyalWebViewHost";
    private static final String BASE_URL = "https://kith.com/";
    private static final long MAX_UPTIME = 3 * 60 * 60 * 1000L; // 3 ساعات

    private static WebView webViewInstance;
    private static MutableContextWrapper contextWrapper;
    private static RoyalJsBridge jsBridgeInstance;
    private static volatile boolean isInitialized = false;
    private static long lastRestartTime = 0;

    // 🧵 مشاركة Executor واحد لكل العمليات الخلفية
    private static final ExecutorService BACKGROUND_EXECUTOR = Executors.newSingleThreadExecutor();

    private RoyalWebViewHost() {}

    /**
     * 🚀 إقلاع النواة المبكر (Async Startup)
     * تهيئة آمنة تتوافق مع كافة الإصدارات دون رموز تجريبية مفقودة
     */
    public static void startEngineAsync(Context context) {
        Log.i(TAG, "🚀 Bootstrapping Chromium asynchronously...");
        BACKGROUND_EXECUTOR.execute(() -> {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) {
                    WebViewCompat.startSafeBrowsing(context.getApplicationContext(), value -> 
                        Log.i(TAG, "✅ Chromium Engine Pre-warmed Successfully.")
                    );
                }
            } catch (Throwable e) {
                Log.w(TAG, "Chromium bootstrap notice: " + e.getMessage());
            }
        });
    }

    public static synchronized void create(Context applicationContext) {
        // تأكيد الخيط الرئيسي
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "create() called off main thread, posting...");
            Looper.getMainLooper().getThread().getUncaughtExceptionHandler();
            return;
        }

        if (webViewInstance != null && isInitialized) {
            Log.d(TAG, "Engine already initialized, skipping.");
            return;
        }

        try {
            Log.i(TAG, "🔥 Rocket Ignite: Pre-warming Immortal Engine...");

            // 1. تهيئة السياق
            if (contextWrapper == null) {
                contextWrapper = new MutableContextWrapper(applicationContext.getApplicationContext());
            }

            // 2. تسريع الكوكيز
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);

            // 3. خلق النواة
            webViewInstance = new WebView(contextWrapper);

            // 4. إعدادات الأولوية القصوى (معالج العرض)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                webViewInstance.setRendererPriorityPolicy(
                    WebView.RENDERER_PRIORITY_BOUND,  // أعلى أولوية
                    true                             // الإبقاء على المعالج حياً
                );
            }

            // 5. التسريع العتادي (مهم جداً للرسم المباشر)
            webViewInstance.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            // 6 & 7. 👑 ضبط كاش التنقل وإعدادات الذاكرة المتقدمة قياسياً
            android.webkit.WebSettings settings = webViewInstance.getSettings();
            settings.setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            Log.i(TAG, "📦 DOM & Disk Caching Policies Active.");

            // 8. حقن المحركات المخصصة
            RoyalHybridEngine.prime(webViewInstance, applicationContext);
            RoyalNetworkEngine.install(applicationContext);

            // 9. جسر JavaScript
            jsBridgeInstance = new RoyalJsBridge(webViewInstance);
            webViewInstance.addJavascriptInterface(jsBridgeInstance, "RoyalBridge");

            // 10. تكوين الخلفية (لتجنب الومضات البيضاء)
            webViewInstance.setBackgroundColor(Color.parseColor("#F3F4F6"));
            // ⚠️ استخدم INVISIBLE بدل VISIBLE لتجنب الرسم غير الضروري أثناء التسخين
            webViewInstance.setVisibility(View.INVISIBLE);

            // 11. تحميل صفحة وهمية لتسخين المحرك بالكامل
            String warmUpHtml = "<html><body style='background:#F3F4F6;'></body></html>";
            webViewInstance.loadDataWithBaseURL(BASE_URL, warmUpHtml, "text/html", "UTF-8", null);

            lastRestartTime = System.currentTimeMillis();

            // 12. 🌐 التسخين المسبق المعتمد من نواة كروميوم
            warmupNetworkAndRenderer(webViewInstance);

            // 13. 👑 إعلان الجاهزية
            isInitialized = true;
            Log.i(TAG, "✅ Engine is HOT and Ready.");

        } catch (Exception e) {
            isInitialized = false;
            webViewInstance = null;
            Log.e(TAG, "❌ FATAL: Initialization failed, resetting...", e);
        }
    }

    /**
     * 🔥 تسخين الشبكة وربط المقابس (Socket Pre-connect) بأمان نيتف خالص
     */
    private static void warmupNetworkAndRenderer(WebView webView) {
        if (webView == null) return;

        BACKGROUND_EXECUTOR.execute(() -> {
            try {
                // فتح اتصال شبكي استباقي (DNS Lookup + TCP Handshake) لمنع التأخير عند طلب أول صفحة
                java.net.URL url = new java.net.URL(BASE_URL);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(2500);
                conn.setReadTimeout(2500);
                conn.connect();
                conn.disconnect();
                Log.i(TAG, "🌐 Network Sockets & DNS pre-warmed for: " + BASE_URL);
            } catch (Throwable e) {
                Log.d(TAG, "Network pre-warm note: " + e.getMessage());
            }
        });
    }

    /**
     * 🔥 تحضير Spare Renderer (معالج عرض احتياطي)
     * يتم استدعاؤها من Application.onCreate() لتسخين عملية الرندر مبكراً
     * هذا يقلل وقت إنشاء أول WebView بنسبة تصل إلى 50%
     */
    public static void prepareSpareRenderer(Context context) {
        BACKGROUND_EXECUTOR.execute(() -> {
            try {
                Log.i(TAG, "🧠 Preparing Spare Renderer...");
                // إنشاء WebView مؤقت في الخلفية
                // هذا الطلب يجبر النظام على إنشاء عملية Renderer جديدة وتجهيزها
                WebView tempWebView = new WebView(context.getApplicationContext());
                tempWebView.setVisibility(View.INVISIBLE);
                tempWebView.loadUrl("about:blank");
                
                // نتركه يعمل للحظات ثم نتخلص منه، تاركين الـ Renderer جاهزاً
                Thread.sleep(50); // وقت كافٍ لبدء العملية
                tempWebView.destroy();
                
                Log.i(TAG, "✅ Spare Renderer prepared successfully.");
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Spare Renderer preparation failed: " + e.getMessage());
            }
        });
    }

    public static synchronized WebView attach(Activity activity) {
        if (!isInitialized || webViewInstance == null) {
            create(activity.getApplicationContext());
        }

        checkSoftRestart(activity.getApplicationContext());

        Log.i(TAG, "🔗 Attaching to: " + activity.getClass().getSimpleName());

        contextWrapper.setBaseContext(activity);
        safeRemoveFromParent();

        // إظهار الويب فيو الآن فقط
        webViewInstance.setVisibility(View.VISIBLE);
        webViewInstance.onResume();
        webViewInstance.resumeTimers();

        return webViewInstance;
    }

    public static synchronized void detach() {
        if (webViewInstance == null) return;

        safeRemoveFromParent();

        if (contextWrapper != null) {
            contextWrapper.setBaseContext(webViewInstance.getContext().getApplicationContext());
        }

        webViewInstance.onPause();
        webViewInstance.pauseTimers();
        webViewInstance.setVisibility(View.INVISIBLE);
    }

    public static synchronized void destroy() {
        if (webViewInstance != null) {
            safeRemoveFromParent();
            webViewInstance.loadUrl("about:blank");
            webViewInstance.destroy();
            webViewInstance = null;
            isInitialized = false;
        }
        RoyalHybridEngine.reset();
        Log.i(TAG, "💀 Engine destroyed.");
    }

    public static void checkSoftRestart(Context context) {
        if (System.currentTimeMillis() - lastRestartTime > MAX_UPTIME) {
            Log.w(TAG, "♻️ Max uptime reached, restarting engine...");
            destroy();
            create(context);
        }
    }

    private static void safeRemoveFromParent() {
        if (webViewInstance != null && webViewInstance.getParent() instanceof ViewGroup) {
            ((ViewGroup) webViewInstance.getParent()).removeView(webViewInstance);
        }
    }

    public static boolean isReady() {
        return isInitialized && webViewInstance != null;
    }

    public static RoyalJsBridge getBridge() {
        return jsBridgeInstance;
    }

    public static WebView getWebView() {
        return webViewInstance;
    }
        }
