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

        // ==========================================
        // 🔥 المرحلة 1: startUpWebView (يجب أن يكون الأول)
        // ==========================================
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.START_UP_WEB_VIEW)) {
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

        // ==========================================
        // 🔥 المرحلة 2: تسخين Profile APIs
        // ==========================================

        // 🔥 [التحسين 8]: تسخين Renderer عبر Profile API
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
                        .preconnect(Uri.parse("https://bellroy.com/").getHost());
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

        // ==========================================
        // 🔥 المرحلة 3: تسخين المحرك
        // ==========================================

        // 🔥 الخطوة الأولى: تسخين نواة كروميوم بشكل غير متزامن
        RoyalWebViewHost.startEngineAsync(this);

        // 🔥 [التحسين 2]: تسخين خدمة الشبكة مبكراً
        RoyalNetworkEngine.warmupNetworkService(this);

        // 🔥 [التحسين 3]: تسخين Renderer (Spare Renderer)
        RoyalWebViewHost.prepareSpareRenderer(this);

        // 🔥 الخطوة الثانية: التسخين المباشر للمحرك (يجب أن يكون الأخير)
        RoyalWebViewHost.create(this);

        // ==========================================
        // 🔥 المرحلة 4: IdleHandler (يُترك للأخير)
        // ==========================================

        Looper.myQueue().addIdleHandler(() -> {
            try {
                CookieManager.getInstance().setAcceptCookie(true);
                Log.i("RoyalEngine", "🍪 CookieManager initialized via IdleHandler.");

                if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) {
                    WebViewCompat.startSafeBrowsing(this, value ->
                        Log.i("RoyalEngine", "🛡️ SafeBrowsing warmed up.")
                    );
                }
            } catch (Exception e) {
                Log.w("RoyalEngine", "IdleHandler task failed: " + e.getMessage());
            }
            return false;
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
