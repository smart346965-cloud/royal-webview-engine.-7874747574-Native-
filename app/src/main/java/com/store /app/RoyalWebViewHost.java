package com.store.app;

import android.app.Activity;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Color;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;

import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.store.app.BuildConfig;

import java.util.ArrayList;

public final class RoyalWebViewHost {
    private static final String TAG = "RoyalWebViewHost";

    private static WebView webViewInstance;
    private static MutableContextWrapper contextWrapper;
    private static RoyalJsBridge jsBridgeInstance;
    private static volatile boolean isInitialized = false;

    // حالات Startup
    private static volatile boolean webViewStartupReady = false;
    private static volatile Throwable webViewStartupFailure;
    private static final ArrayList<Runnable> startupListeners = new ArrayList<>();

    private RoyalWebViewHost() {}

    // =========================================================
    // 🚀 دوال إدارة Startup
    // =========================================================

    public static synchronized void onWebViewStartupReady(Context context) {
        webViewStartupReady = true;
        webViewStartupFailure = null;
        Log.i(TAG, "🔥 WebView startup barrier OPEN.");

        warmUpDefaultProfile(context);

        for (Runnable listener : startupListeners) {
            listener.run();
        }
        startupListeners.clear();
    }

    public static synchronized void onWebViewStartupFailed(Throwable error) {
        webViewStartupFailure = error;
        Log.e(TAG, "❌ WebView startup barrier FAILED.", error);
    }

    public static synchronized void whenStartupReady(Runnable listener) {
        if (webViewStartupReady) {
            listener.run();
            return;
        }
        if (webViewStartupFailure != null) {
            Log.e(TAG, "WebView startup previously failed; listener not executed.");
            return;
        }
        startupListeners.add(listener);
    }

    private static void warmUpDefaultProfile(Context context) {
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                Log.w(TAG, "MULTI_PROFILE not supported.");
                return;
            }

            Profile profile = ProfileStore.getInstance()
                    .getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME);

            if (WebViewFeature.isFeatureSupported(WebViewFeature.WARM_UP_RENDERER_PROCESS)) {
                profile.warmUpRendererProcess();
                Log.i(TAG, "🧠 Chromium renderer warm-up requested.");
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.PRECONNECT)) {
                android.net.Uri clientUri =
                        android.net.Uri.parse(BuildConfig.CLIENT_URL);

                android.net.Uri originUri =
                        new android.net.Uri.Builder()
                                .scheme(clientUri.getScheme())
                                .authority(clientUri.getAuthority())
                                .build();

                profile.preconnect(originUri);
                Log.i(TAG, "🌐 WebView preconnect requested for: " + originUri.toString());
            }

            if (WebViewFeature.isFeatureSupported(WebViewFeature.ADD_QUIC_HINTS_V1)) {
                android.net.Uri clientUri =
                        android.net.Uri.parse(BuildConfig.CLIENT_URL);

                android.net.Uri originUri =
                        new android.net.Uri.Builder()
                                .scheme(clientUri.getScheme())
                                .authority(clientUri.getAuthority())
                                .build();

                java.util.Set<String> origins = new java.util.HashSet<>();
                origins.add(originUri.toString());
                profile.addQuicHints(origins);
                Log.i(TAG, "🚀 QUIC hint registered for client origin.");
            }

        } catch (Throwable t) {
            Log.w(TAG, "Profile warm-up failed: " + t.getMessage(), t);
        }
    }

    // =========================================================
    // 🚀 إقلاع النواة (create)
    // =========================================================

    public static synchronized void create(Activity activity) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("RoyalWebViewHost.create() must run on Main Looper.");
        }

        if (!webViewStartupReady) {
            throw new IllegalStateException("WebView startup is not complete yet.");
        }

        if (webViewInstance != null && isInitialized) {
            return;
        }

        try {
            Log.i(TAG, "🔥 Creating production WebView on UI thread.");

            if (contextWrapper == null) {
                contextWrapper = new MutableContextWrapper(activity);
            } else {
                contextWrapper.setBaseContext(activity);
            }

            CookieManager.getInstance().setAcceptCookie(true);

            WebView webView = new WebView(contextWrapper);
            webViewInstance = webView;

            webView.setBackgroundColor(Color.parseColor("#F3F4F6"));
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

            android.webkit.WebSettings settings = webView.getSettings();
            settings.setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
            settings.setDomStorageEnabled(true);

            RoyalHybridEngine.prime(webView, activity.getApplicationContext());
            RoyalNetworkEngine.install(activity.getApplicationContext());

            jsBridgeInstance = new RoyalJsBridge(webView);
            webView.addJavascriptInterface(jsBridgeInstance, "RoyalBridge");

            webViewInstance = webView;
            isInitialized = true;

            Log.i(TAG, "✅ Production WebView created and ready.");

        } catch (Throwable t) {
            isInitialized = false;
            webViewInstance = null;
            jsBridgeInstance = null;
            Log.e(TAG, "❌ WebView creation failed.", t);
            throw t;
        }
    }

    // =========================================================
    // 🔗 attach / detach / destroy
    // =========================================================

    public static synchronized WebView attach(Activity activity) {
        if (!isInitialized || webViewInstance == null) {
            create(activity);
        }

        if (contextWrapper != null) {
            contextWrapper.setBaseContext(activity);
        }

        safeRemoveFromParent();

        // 👑 الإبقاء على WebView في وضع INVISIBLE حتى يصبح جاهزاً للعرض
        webViewInstance.setVisibility(View.INVISIBLE);

        webViewInstance.onResume();
        webViewInstance.resumeTimers();

        Log.i(TAG, "🔗 WebView attached to " + activity.getClass().getSimpleName());
        return webViewInstance;
    }

    public static synchronized void detach() {
        if (webViewInstance == null) return;

        safeRemoveFromParent();
        RoyalSessionSentinel.freeze(webViewInstance);

        webViewInstance.onPause();
        webViewInstance.pauseTimers();
        webViewInstance.setVisibility(View.INVISIBLE);

        if (contextWrapper != null) {
            contextWrapper.setBaseContext(webViewInstance.getContext().getApplicationContext());
        }

        Log.i(TAG, "❄️ WebView detached and session frozen.");
    }

    public static synchronized void destroy() {
        if (webViewInstance != null) {
            WebView dead = webViewInstance;
            webViewInstance = null;
            jsBridgeInstance = null;
            isInitialized = false;

            safeRemoveFromParent();
            dead.destroy();
        }

        RoyalHybridEngine.reset();
        Log.i(TAG, "💀 WebView engine destroyed.");
    }

    // =========================================================
    // 🧹 أدوات مساعدة
    // =========================================================

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
