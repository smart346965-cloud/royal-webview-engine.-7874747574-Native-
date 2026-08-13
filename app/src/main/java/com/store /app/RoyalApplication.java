package com.store.app;

import android.app.Application;
import android.util.Log;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewOutcomeReceiver;
import androidx.webkit.WebViewStartUpConfig;
import androidx.webkit.WebViewStartUpResult;
import androidx.webkit.WebViewStartupException;

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

                            Log.i(TAG,
                                    "✅ Chromium startup completed. "
                                            + "UI is now safe to create WebView.");

                            /*
                             * هذا callback يأتي على Main Looper.
                             *
                             * من هذه النقطة فقط نسمح للـ WebView
                             * بالدخول في دورة حياته.
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
}
