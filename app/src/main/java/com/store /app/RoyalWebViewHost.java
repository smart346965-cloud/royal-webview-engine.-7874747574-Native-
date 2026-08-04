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
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.WebView;
import androidx.webkit.Profile;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewRenderProcess;
import androidx.webkit.WebViewRenderProcessClient;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

public final class RoyalWebViewHost {
    private static final String TAG = "RoyalWebViewHost";
    private static WebView webViewInstance;
    private static MutableContextWrapper contextWrapper;
    private static volatile boolean isInitialized = false; // استخدام volatile لضمان الرؤية بين الخيوط

    private static long lastRestartTime = 0;
    private static final long MAX_UPTIME = 3 * 60 * 60 * 1000L;
    private static RoyalJsBridge jsBridgeInstance;

    private RoyalWebViewHost() {}

    /**
     * 🚀 إقلاع النواة المبكر (Async Startup)
     * يتم استدعاؤها في Application.onCreate() قبل كل شيء
     */
    public static void startEngineAsync(Context context) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.STARTUP_FEATURE_SET_DATA_DIRECTORY_SUFFIX)) {
            WebViewCompat.startUpWebView(context, new WebViewCompat.StartUpConfig.Builder()
                .setBgExecutor(Executors.newSingleThreadExecutor())
                .build(),
                new WebViewCompat.StartUpCallback() {
                    @Override
                    public void onSuccess(WebViewCompat.StartUpResult result) {
                        Log.i(TAG, "🚀 Chromium Process Bootstrapped Asynchronously.");
                    }
                    @Override
                    public void onFailure(WebViewCompat.StartUpFailure failure) {
                        Log.e(TAG, "Chromium Bootstrap Failed", failure.getException());
                    }
                });
        }
    }

    public static synchronized void create(Context applicationContext) {
        if (Looper.myLooper() != Looper.getMainLooper()) return;
        
        // إذا كان هناك نسخة قديمة أو قيد الإنشاء، لا تفعل شيئاً
        if (webViewInstance != null && isInitialized) return;

        try {
            Log.i(TAG, "🚀 Rocket Ignite: Pre-warming Immortal Engine...");
            
            if (contextWrapper == null) {
                contextWrapper = new MutableContextWrapper(applicationContext.getApplicationContext());
            }

            // تسريع الكوكيز
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);

            // خلق النسخة
            webViewInstance = new WebView(contextWrapper);
            
            // إعدادات الأولوية القصوى
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                webViewInstance.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
            }
            webViewInstance.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            // تفعيل كاش التنقل العكسي (Back/Forward Cache)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
                WebViewCompat.setBackForwardCacheEnabled(webViewInstance, true);
            }

            // حقن المحركات
            RoyalHybridEngine.prime(webViewInstance, applicationContext);
            RoyalNetworkEngine.install(applicationContext);
            
            jsBridgeInstance = new RoyalJsBridge(webViewInstance);
            webViewInstance.addJavascriptInterface(jsBridgeInstance, "RoyalBridge");

            // [داخل RoyalWebViewHost.java]
            webViewInstance.setBackgroundColor(Color.parseColor("#F3F4F6")); // نفس لون السبلاش بدقة
            // إخفاء الويب فيو برمجياً وليس عبر الـ Alpha (للحفاظ على كفاءة الـ GPU)
            webViewInstance.setVisibility(View.VISIBLE);

            // [تعديل في RoyalWebViewHost.java]
            // تأكد أن الهيكل المسخن يحتوي على نفس اللون بدقة
            String warmUpHtml = "<html><body style='background:#F3F4F6;'></body></html>";
            webViewInstance.loadDataWithBaseURL("https://kith.com/", warmUpHtml, "text/html", "UTF-8", null);

            lastRestartTime = System.currentTimeMillis();
            
            // التسخين المسبق المعتمد من نواة كروميوم للشبكة والرندرة
            Uri targetUri = Uri.parse("https://kith.com/");

            // 1. فتح قناة الاتصال المسبق (Preconnect)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ENQUEUE_PRECONNECT)) {
                try {
                    Profile defaultProfile = WebViewCompat.getProfile(webViewInstance);
                    defaultProfile.enqueuePreconnect(targetUri, null);
                    Log.i(TAG, "🌐 Preconnect enqueued successfully.");
                } catch (Exception e) {
                    Log.e(TAG, "Preconnect failed", e);
                }
            }

            // 2. الرندرة المسبقة للصفحة (Prerender Async)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PRERENDER_URL)) {
                try {
                    WebViewCompat.prerenderUrlAsync(
                        webViewInstance,
                        targetUri.toString(),
                        null,
                        Executors.newSingleThreadExecutor(),
                        new WebViewCompat.PrerenderOperationCallback() {
                            @Override
                            public void onPrerenderStarted() {
                                Log.i(TAG, "⚡ Prerender Started for Domain.");
                            }
                            @Override
                            public void onPrerenderFailed(int error) {
                                Log.w(TAG, "Prerender Failed with code: " + error);
                            }
                        }
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Prerender execution failed", e);
                }
            }
            
            // 👑 الآن فقط نعلن أن المحرك جاهز (بعد نجاح كل الخطوات)
            isInitialized = true;
            Log.i(TAG, "✅ Engine is HOT and Ready.");

        } catch (Exception e) {
            isInitialized = false;
            webViewInstance = null;
            Log.e(TAG, "❌ FATAL: Initialization failed, resetting...", e);
        }
    }

    public static synchronized WebView attach(Activity activity) {
        // إذا لم يكن جاهزاً، قم بخلقه فوراً (حماية من الكراش)
        if (!isInitialized || webViewInstance == null) {
            create(activity.getApplicationContext());
        }

        checkSoftRestart(activity.getApplicationContext());

        Log.i(TAG, "🔗 Attaching to: " + activity.getClass().getSimpleName());
        
        contextWrapper.setBaseContext(activity);
        safeRemoveFromParent();

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
    }

    public static void checkSoftRestart(Context context) {
        if (System.currentTimeMillis() - lastRestartTime > MAX_UPTIME) {
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
}
