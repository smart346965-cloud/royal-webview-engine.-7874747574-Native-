package com.store.app;

import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.store.app.offline.OfflineStateManager;

public class RoyalJsBridge {

    private static final String TAG = "RoyalJsBridge";
    private final WebView webView;
    private final WebEngineManager webEngineManager;
    private Runnable onHideSplashCallback;

    // 🚀 جسر الصواريخ: مسار خلفي معزول (Single Thread) لمعالجة أوامر JS دون خنق واجهة المستخدم
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    public RoyalJsBridge(
            WebView webView,
            WebEngineManager webEngineManager) {

        this.webView = webView;
        this.webEngineManager = webEngineManager;

        RoyalPanopticon.registerDependency(
                "WebChromeEngine",
                "JS-BridgeChannel"
        );
    }

    // =========================================================
    // 🧠 ROYAL BRIDGE V6
    // Single Prediction API
    // =========================================================
    @JavascriptInterface
    public void predict(String url) {

        if (url == null || url.length() == 0) {
            return;
        }

        if (webEngineManager == null) {
            return;
        }

        webView.post(() -> {

            try {

                RoyalPanopticon.pulse(
                        "JS-BridgeChannel"
                );

                webEngineManager.predict(url);

            } catch (Exception e) {

                Log.w(
                        TAG,
                        "Prediction dispatch failed.",
                        e
                );
            }
        });
    }

    /**
     * 👑 Native Auto-OAuth Trigger: الاعتراض الآلي المباشر لزر تسجيل الدخول
     * يفتح الـ Custom Tab من أول كسر في الثانية ويمنع كتابة الـ state في ذاكرة الـ WebView
     */
    @JavascriptInterface
    public void startOAuth(String authUrl) {
        if (authUrl == null || authUrl.length() == 0) {
            return;
        }

        if (webEngineManager == null || webView == null) {
            return;
        }

        webView.post(() -> {
            try {
                RoyalPanopticon.pulse("JS-BridgeChannel");

                // 📴 بوابة الأوفلاين المركزية لمسار OAuth
                if (!NetworkMonitor.isInternetAvailable(webView.getContext())) {
                    OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                    Log.i(TAG, "📴 OAuth blocked by native offline guard.");
                    return;
                }

                Log.i(TAG, "🚀 Native Auto-OAuth Intercepted: " + authUrl);

                webEngineManager.launchSensitiveFlow(
                        android.net.Uri.parse(authUrl)
                );

            } catch (Exception e) {
                Log.e(TAG, "startOAuth dispatch failed.", e);
            }
        });
    }

    public void setOnHideSplashCallback(Runnable callback) {
        this.onHideSplashCallback = callback;
    }

    /**
     * 🌊 Scroll velocity hint
     */
    @JavascriptInterface
    public void scrollHint(int velocity) {
        backgroundExecutor.execute(() -> {
            try {
                RoyalPanopticon.pulse("JS-BridgeChannel");
                // Log.d(TAG, "Scroll velocity: " + velocity);
            } catch (Exception e) {
                Log.e(TAG, "scrollHint error", e);
            }
        });
    }

    /**
     * 🧠 JS diagnostic channel
     */
    @JavascriptInterface
    public void log(String message) {
        Log.d(TAG, "JS: " + message);
        RoyalPanopticon.pulse("WebChromeEngine");
    }

    /**
     * 🎭 Visual Completeness Signal
     * يُستدعى من الجافاسكريبت عندما يكتمل رسم الموقع بالكامل
     */
    @JavascriptInterface
    public void hideSplash() {
        if (onHideSplashCallback != null) {
            if (webView != null) {
                webView.post(onHideSplashCallback);
            }
        }
    }

    /**
     * 👁️‍عون Panopticon Telemetry Receiver
     * يستقبل الحالة الصحية للمتصفح من الجافاسكريبت ويرسلها للعقل المدبر
     */
    @JavascriptInterface
    public void reportBrowserState(int domNodes, int fps, long jsMemoryMB, int longTasks) {
        backgroundExecutor.execute(() -> {
            try {
                RoyalPanopticon.syncBrowserState(domNodes, fps, jsMemoryMB, longTasks);
                RoyalPanopticon.pulse("WebChromeEngine");
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync browser state", e);
            }
        });
    }

    @JavascriptInterface
    public void inspect() {
        backgroundExecutor.execute(() -> {
            try {
                String report = RoyalPanopticon.buildReport();

                // معالجة النصوص الثقيلة في المسار الخلفي
                report = report
                        .replace("\\", "\\\\")
                        .replace("`", "\\`")
                        .replace("$", "\\$");

                final String js = "console.log(`" + report + "`);";
                
                // إرسال النتيجة النهائية فقط للمسار الرئيسي
                if (webView != null) {
                    webView.post(() -> webView.evaluateJavascript(js, null));
                }
            } catch (Exception e) {
                Log.e("RoyalJsBridge", "Inspect failed", e);
            }
        });
    }

    /**
     * 🔁 Native → JS callback
     */
    public void dispatchToJS(String script) {
        if (webView == null) return;
        webView.post(() -> webView.evaluateJavascript(script, null));
    }
            }
