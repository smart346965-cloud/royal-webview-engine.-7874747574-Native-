package com.store.app;

import android.app.Application;
import android.net.Uri;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.HashSet;
import java.util.Set;

public class RoyalApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i("RoyalEngine", "🚀 Royal Application Ignite!");

        // 🌐 تشغيل رادار مراقبة الشبكة فوراً
        NetworkMonitor.init(this);

        // 👁️ تشغيل عقل الفحص الملكي
        RoyalPanopticon.startAwareness();

        // 🔥 [التحسين 11]: startUpWebView - تسخين WebView بشكل غير متزامن (API رسمي من Jetpack Webkit 1.16.0)
        // ينقل عبء التهيئة لخيط خلفي ويجزئ عمل الخيط الرئيسي، مما يقلل ANR ويحسن الأداء
        // يجب أن يكون قبل أي استدعاء آخر لـ WebView APIs
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.START_UP_WEB_VIEW)) {
                // استخدام BACKGROUND_EXECUTOR الموجود بالفعل في RoyalWebViewHost
                androidx.webkit.WebViewStartUpConfig config = new androidx.webkit.WebViewStartUpConfig.Builder(
                        RoyalWebViewHost.getBackgroundExecutor()
                ).build();

                WebViewCompat.startUpWebView(
                        this,
                        config,
                        new androidx.webkit.WebViewOutcomeReceiver<
                                androidx.webkit.WebViewStartUpResult,
                                androidx.webkit.WebViewStartupException>() {
                            @Override
                            public void onSuccess(androidx.webkit.WebViewStartUpResult result) {
                                Log.i("RoyalEngine", "✅ startUpWebView succeeded.");
                            }

                            @Override
                            public void onFailure(androidx.webkit.WebViewStartupException exception) {
                                Log.w("RoyalEngine", "⚠️ startUpWebView failed: " + exception.getMessage());
                            }
                        }
                );
                Log.i("RoyalEngine", "🚀 startUpWebView triggered asynchronously.");
            } else {
                Log.w("RoyalEngine", "⚠️ START_UP_WEB_VIEW not supported on this device.");
            }
        } catch (Exception e) {
            Log.w("RoyalEngine", "startUpWebView not available: " + e.getMessage());
        }

        // 🔥 الخطوة الأولى: تسخين نواة كروميوم بشكل غير متزامن
        // هذا يقلل وقت التهيئة بشكل كبير ويوزع الحمل على خيط خلفي
        RoyalWebViewHost.startEngineAsync(this);

        // 🔥 [التحسين 2]: تسخين خدمة الشبكة مبكراً (جديد)
        // هذا يقلل وقت التهيئة عند أول طلب شبكي
        RoyalNetworkEngine.warmupNetworkService(this);

        // 🔥 [التحسين 3]: تسخين Renderer (جديد)
        // يقوم بإنشاء Spare Renderer في الخلفية
        RoyalWebViewHost.prepareSpareRenderer(this);

        // 🔥 الخطوة الثانية: التسخين المباشر للمحرك (يتم بالتزامن مع الخطوة الأولى)
        // بما أن startEngineAsync يعمل على خيط خلفي، فهذا التسخين يضمن
        // أن WebView جاهز فوراً عند الحاجة إليه
        RoyalWebViewHost.create(this);

        // 🔥 [التحسين 8]: تسخين Renderer عبر Profile API (أعمق من Spare Renderer)
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WARM_UP_RENDERER_PROCESS)) {
                androidx.webkit.ProfileStore.getInstance()
                        .getOrCreateProfile("Default")
                        .warmUpRendererProcess();
                Log.i("RoyalEngine", "🧠 Renderer process warmed up via Profile API.");
            }
        } catch (Exception e) {
            Log.w("RoyalEngine", "Profile warmUpRendererProcess failed: " + e.getMessage());
        }

        // 🔥 [التحسين 9]: Preconnect مسبق لأصل الموقع
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PRECONNECT)) {
                androidx.webkit.ProfileStore.getInstance()
                        .getOrCreateProfile("Default")
                        .preconnect(Uri.parse("https://kith.com/").getHost());
                Log.i("RoyalEngine", "🌐 Preconnect enqueued for origin.");
            }
        } catch (Exception e) {
            Log.w("RoyalEngine", "Preconnect failed: " + e.getMessage());
        }

        // 🔥 [التحسين 10]: إعلام WebView بأن الأصل يدعم QUIC
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ADD_QUIC_HINTS_V1)) {
                Set<String> origins = new HashSet<>();
                origins.add("https://kith.com");
                androidx.webkit.ProfileStore.getInstance()
                        .getOrCreateProfile("Default")
                        .addQuicHints(origins);
                Log.i("RoyalEngine", "🚀 QUIC hints added.");
            }
        } catch (Exception e) {
            Log.w("RoyalEngine", "addQuicHints failed: " + e.getMessage());
        }

        // 🔥 [التحسين 4]: IdleHandler لتهيئة المهام الخفيفة أثناء خمول المعالج
        Looper.myQueue().addIdleHandler(() -> {
            try {
                // تهيئة CookieManager في الخلفية
                CookieManager.getInstance().setAcceptCookie(true);
                Log.i("RoyalEngine", "🍪 CookieManager initialized via IdleHandler.");

                // تسخين SafeBrowsing (إن كان مدعوماً)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) {
                    WebViewCompat.startSafeBrowsing(this, value ->
                        Log.i("RoyalEngine", "🛡️ SafeBrowsing warmed up.")
                    );
                }
            } catch (Exception e) {
                Log.w("RoyalEngine", "IdleHandler task failed: " + e.getMessage());
            }
            return false; // تنفيذ مرة واحدة فقط
        });

        Log.i("RoyalEngine", "✅ All systems initialized. Ready for action.");
    }

    @Override
    public void onTerminate() {
        // إيقاف العقل الملكي وتنظيف الذاكرة
        RoyalPanopticon.stopAwareness();

        // تنظيف المحرك لمنع تسريب الذاكرة
        RoyalWebViewHost.destroy();

        super.onTerminate();
    }
                    }
