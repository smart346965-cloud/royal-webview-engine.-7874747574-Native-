package com.store.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.*;

import androidx.annotation.NonNull;
import androidx.webkit.Navigation;
import androidx.webkit.NavigationListener;
import androidx.webkit.Page;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.PrerenderException;
import androidx.webkit.PrerenderOperationCallback;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.store.app.offline.OfflineStateManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public class WebEngineManager {

    private static final String TAG = "RoyalEngine";

    private final Context context;
    private final android.app.Activity activity;
    private final WebView webView;
    private final View splashOverlay;
    private final android.widget.ProgressBar progressBar;

    private final Runnable markSplashRemoved;
    private final SplashStateChecker splashChecker;

    private String trustedScheme = null;
    private String trustedHost = null;
    private int trustedPort = -1;

    private final Runnable scrollFinishedRunnable =
            RoyalNetworkEngine::notifyScrollFinished;

    private long splashStartTime = 0;

    private final RoyalCapabilitiesEngine capabilitiesEngine;

    // =========================================================
    // ⚡ Speculative Loading Fields
    // =========================================================
    private Profile webProfile = null;
    private boolean speculativeLoadingReady = false;
    private static final int MAX_SPECULATIVE_URLS = 32;
    private final Set<String> speculativeUrls = new HashSet<>();
    private CancellationSignal activePrerenderCancellationSignal = null;
    private String activePrerenderUrl = null;

    public interface SplashStateChecker {
        boolean isRemoved();
    }

    public WebEngineManager(Context context,
                            WebView webView,
                            View splashOverlay,
                            android.widget.ProgressBar progressBar,
                            Runnable markSplashRemoved,
                            SplashStateChecker splashChecker) {

        this.context = context;
        this.webView = webView;
        this.splashOverlay = splashOverlay;
        this.progressBar = progressBar;
        this.markSplashRemoved = markSplashRemoved;
        this.splashChecker = splashChecker;

        this.activity = (context instanceof android.app.Activity)
                ? (android.app.Activity) context
                : null;

        this.capabilitiesEngine = new RoyalCapabilitiesEngine(this.activity);
    }

    // =========================================================
    // ⚡ Initialize Native Speculative Loading
    // =========================================================
    private void initializeSpeculativeLoading() {

        if (activity == null) return;

        activity.runOnUiThread(() -> {
            try {
                if (!WebViewFeature.isFeatureSupported(
                        WebViewFeature.MULTI_PROFILE)) {

                    Log.w(TAG,
                            "⚠️ MULTI_PROFILE not supported; speculative profile unavailable.");

                    speculativeLoadingReady = false;
                    return;
                }

                webProfile = ProfileStore
                        .getInstance()
                        .getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME);

                speculativeLoadingReady =
                        webProfile != null;

                if (speculativeLoadingReady) {
                    Log.i(TAG,
                            "⚡ Native speculative loading ready.");
                }

            } catch (Throwable e) {
                speculativeLoadingReady = false;

                Log.e(TAG,
                        "❌ Failed to initialize WebView Profile.",
                        e);
            }
        });
    }

    // =========================================================
    // ⚡ Native Preconnect
    // =========================================================
    private void preconnectOrigin(String url) {

        if (activity == null || url == null) return;

        activity.runOnUiThread(() -> {

            try {

                if (!WebViewFeature.isFeatureSupported(
                        WebViewFeature.PRECONNECT)) {

                    Log.w(TAG,
                            "⚠️ PRECONNECT not supported.");

                    return;
                }

                if (webProfile == null) {

                    if (!WebViewFeature.isFeatureSupported(
                            WebViewFeature.MULTI_PROFILE)) {

                        Log.w(TAG,
                                "⚠️ MULTI_PROFILE not supported; cannot obtain default profile.");

                        return;
                    }

                    webProfile = ProfileStore
                            .getInstance()
                            .getOrCreateProfile(Profile.DEFAULT_PROFILE_NAME);
                }

                if (webProfile == null) return;

                Uri uri = Uri.parse(url);

                String origin = buildOrigin(uri);

                if (origin == null) return;

                webProfile.preconnect(origin);

                Log.d(TAG,
                        "⚡ Preconnected: " + origin);

            } catch (Throwable e) {

                Log.w(TAG,
                        "⚠️ Preconnect failed: " + url,
                        e);
            }
        });
    }

    private String buildOrigin(Uri uri) {

        if (uri == null) return null;

        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            return null;
        }

        scheme = scheme.toLowerCase();
        host = host.toLowerCase();

        if (!"https".equals(scheme)) {
            return null;
        }

        int port = uri.getPort();

        if (port == -1 || port == 443) {
            return scheme + "://" + host;
        }

        return scheme + "://" + host + ":" + port;
    }

    // =========================================================
    // 🔒 Speculative Origin Policy
    // =========================================================
    private boolean isSafePredictionUrl(Uri uri) {

        if (uri == null) {
            return false;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            return false;
        }

        scheme = scheme.toLowerCase();
        host = host.toLowerCase();

        // HTTPS فقط
        if (!"https".equals(scheme)) {
            return false;
        }

        if (trustedHost == null ||
                trustedScheme == null) {
            return false;
        }

        // نفس الـ scheme
        if (!trustedScheme.equalsIgnoreCase(scheme)) {
            return false;
        }

        // نفس الـ origin policy
        int port = uri.getPort();

        if (port == -1) {
            port = 443;
        }

        if (port != trustedPort) {
            return false;
        }

        String trusted =
                trustedHost.toLowerCase();

        // Root + subdomains الموثوقة
        boolean hostMatches =
                trusted.equals(host)
                        || host.endsWith("." + trusted);

        if (!hostMatches) {
            Log.w(TAG,
                    "🛡️ Prediction rejected: foreign origin -> "
                            + uri);

            return false;
        }

        return true;
    }

    // =========================================================
    // 🧠 ROYAL PREDICTION ENTRY POINT
    // =========================================================
    public void predict(String url) {

        if (activity == null || url == null) {
            return;
        }

        activity.runOnUiThread(() -> {

            try {

                Uri uri = Uri.parse(url);

                if (!isSafePredictionUrl(uri)) {
                    return;
                }

                String normalizedUrl =
                        uri.toString();

                // منع تكرار نفس التنبؤ
                if (speculativeUrls.contains(normalizedUrl)) {
                    return;
                }

                if (speculativeUrls.size() >= MAX_SPECULATIVE_URLS) {

                    Log.d(
                            TAG,
                            "🧹 Speculative URL budget reached; clearing stale predictions."
                    );

                    speculativeUrls.clear();
                }

                speculativeUrls.add(normalizedUrl);

                Log.d(TAG,
                        "🧠 High-confidence prediction: "
                                + normalizedUrl);

                // 1️⃣ افتح DNS/TCP/TLS مبكراً
                preconnectOrigin(normalizedUrl);

                // 2️⃣ جهّز الصفحة في Chromium
                startPrerender(normalizedUrl);

            } catch (Throwable e) {

                Log.w(TAG,
                        "Prediction failed: " + url,
                        e);
            }
        });
    }

    // =========================================================
    // 🚀 Chromium Native Prerender
    // =========================================================
    private void cancelActivePrerender() {

        if (activePrerenderCancellationSignal != null) {

            try {

                activePrerenderCancellationSignal.cancel();

                Log.d(
                        TAG,
                        "🛑 Active prerender cancelled: "
                                + activePrerenderUrl
                );

            } catch (Throwable e) {

                Log.w(
                        TAG,
                        "⚠️ Failed to cancel active prerender.",
                        e
                );
            }
        }

        activePrerenderCancellationSignal = null;
        activePrerenderUrl = null;
    }

    private void startPrerender(String url) {

        if (activity == null || webView == null || url == null) {
            return;
        }

        if (!WebViewFeature.isFeatureSupported(
                WebViewFeature.PRERENDER_WITH_URL)) {

            Log.w(
                    TAG,
                    "⚠️ PRERENDER_WITH_URL not supported."
            );

            return;
        }

        try {

            // =====================================================
            // 🛑 إلغاء عملية الـ prerender السابقة
            // =====================================================

            if (activePrerenderCancellationSignal != null) {

                activePrerenderCancellationSignal.cancel();

                Log.d(
                        TAG,
                        "🛑 Previous prerender cancelled: "
                                + activePrerenderUrl
                );

                activePrerenderCancellationSignal = null;
                activePrerenderUrl = null;
            }

            // =====================================================
            // 🚀 إنشاء عملية جديدة
            // =====================================================

            CancellationSignal cancellationSignal =
                    new CancellationSignal();

            activePrerenderCancellationSignal =
                    cancellationSignal;

            activePrerenderUrl = url;

            WebViewCompat.prerenderUrlAsync(
                    webView,
                    url,
                    cancellationSignal,
                    activity.getMainExecutor(),

                    new PrerenderOperationCallback() {

                        @Override
                        public void onError(
                                @NonNull PrerenderException exception) {

                            Log.w(
                                    TAG,
                                    "⚠️ Prerender rejected: "
                                            + url
                                            + " error="
                                            + exception.getMessage(),
                                    exception
                            );

                            // لا نمسح عملية أحدث بالخطأ
                            if (url.equals(activePrerenderUrl)) {

                                activePrerenderCancellationSignal =
                                        null;

                                activePrerenderUrl = null;
                            }
                        }

                        @Override
                        public void onStatusUpdated(
                                int status) {

                            Log.d(
                                    TAG,
                                    "🚀 Prerender status="
                                            + status
                                            + " url="
                                            + url
                            );
                        }
                    }
            );

            Log.d(
                    TAG,
                    "🚀 Prerender started: "
                            + url
            );

        } catch (Throwable e) {

            Log.w(
                    TAG,
                    "❌ Prerender failed: "
                            + url,
                    e
            );

            if (url.equals(activePrerenderUrl)) {

                activePrerenderCancellationSignal = null;
                activePrerenderUrl = null;
            }
        }
    }

    public RoyalCapabilitiesEngine getCapabilitiesHandler() {
        return this.capabilitiesEngine;
    }

    public void setSplashStartTime(long startTime) {
        this.splashStartTime = startTime;
    }

    public void init() {
        // ✅ الكود الجديد: معالجة التعافي الذكي من about:blank
        String currentUrl = webView.getUrl();
        if (currentUrl == null || currentUrl.equalsIgnoreCase("about:blank") || currentUrl.contains("chromewebdata")) {
            Log.w(TAG, "⚠️ WebView stuck on invalid frame (" + currentUrl + "). Recovering state...");
            if (NetworkMonitor.isInternetAvailable(context)) {
                webView.loadUrl(com.store.app.BuildConfig.CLIENT_URL);
            } else {
                OfflineStateManager.getInstance().setErrorPage(true, com.store.app.BuildConfig.CLIENT_URL);
            }
        } else if (RoyalWebViewHost.isReady()) {
            Log.i("RoyalEngine", "🔥 Warm Resume Detected, enforcing fixed splash time.");
        }

        configureSettings();

        attachClients();

        initializeSpeculativeLoading();

        // ⚡ Preconnect للـ origin الأساسي مبكراً
        String clientUrl = BuildConfig.CLIENT_URL;

        if (clientUrl != null) {
            preconnectOrigin(clientUrl);
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.NAVIGATION_LISTENER)) {
            WebViewCompat.addNavigationListener(webView, new NavigationListener() {
                @Override
                public void onFirstContentfulPaintMillis(@NonNull Page page, long durationMillis) {
                    Log.i("Performance", "🎯 FCP: " + durationMillis + "ms");
                    RoyalPanopticon.recordMetric("FCP", durationMillis);
                }

                @Override
                public void onPageDomContentLoadedEvent(@NonNull Page page) {
                    Log.i("Performance", "📄 DOMContentLoaded");
                }

                @Override
                public void onPageLoadEvent(@NonNull Page page) {
                    Log.i("Performance", "📦 Load event fired");
                }

                @Override
                public void onNavigationStarted(@NonNull Navigation navigation) {

                    speculativeUrls.clear();

                    cancelActivePrerender();

                    Log.i(
                            "Performance",
                            "🚀 Navigation started"
                    );
                }

                @Override
                public void onNavigationRedirected(@NonNull Navigation navigation) {
                    Log.i("Performance", "↪️ Navigation redirected");
                }

                @Override
                public void onNavigationCompleted(@NonNull Navigation navigation) {
                    Log.i("Performance", "✅ Navigation completed");
                }

                @Override
                public void onPageDeleted(@NonNull Page page) {
                    Log.i("Performance", "🗑️ Page evicted");
                }

                @Override
                public void onLargestContentfulPaintMillis(@NonNull Page page, long durationMillis) {
                    Log.i("Performance", "🏆 LCP: " + durationMillis + "ms");
                    RoyalPanopticon.recordMetric("LCP", durationMillis);
                }

                @Override
                public void onPerformanceMarkMillis(@NonNull Page page, @NonNull String markName, long markTimeMillis) {
                    Log.i("Performance", "📊 Performance mark: " + markName + " at " + markTimeMillis + "ms");
                }
            });
            Log.i("RoyalEngine", "📊 NavigationListener added for performance metrics.");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                RoyalNetworkEngine.notifyScroll(scrollY);
                v.removeCallbacks(scrollFinishedRunnable);
                v.postDelayed(scrollFinishedRunnable, 90);
            });
        }
    }

    private void removeSplashInstantly() {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            if (splashOverlay != null && splashOverlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) splashOverlay.getParent()).removeView(splashOverlay);
            }
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            markSplashRemoved.run();
            RoyalNetworkEngine.notifyRenderIdle();
        });
    }

    public void removeSplashSmoothly() {
        if (activity == null || splashChecker.isRemoved()) return;

        activity.runOnUiThread(() -> {
            if (splashOverlay != null && splashOverlay.getAlpha() > 0f) {
                splashOverlay.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction(this::removeSplashInstantly)
                        .start();
            } else {
                removeSplashInstantly();
            }
        });
    }

    // =========================================================
    // ❌ تم حذف الدالة triggerFinalReveal() نهائياً
    // =========================================================

    private void configureSettings() {
        WebSettings settings = webView.getSettings();

        settings.setEnableSmoothTransition(true); 
        
        // 🔥 تعديل OffscreenPreRaster إلى false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(false);
        }

        // 🔥 تم حذف webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        // 🔥 تعديل LayoutAlgorithm إلى NORMAL
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.setVerticalScrollbarThumbDrawable(null);
        }

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // =========================================================
        // 🔥 تم حذف كتلة AlgorithmicDarkening وتم الإبقاء على ForceDarkAllowed فقط
        // =========================================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.setForceDarkAllowed(false);
        }

        // =========================================================
        // 🔥 إعدادات JavaScript و DOM و Database (بدون تكرار)
        // =========================================================
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSafeBrowsingEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowFileAccessFromFileURLs(false);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }

        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
    }

    private void attachClients() {
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                RoyalPanopticon.recordRequestSent();
            }

            // =========================================================
            // 🔥 [تعديل 1] onPageFinished المحسّن
            // =========================================================
            @Override
            public void onPageFinished(WebView view, String url) {

                RoyalPanopticon.recordNavigationComplete();
                RoyalNetworkEngine.notifyRenderIdle();

                if (url == null
                        || url.startsWith("data:")
                        || url.startsWith("about:")
                        || url.contains("chromewebdata")) {

                    OfflineStateManager.getInstance().setPageValid(false);

                    Log.w(TAG, "⚠️ Invalid/error page finished: " + url);
                    return;
                }

                // إذا كان النظام سجل Error قبل onPageFinished
                // لا نقلب الحالة إلى Valid مرة أخرى.
                if (OfflineStateManager.getInstance().isOnErrorPage()) {
                    Log.w(TAG, "⚠️ Ignoring onPageFinished because page is marked as error.");
                    return;
                }

                OfflineStateManager.getInstance().setPageValid(true);

                Log.i(TAG, "✅ Page finished successfully. Page is valid.");
            }

            // =========================================================
            // 🔥 [تعديل 2] onPageCommitVisible النهائي
            // =========================================================
            @Override
            public void onPageCommitVisible(WebView view, String url) {

                if (url != null
                        && !url.startsWith("data:")
                        && !url.startsWith("about:")
                        && !url.contains("chromewebdata")) {

                    Log.i(TAG, "✅ Page committed successfully: " + url);

                    if (trustedHost == null) {
                        setTrustedOrigin(url);
                    }

                    if (activity != null) {
                        activity.runOnUiThread(() ->
                                WebEnhancer.apply(view, context)
                        );
                    }

                    RoyalNetworkEngine.notifyRenderStart();

                    // =========================================================
                    // 🔥 إضافة Visual State Callback
                    // =========================================================
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            && WebViewFeature.isFeatureSupported(
                                    WebViewFeature.VISUAL_STATE_CALLBACK
                            )) {

                        WebViewCompat.postVisualStateCallback(
                                view,
                                System.nanoTime(),
                                new WebViewCompat.VisualStateCallback() {

                                    @Override
                                    public void onComplete(long requestId) {

                                        Log.i(
                                                TAG,
                                                "🎨 Visual state ready for first valid draw."
                                        );

                                        RoyalPanopticon.recordMetric(
                                                "VisualStateReady",
                                                System.currentTimeMillis()
                                        );
                                    }
                                }
                        );
                    }

                    syncStatusBarColor(view);

                    if (NetworkMonitor.isInternetAvailable(context)
                            && !OfflineStateManager.getInstance().isOnErrorPage()
                            && OfflineStateManager.getInstance().isPageValid()) {

                        OfflineStateManager.getInstance().notifyPageReadyToHide();
                    }

                } else {

                    Log.w(TAG, "⚠️ Invalid page committed: " + url);
                }
            }

            // =========================================================
            // 🔥 [تعديل 11] onRenderProcessGone المُصحَّح
            // =========================================================
            @Override
            public boolean onRenderProcessGone(
                    WebView view,
                    RenderProcessGoneDetail detail
            ) {

                Log.e(
                        TAG,
                        detail.didCrash()
                                ? "☠️ Chromium Renderer crashed."
                                : "⚠️ Chromium Renderer killed by system."
                );

                RoyalNetworkEngine.notifyRenderIdle();

                RoyalWebViewHost.destroy();

                if (activity != null && !activity.isFinishing()) {
                    activity.recreate();
                }

                return true;
            }

            // ✅ الكود الجديد: إبلاغ الأوفلاين النيتيف بدون قطع عملية الرسم
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    OfflineStateManager.getInstance().setErrorPage(true, request.getUrl().toString());
                    Log.w(TAG, "🛡️ Main frame error detected. Native offline state activated.");
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                OfflineStateManager.getInstance().setErrorPage(true, failingUrl);
                Log.w(TAG, "🛡️ Legacy main frame error detected. Page invalid.");
            }

            // =========================================================
            // 🔥 [تعديل 10] shouldInterceptRequest المبسَّط
            // =========================================================
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return null;
                String url = request.getUrl().toString();

                if (url.contains("gorgias") || url.contains("facebook.net") || url.contains("analytics") || url.contains("klaviyo")) {
                    String stubScript = "/* Isolated by Nexus Script Shield to ensure 60FPS Performance */";
                    InputStream stubStream = new ByteArrayInputStream(stubScript.getBytes());
                    Log.d("RoyalEngine", "🛡️ Shield: Isolated Parasitic Script -> " + url);
                    return new WebResourceResponse("application/javascript", "UTF-8", stubStream);
                }

                if (url.endsWith("/royal_nucleus.js")) {
                    try {
                        java.io.InputStream jsStream = context.getAssets().open("public/js/royal_nucleus.js");
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Content-Type", "application/javascript");
                        headers.put("Access-Control-Allow-Origin", "*");
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            return new WebResourceResponse("application/javascript", "UTF-8", 200, "OK", headers, jsStream);
                        } else {
                            return new WebResourceResponse("application/javascript", "UTF-8", jsStream);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("RoyalEngine", "❌ FATAL: Failed to serve local JS Core!", e);
                    }
                }

                if (url.endsWith("/nexus-worker.js") || url.contains("nexus-worker.js")) {
                    try {
                        java.io.InputStream workerStream = context.getAssets().open("public/js/nexus-worker.js");
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Content-Type", "application/javascript");
                        headers.put("Access-Control-Allow-Origin", "*");
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            return new WebResourceResponse("application/javascript", "UTF-8", 200, "OK", headers, workerStream);
                        } else {
                            return new WebResourceResponse("application/javascript", "UTF-8", workerStream);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("RoyalEngine", "❌ FATAL: Failed to serve local Nexus Worker Core!", e);
                    }
                }

                if (url.endsWith("/royal_nucleus.wasm")) {
                    try {
                        java.io.InputStream wasmStream = context.getAssets().open("public/js/royal_nucleus.wasm");
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Content-Type", "application/wasm");
                        headers.put("Access-Control-Allow-Origin", "*");
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            return new WebResourceResponse("application/wasm", null, 200, "OK", headers, wasmStream);
                        } else {
                            return new WebResourceResponse("application/wasm", null, wasmStream);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("RoyalEngine", "❌ FATAL: Failed to serve local WASM Core!", e);
                    }
                }

                if (url.endsWith("/nexus-service-worker.js")) {
                    try {
                        java.io.InputStream swStream = context.getAssets().open("public/js/nexus-service-worker.js");
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Content-Type", "application/javascript");
                        headers.put("Service-Worker-Allowed", "/");
                        headers.put("Cache-Control", "no-cache"); 

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            return new WebResourceResponse("application/javascript", "UTF-8", 200, "OK", headers, swStream);
                        } else {
                            return new WebResourceResponse("application/javascript", "UTF-8", swStream);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("RoyalEngine", "Failed to serve local Service Worker", e);
                    }
                }

                // ✅ الكود الجديد: حماية السجل وتفعيل الأوفلاين النيتيف
                if (!NetworkMonitor.isInternetAvailable(context) && request.isForMainFrame()) {
                    Log.i(TAG, "📴 Offline main-frame request intercepted. Triggering Native Offline UI.");
                    
                    // إبلاغ مدير الأوفلاين لإظهار الواجهة النيتيف فوراً (OfflineUIController)
                    OfflineStateManager.getInstance().setErrorPage(true, request.getUrl().toString());

                    // إرجاع استجابة هيكلية سليمة لمنع Chromium من التحول إلى about:blank أو chromewebdata
                    String cleanStub = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head><body style=\"background-color:transparent;\"></body></html>";
                    InputStream stubStream = new ByteArrayInputStream(cleanStub.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    return new WebResourceResponse("text/html", "UTF-8", 200, "OK", null, stubStream);
                }

                // =========================================================
                // 🔥 الجزء المحسَّن من shouldInterceptRequest (بدون قياس زمن أو تعديل أولوية)
                // =========================================================
                WebResourceResponse royalResponse = RoyalNetworkEngine.interceptRequest(request);

                if (royalResponse != null) {
                    return royalResponse;
                }

                return null; // ❌ لا نستخدم super.shouldInterceptRequest
            }

            // =========================================================
            // 🔥 [تعديل 4] shouldOverrideUrlLoading (الإصدار الجديد)
            // =========================================================
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                Uri uri = request.getUrl();

                if (!NetworkMonitor.isInternetAvailable(context)) {
                    if (isSameOrigin(uri)) {
                        // ❌ لا تحفظ الرابط ولا توقف التحميل
                        // ✅ فقط اهتز الشريط وأهمل النقر
                        OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                        return true; // تجاهل النقر تماماً
                    }
                }
                return handleUriLogic(uri, request.isForMainFrame());
            }

            // =========================================================
            // 🔥 [تعديل 5] shouldOverrideUrlLoading (الإصدار القديم)
            // =========================================================
            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) return false;
                Uri uri = Uri.parse(url);

                if (!NetworkMonitor.isInternetAvailable(context) && isSameOrigin(uri)) {
                    // ❌ لا تحفظ الرابط ولا توقف التحميل
                    // ✅ فقط اهتز الشريط وأهمل النقر
                    OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                    return true; // تجاهل النقر تماماً
                }
                return handleUriLogic(uri, true);
            }
        });

        webView.setWebChromeClient(capabilitiesEngine.buildChromeClient(progressBar));

        capabilitiesEngine.attachDownloadManager(webView);
    }

    // ==========================================
    // 🧠 محرك الروابط السيادي (معدل)
    // ==========================================
    private boolean handleUriLogic(Uri uri, boolean isMainFrame) {
        if (uri == null) return false;

        // ✅ إذا كنا أوفلاين → تجاهل كل النقرات فوراً
        if (!NetworkMonitor.isInternetAvailable(context)) {
            OfflineStateManager.getInstance().notifyOfflineClickAttempt();
            return true; // لا تنفذ أي رابط، لا داخلي ولا خارجي
        }

        String scheme = uri.getScheme();
        if (scheme == null) return false;
        scheme = scheme.toLowerCase();

        if (isSameOrigin(uri)) {
            return false;
        }

        switch (scheme) {
            case "tel":
            case "mailto":
            case "sms":
            case "smsto":
            case "geo":
            case "market":
            case "intent":
            case "whatsapp":
                return launchExternal(uri);
        }

        if ("http".equals(scheme) || "https".equals(scheme)) {
            return false;
        }

        return launchExternal(uri);
    }

    private boolean launchExternal(Uri uri) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                applyNativeExitTransition();
                context.startActivity(intent);
            } else {
                Log.w("RoyalEngine", "No Activity found for: " + uri);
            }
        } catch (Exception e) {
            Log.e("RoyalEngine", "External launch failed", e);
        }
        return true;
    }

    private void applyNativeExitTransition() {
        if (activity != null) {
            activity.runOnUiThread(() -> {
                activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }
    }

    private void syncStatusBarColor(WebView view) {
        if (activity == null || activity.isFinishing()) return;

        if (!NetworkMonitor.isInternetAvailable(context))
            return;

        String currentUrl = view.getUrl();
        if (currentUrl != null && currentUrl.startsWith("file:///android_asset/")) {
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
            SystemUI.setDynamicIcons(activity.getWindow(), true);
            return;
        }

        if (!view.isAttachedToWindow()) {
            return;
        }

        view.evaluateJavascript(
                "(function(){return window.getComputedStyle(document.body).backgroundColor;})();",
                value -> {
                    try {
                        if (value != null && value.contains("rgb")) {
                            String clean = value.replaceAll("[^0-9,]", "");
                            String[] parts = clean.split(",");
                            int r = Integer.parseInt(parts[0].trim());
                            int g = Integer.parseInt(parts[1].trim());
                            int b = Integer.parseInt(parts[2].trim());
                            int color = Color.rgb(r, g, b);

                            activity.getWindow().setStatusBarColor(color);
                            boolean isLight = SystemUI.isColorLight(color);
                            SystemUI.setDynamicIcons(activity.getWindow(), isLight);
                        }
                    } catch (Exception ignored) {}
                }
        );
    }

    private void setTrustedOrigin(String url) {

        if (url == null) return;

        Uri uri = Uri.parse(url);

        String scheme = uri.getScheme();
        String host = uri.getHost();

        if (scheme == null || host == null) {
            return;
        }

        trustedScheme = scheme.toLowerCase();
        trustedHost = host.toLowerCase();

        trustedPort =
                uri.getPort() == -1
                        ? ("https".equals(trustedScheme) ? 443 : 80)
                        : uri.getPort();

        Log.i(TAG,
                "🔒 Trusted Origin = "
                        + trustedScheme
                        + "://"
                        + trustedHost
                        + ":"
                        + trustedPort);
    }

    // =========================================================
    // 🔥 [تعديل 6] isSameOrigin() المُصحَّحة
    // =========================================================
    private boolean isSameOrigin(Uri uri) {
        if (uri == null) {
            return false;
        }

        if (trustedHost == null) {
            return false;
        }

        String targetHost = uri.getHost();

        if (targetHost == null) {
            return false;
        }

        targetHost = targetHost.toLowerCase();

        String trusted = trustedHost.toLowerCase();

        String targetScheme = uri.getScheme();

        int port = uri.getPort();

        if (port == -1) {
            port = "https".equals(targetScheme) ? 443 : 80;
        }

        // السماح بـ subdomains
        boolean hostMatches =
                trusted.equalsIgnoreCase(targetHost)
                        || targetHost.endsWith("." + trusted);

        return hostMatches
                && trustedScheme.equalsIgnoreCase(targetScheme)
                && trustedPort == port;
    }

    // ==============================
    // 🔒 Safe back navigation helper
    // ==============================
    /**
     * Attempts to navigate back to the nearest previous history entry that is a valid page.
     * Skips entries like about:blank, data: URIs, chromewebdata, or null URLs.
     * Returns true if navigation was performed, false if no safe entry found.
     */
    public boolean safeGoBack() {
        try {
            if (webView == null) return false;

            WebBackForwardList list = webView.copyBackForwardList();
            if (list == null) return false;

            int currentIndex = list.getCurrentIndex();
            // scan backwards for the first valid URL
            for (int i = currentIndex - 1; i >= 0; i--) {
                String candidate = list.getItemAtIndex(i).getUrl();
                if (candidate == null) continue;
                String lower = candidate.toLowerCase();
                if (lower.startsWith("about:") || lower.startsWith("data:") || lower.contains("chromewebdata")) {
                    continue; // skip invalid entries
                }
                final int steps = i - currentIndex; // negative value
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        try {
                            webView.goBackOrForward(steps);
                        } catch (Exception e) {
                            Log.w(TAG, "safeGoBack: goBackOrForward failed", e);
                        }
                    });
                } else {
                    // fallback: call on UI thread via webView
                    webView.post(() -> {
                        try {
                            webView.goBackOrForward(steps);
                        } catch (Exception e) {
                            Log.w(TAG, "safeGoBack: goBackOrForward failed (post)", e);
                        }
                    });
                }
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "safeGoBack: unexpected error", e);
        }
        return false;
    }

    /**
     * Returns true if the current WebView URL is the app's home/root URL.
     */
    public boolean isAtHomeUrl() {
        try {
            if (webView == null) return true;
            String url = webView.getUrl();
            if (url == null) return true;
            String home = com.store.app.BuildConfig.CLIENT_URL;
            return url.equalsIgnoreCase(home) || url.equalsIgnoreCase(home + "/") ;
        } catch (Exception e) {
            return true;
        }
    }

    // ==========================================
    // 🔥 دوال حالة الصفحة (تستخدم OfflineStateManager)
    // ==========================================
    public boolean isOnErrorPage() {
        return OfflineStateManager.getInstance().isOnErrorPage();
    }

    public boolean isPageValid() {
        return OfflineStateManager.getInstance().isPageValid();
    }

    // ==========================================
    // 🧹 دورة الحياة (destroy)
    // ==========================================
    public void destroy() {

        cancelActivePrerender();

        speculativeUrls.clear();

        webProfile = null;

        speculativeLoadingReady = false;

        Log.i(
                TAG,
                "🧹 WebEngineManager destroyed."
        );
    }
    }
