package com.store.app;

import android.app.Application;
import android.content.pm.PackageInfo;
import android.os.Process;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebStorage;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewOutcomeReceiver;
import androidx.webkit.WebViewStartUpConfig;
import androidx.webkit.WebViewStartUpResult;
import androidx.webkit.WebViewStartupException;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RoyalApplication extends Application {

    // 👑 Executor خاص بـ startUpWebView
    private static final ExecutorService STARTUP_EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();

        final String TAG = "RoyalEngine";

        Log.i(TAG, "🚀 Royal Application Ignite!");

        // هذه الخدمات لا تعتمد على إنشاء WebView.
        NetworkMonitor.init(this);
        RoyalPanopticon.startAwareness();

        // 🟢 تسخين كاش الذاكرة (RAM Page Cache) لمكتبات C++ في خيط خلفي خفيف
        prewarmWebViewPageCache();

        /*
         * =========================================================
         * Chromium / WebView asynchronous startup
         * =========================================================
         *
         * مهم جداً:
         * لا تنشئ WebView هنا.
         * لا تستدعِ CookieManager.
         * لا تستدعِ ProfileStore.
         * لا تستدعِ WebViewFeature.
         *
         * كل ذلك ينتظر callback.
         */

        try {

            WebViewStartUpConfig config =
                    new WebViewStartUpConfig.Builder(
                            STARTUP_EXECUTOR
                    )
                    .setShouldRunUiThreadStartUpTasks(true)
                    .build();

            WebViewCompat.startUpWebView(
                    getApplicationContext(),
                    config,
                    new WebViewOutcomeReceiver<
                            WebViewStartUpResult,
                            WebViewStartupException>() {

                        @Override
                        public void onResult(WebViewStartUpResult result) {

                            Log.i(
                                    TAG,
                                    "✅ Chromium startup completed. "
                                            + "WebView creation path is now warmed."
                            );

                            /*
                             * لا تنشئ WebView وهمياً هنا.
                             * لا loadUrl("about:blank").
                             * لا destroy().
                             *
                             * الـ WebView الحقيقي في MainActivity هو الذي يجب
                             * أن يستفيد من Chromium startup الذي تم تجهيزه.
                             */
                            RoyalWebViewHost.onWebViewStartupReady(
                                    getApplicationContext()
                            );
                        }

                        @Override
                        public void onError(WebViewStartupException exception) {

                            Log.e(
                                    TAG,
                                    "❌ WebView startup failed: "
                                            + exception.getMessage(),
                                    exception
                            );

                            RoyalWebViewHost.onWebViewStartupFailed(exception);
                        }
                    }
            );

            Log.i(TAG,
                    "🚀 Async WebView startup requested.");

        } catch (Throwable t) {

            Log.e(
                    TAG,
                    "❌ Unable to start WebView startup pipeline.",
                    t
            );

            RoyalWebViewHost.onWebViewStartupFailed(t);
        }
    }

    /**
     * 1. تسخين ذاكرة الصفحات (Page Fault Pre-warming)
     * قراءة صامتة لملف APK الخاص بـ WebView Provider لخداع الكيرنل
     * وسحب مكتبات C++ إلى ذاكرة الـ RAM تلقائياً في الخلفية.
     */
    private void prewarmWebViewPageCache() {
        STARTUP_EXECUTOR.execute(() -> {
            try {
                Process.setThreadPriority(
                        Process.THREAD_PRIORITY_BACKGROUND
                );

                PackageInfo webViewPackage =
                        WebViewCompat.getCurrentWebViewPackage(this);

                if (webViewPackage != null
                        && webViewPackage.applicationInfo != null) {

                    String apkPath =
                            webViewPackage.applicationInfo.publicSourceDir;

                    if (apkPath != null) {

                        try (InputStream is =
                                     new FileInputStream(apkPath)) {

                            byte[] buffer =
                                    new byte[64 * 1024];

                            while (is.read(buffer) != -1) {
                                // Page-cache prewarm only.
                            }
                        }
                    }
                }

            } catch (Throwable ignored) {
                // Never interfere with WebView startup.
            }
        });
    }
}
