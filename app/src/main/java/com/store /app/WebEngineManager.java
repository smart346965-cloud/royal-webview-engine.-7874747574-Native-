package com.store.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
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
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.store.app.offline.OfflineStateManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

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

    public RoyalCapabilitiesEngine getCapabilitiesHandler() {
        return this.capabilitiesEngine;
    }

    public void setSplashStartTime(long startTime) {
        this.splashStartTime = startTime;
    }

    public void init() {
        if (RoyalWebViewHost.isReady() && webView.getUrl() != null && !webView.getUrl().equals("about:blank")) {
            android.util.Log.i("RoyalEngine", "🔥 Warm Resume Detected, but enforcing fixed splash time.");
        }

        configureSettings();

        attachClients();

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
                    Log.i("Performance", "🚀 Navigation started");
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

    public void triggerFinalReveal() {
        if (splashChecker.isRemoved()) return;

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - splashStartTime;
        
        long remaining = Math.max(0, 5000 - elapsed);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!splashChecker.isRemoved()) {
                removeSplashSmoothly();
                Log.i("RoyalEngine", "👑 Time's up! Fixed Splash Released.");
            }
        }, remaining);
    }

    private void configureSettings() {
        WebSettings settings = webView.getSettings();

        settings.setEnableSmoothTransition(true); 
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.setVerticalScrollbarThumbDrawable(null);
        }

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.setForceDarkAllowed(false);
        }

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

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true);
        }

        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
        
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
    }

    private void attachClients() {
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                RoyalPanopticon.recordRequestSent();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                RoyalPanopticon.recordNavigationComplete();
                RoyalNetworkEngine.notifyRenderIdle();
                
                if (url != null && !url.startsWith("data:") && !url.startsWith("about:") && !url.contains("chromewebdata")) {
                    OfflineStateManager.getInstance().setPageValid(true);
                    Log.i(TAG, "✅ Page finished successfully. Page is valid.");
                } else {
                    OfflineStateManager.getInstance().setPageValid(false);
                    Log.w(TAG, "⚠️ Page finished but URL is invalid or error page.");
                }
            }

            // =========================================================
            // 🔥 [تعديل جراحي مطلوب] onPageCommitVisible
            // =========================================================
            @Override
            public void onPageCommitVisible(WebView view, String url) {
                // 🎯 الإشارة الذهبية: الموقع أصبح جاهزاً خلف الدرع، الآن نزيحه
                if (NetworkMonitor.isInternetAvailable(context)) {
                    OfflineStateManager.getInstance().notifyPageReadyToHide();
                }

                // إبلاغ OfflineStateManager بأن الصفحة جاهزة للإخفاء
                if (capabilitiesEngine != null) OfflineStateManager.getInstance().notifyPageReadyToHide();

                // 🚀 [الحل العبقري]: الإنترنت عاد والموقع بدأ بالظهور فعلياً
                // الآن فقط نخفي واجهة الأوفلاين الكبيرة ليكون الانتقال 0ms بياض
                if (OfflineStateManager.getInstance().isNetworkAvailable()) {
                    OfflineStateManager.getInstance().setPageValid(true);
                    if (activity != null) {
                        activity.runOnUiThread(() -> {
                            // إخفاء الدرع الناتيف الكبير الآن فقط
                            // يتم التعامل معه عبر OfflineStateManager المتصل بـ OfflineUIController
                        });
                    }
                }

                // باقي الكود الحالي
                if (url != null && !url.startsWith("data:") && !url.startsWith("about:") && !url.contains("chromewebdata")) {
                    OfflineStateManager.getInstance().setPageValid(true);
                    Log.i(TAG, "✅ Page committed successfully. Page is valid.");
                } else {
                    OfflineStateManager.getInstance().setPageValid(false);
                    Log.w(TAG, "⚠️ Page commit but URL is invalid or error page.");
                }

                if (trustedHost == null && url != null) {
                    setTrustedOrigin(url);
                }

                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        WebEnhancer.apply(view, context);
                    });
                }

                RoyalNetworkEngine.notifyRenderStart();
                syncStatusBarColor(view);
                Log.i("RoyalEngine", "🎨 Page Committed. Content is ready.");
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                android.util.Log.e("RoyalEngine", "☠️ FATAL: Chromium Renderer crashed! Auto-Recovery...");
                RoyalNetworkEngine.notifyRenderIdle();
                RoyalWebViewHost.destroy();
                if (activity != null) {
                    RoyalWebViewHost.create(activity.getApplicationContext());
                    activity.recreate();
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    view.stopLoading();
                    OfflineStateManager.getInstance().setErrorPage(true, request.getUrl().toString());
                    Log.w(TAG, "🛡️ Main frame error detected. Page invalid.");
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                OfflineStateManager.getInstance().setErrorPage(true, failingUrl);
                Log.w(TAG, "🛡️ Legacy main frame error detected. Page invalid.");
            }

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

                // 🔥 [تعديل جراحي] معالجة الأوفلاين مع الكاش أولاً
                if (!NetworkMonitor.isInternetAvailable(context) && request.isForMainFrame()) {
                    // 🏗️ محاولة السحب من المستودع الملكي (Royal Vault)
                    WebResourceResponse vaultResponse = RoyalCacheManager.intercept(request);
                    if (vaultResponse != null) return vaultResponse;

                    // ❌ لا ترجع Null أبداً هنا لأنه يطلق الصفحة البيضاء
                    // ✅ الحل: إذا لم نجد في الكاش، نطلب من المحرك الصمت والبقاء في مكانه
                    return new WebResourceResponse("text/plain", "UTF-8", null); 
                }

                boolean isCoreResource = request.isForMainFrame() || url.contains(".js") || url.contains(".css") || url.contains(".wasm");
                if (isCoreResource) {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
                }

                long startIntercept = System.currentTimeMillis();
                WebResourceResponse royalResponse = RoyalNetworkEngine.interceptRequest(request);
                long duration = System.currentTimeMillis() - startIntercept;
                
                RoyalPanopticon.recordExecution("NetworkInterceptor", duration, true, 0);

                if (royalResponse != null) {
                    return royalResponse;
                }
                
                return super.shouldInterceptRequest(view, request);
            }

            // =========================================================
            // 🔥 [تعديل جراحي مطلوب] shouldOverrideUrlLoading
            // =========================================================
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                Uri uri = request.getUrl();

                // 🛡️ قفل الأوفلاين الحتمي: منع المغادرة لأي رابط داخلي إذا انقطع النت
                if (!NetworkMonitor.isInternetAvailable(context)) {
                    if (isSameOrigin(uri)) {
                        // 🛡️ قفل "الحصانة": أوقف المحرك فوراً قبل أن يفرغ الذاكرة الرسومية
                        view.stopLoading(); 
                        OfflineStateManager.getInstance().notifyOfflineClickAttempt();
                        return true; 
                    }
                }

                return handleUriLogic(uri, request.isForMainFrame());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUriLogic(Uri.parse(url), true);
            }
        });

        webView.setWebChromeClient(capabilitiesEngine.buildChromeClient(progressBar));

        capabilitiesEngine.attachDownloadManager(webView);
    }

    // ==========================================
    // 🧠 محرك الروابط السيادي
    // ==========================================
    private boolean handleUriLogic(Uri uri, boolean isMainFrame) {

        if (uri == null) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }

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
        Uri uri = Uri.parse(url);
        trustedScheme = uri.getScheme();
        trustedHost = uri.getHost();
        trustedPort = uri.getPort() == -1 ? (trustedScheme.equals("https") ? 443 : 80) : uri.getPort();
    }

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

        return trusted.equalsIgnoreCase(targetHost)
                || targetHost.endsWith("." + trusted)
                && trustedScheme.equalsIgnoreCase(targetScheme)
                && trustedPort == port;
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
            }
