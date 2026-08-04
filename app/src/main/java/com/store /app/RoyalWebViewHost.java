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
     * يتم استدعاؤها في Application.onCreate() قبل كل شيء
     */
    public static void startEngineAsync(Context context) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.STARTUP_FEATURE_SET_DATA_DIRECTORY_SUFFIX)) {
            Log.i(TAG, "🚀 Bootstrapping Chromium asynchronously...");
            
            WebViewCompat.startUpWebView(context, 
                new WebViewCompat.StartUpConfig.Builder()
                    .setBgExecutor(BACKGROUND_EXECUTOR)
                    .build(),
                new WebViewCompat.StartUpCallback() {
                    @Override
                    public void onSuccess(WebViewCompat.StartUpResult result) {
                        Log.i(TAG, "✅ Chromium Process Bootstrapped Successfully.");
                    }

                    @Override
                    public void onFailure(WebViewCompat.StartUpFailure failure) {
                        Log.e(TAG, "❌ Chromium Bootstrap Failed", failure.getException());
                    }
                }
            );
        } else {
            Log.w(TAG, "⚠️ STARTUP_FEATURE not supported on this device, falling back to manual warmup.");
            // بديل يدوي لمن لا يدعمون الميزة
            create(context);
        }
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
            cookieManager.setAcceptThirdPartyCookies(webViewInstance, true);

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

            // 6. 🔥 تفعيل كاش التنقل العكسي (Back/Forward Cache)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
                WebViewCompat.setBackForwardCacheEnabled(webViewInstance, true);
                Log.i(TAG, "📦 Back/Forward Cache enabled.");
            }

            // 7. 🔥 تفعيل تجميد الخلفية (Background Freeze) لتوفير البطارية
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FREEZE_DOES_NOT_DESTROY)) {
                // مفعّل افتراضياً، لكن نؤكد عليه
                Log.i(TAG, "❄️ Background freeze policy active.");
            }

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
     * 🔥 تسخين الشبكة والرندرة باستخدام واجهات كروميوم الرسمية
     */
    private static void warmupNetworkAndRenderer(WebView webView) {
        if (webView == null) return;

        Uri targetUri = Uri.parse(BASE_URL);

        // 1. فتح قناة الاتصال المسبق (Preconnect)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ENQUEUE_PRECONNECT)) {
            try {
                Profile defaultProfile = WebViewCompat.getProfile(webView);
                defaultProfile.enqueuePreconnect(targetUri, null);
                Log.i(TAG, "🌐 Preconnect enqueued successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Preconnect failed", e);
            }
        }

        // 2. الرندرة المسبقة للصفحة (Prerender)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PRERENDER_URL)) {
            try {
                WebViewCompat.prerenderUrlAsync(
                    webView,
                    targetUri.toString(),
                    null,
                    BACKGROUND_EXECUTOR,
                    new WebViewCompat.PrerenderOperationCallback() {
                        @Override
                        public void onPrerenderStarted() {
                            Log.i(TAG, "⚡ Prerender Started for: " + BASE_URL);
                        }

                        @Override
                        public void onPrerenderSucceeded() {
                            Log.i(TAG, "✅ Prerender Succeeded!");
                        }

                        @Override
                        public void onPrerenderFailed(int error) {
                            Log.w(TAG, "⚠️ Prerender Failed with code: " + error);
                        }
                    }
                );
            } catch (Exception e) {
                Log.e(TAG, "Prerender execution failed", e);
            }
        }
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
