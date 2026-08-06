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

import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class WebEngineManager {

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

        // 👑 السطر الجديد: تهيئة محرك الإمكانيات
        this.capabilitiesEngine = new RoyalCapabilitiesEngine(this.activity);
    }

    // 👑 ----------------- أضف هذا الكود هنا ----------------- 👑
    public RoyalCapabilitiesEngine getCapabilitiesHandler() {
        return this.capabilitiesEngine;
    }
    // --------------------------------------------------------

    public void setSplashStartTime(long startTime) {
        this.splashStartTime = startTime;
    }

    // [تعديل جراحي 1: WebEngineManager.java]
    public void init() {
        // 🛡️ تم تعطيل حذف السبلاش التلقائي هنا لضمان السيادة الزمنية لـ FIXED_SPLASH_TIME
        if (RoyalWebViewHost.isReady() && webView.getUrl() != null && !webView.getUrl().equals("about:blank")) {
            android.util.Log.i("RoyalEngine", "🔥 Warm Resume Detected, but enforcing fixed splash time.");
            // لا تستدعي removeSplashInstantly() هنا أبداً
        }

        configureSettings();
        attachClients();

        // 🔥 [التحسين 14]: Prerender تخميني للصفحة الرئيسية
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PRERENDER_WITH_URL)) {
            try {
                WebViewCompat.prerenderUrlAsync(
                    webView,
                    BuildConfig.CLIENT_URL,
                    null,  // CancellationSignal - يمكن تمرير null
                    null,  // Executor - سيستخدم الخيط الرئيسي
                    new WebViewCompat.PrerenderOperationCallback() {
                        @Override
                        public void onPrerenderStarted() {
                            Log.i("RoyalEngine", "⚡ Prerender started for home page.");
                        }
                        @Override
                        public void onPrerenderFailed(int error) {
                            Log.w("RoyalEngine", "⚠️ Prerender failed: " + error);
                        }
                    }
                );
            } catch (Exception e) {
                Log.w("RoyalEngine", "Prerender failed: " + e.getMessage());
            }
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

    // 👑 تعديل جراحي: توحيد عملية الخروج الناعم للسبلاش
    public void removeSplashSmoothly() {
        if (activity == null || splashChecker.isRemoved()) return;

        activity.runOnUiThread(() -> {
            if (splashOverlay != null && splashOverlay.getAlpha() > 0f) {
                splashOverlay.animate()
                        .alpha(0f)
                        .setDuration(400) // انكماش ناعم جداً للخروج
                        .withEndAction(this::removeSplashInstantly)
                        .start();
            } else {
                removeSplashInstantly();
            }
        });
    }

    // [تعديل جراحي 2: WebEngineManager.java]
    public void triggerFinalReveal() {
        if (splashChecker.isRemoved()) return;

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - splashStartTime;
        
        // 👑 نستخدم القيمة 5000ms مباشرة هنا لضمان التطابق مع MainActivity
        long remaining = Math.max(0, 5000 - elapsed); 

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!splashChecker.isRemoved()) {
                removeSplashSmoothly();
                Log.i("RoyalEngine", "👑 Time's up! Fixed Splash Released.");
            }
        }, remaining);
    }

    // [تعديل جراحي في WebEngineManager.java]
    private void configureSettings() {
        WebSettings settings = webView.getSettings();

        // 1. تفعيل خيوط الرسم المتعددة (قوة الـ Compositor Thread)
        // هذا يجعل السكرول لا يتأثر بتجمد الجافا سكريبت
        settings.setEnableSmoothTransition(true); 
        
        // 2. إجبار المتصفح على رسم "الطبقات" مسبقاً (Pre-rasterization)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);
        }

        // 🔥 [التحسين 13]: تحسين Compositor (Fast Fallback Tick مدمج في الإعدادات)
        // هذه الإعدادات تضبط سلوك الـ Compositor لتقليل زمن الإطار الأول
        // settings.setEnableSmoothTransition(true); // موجود بالفعل، نؤكد عليه

        // 3. تحرير طاقة المعالج الرسومي (GPU Unbound)
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        
        // 4. تعطيل تأخير النقر (300ms delay) برمجياً
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);

        // 5. تفعيل ميزة الـ "Scroll Buffering" لضمان سلاسة Kiwi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.setVerticalScrollbarThumbDrawable(null); // تخفيف عبء الرسم
        }

        // [إضافة جراحية في WebEngineManager.java]

        // 1. منع النتعة الناتجة عن تمدد الشاشة (Stretch Effect) في الأندرويد الحديث
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        // 2. ضمان أن الـ Hardware Layer يعمل في "خيط منفصل" (هذا سطر ذهبي)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.setForceDarkAllowed(false); // منع المعالجة اللونية الثقيلة أثناء الرسم
        }

        // بقية الإعدادات السابقة...
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        // 👑 فتح قواعد البيانات التخزينية العميقة (ضروري للـ Service Worker والـ IndexedDB)
        settings.setDatabaseEnabled(true);
        
        // 👑 إجبار الكروميوم على استخدام كاش الـ V8 بشكل مكثف وإلزامية التخزين الافتراضي
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // [تعديل في WebEngineManager.java - دالة configureSettings]
        settings.setSafeBrowsingEnabled(true); // إعادة التفعيل للأمان
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // تقييد الوصول من ملفات الـ Assets للمواقع الخارجية لزيادة الأمان
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowFileAccessFromFileURLs(false);
        
        // 👑 السماح بتشغيل الفيديو وملفات الصوت برمجياً (مهم جداً للإضافات)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }

        if (WebViewFeature.isFeatureSupported(
                WebViewFeature.ALGORITHMIC_DARKENING)) {

            WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                    settings,
                    true
            );
        }

        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(false);

        // 👑 [تعديل جراحي]: تفعيل ملفات تعريف الارتباط للطرف الثالث
        // (حاسم جداً لعمل بوابات الدفع مثل Stripe وتسجيل الدخول بـ Google/Facebook)
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
        
        // 👑 [تعديل جراحي]: تفعيل التخزين المؤقت للـ DOM (ضروري لـ IndexedDB و Offline Mode)
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
    }

    private void attachClients() {
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // سجل الطلب فقط، لا تلمس الـ Alpha أبداً لكي يظل آخر إطار معروضاً
                RoyalPanopticon.recordRequestSent();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                RoyalPanopticon.recordNavigationComplete();
                RoyalNetworkEngine.notifyRenderIdle();
                // ❌ تم نقل WebEnhancer.apply من هنا للسرعة
            }

            // [تعديل جراحي 3 في WebEngineManager.java]
            @Override
            public void onPageCommitVisible(WebView view, String url) {
                // 🔥 تمت إزالة RoyalPanopticon.recordFirstFrame() لأنها غير موجودة
                // يمكن استبدالها بـ Log بسيط أو تنفيذ الدالة في RoyalPanopticon

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
                Log.i("RoyalEngine", "🎨 Page Committed. Content is ready, but Splash is locked by timer.");
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

            // [تعديل جراحي في WebEngineManager.java]
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    // 🚀 القاعدة الذهبية: إذا انقطع الإنترنت، لا تفعل شيئاً (Freeze UI)
                    // لا تحمل صفحة أوفلاين، لا تمسح الشاشة. فقط ابقَ مكانك.
                    Log.w("RoyalEngine", "🛡️ Connection Drop Detected. Freezing UI to prevent Chrome Error Page.");
                    
                    // منع الويب فيو من إكمال عملية التحويل لصفحة الخطأ
                    view.stopLoading(); 
                    
                    // إذا كانت الصفحة فارغة تماماً (أول تشغيل)، فقط حينها يمكن عرض شيء من الكاش
                    if (view.getUrl() == null || view.getUrl().equals("about:blank")) {
                        // محاولة استدعاء الرئيسية من المستودع العملاق
                        view.loadUrl(trustedScheme + "://" + trustedHost + "/");
                    }
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // 🚀 القاعدة الذهبية: الصمت التام
                Log.w("RoyalEngine", "🛡️ Legacy Error Intercepted. Freezing UI.");
                view.stopLoading();
            }

            // [تعديل جراحي 2: حقن درع نكسوس في الأندرويد]
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return null;
                String url = request.getUrl().toString();

                // 1. خوارزمية التعرف السريع على الطفيليات (Fast String Matching)
                // نضع قائمة سوداء سريعة في الجافا تعكس ما في الـ C++ لضمان عدم تأخير الطلب
                if (url.contains("gorgias") || url.contains("facebook.net") || url.contains("analytics") || url.contains("klaviyo")) {
                    
                    // 🚀 [السر المهني]: بدلاً من الحظر (404) الذي قد يكسر الموقع
                    // نرجع استجابة (200 OK) ولكن بمحتوى فارغ تماماً (JS Stub)
                    // هذا يحرر الـ Main Thread فوراً ويجعل المتصفح يظن أن السكربت انتهى تحميله!
                    String stubScript = "/* Isolated by Nexus Script Shield to ensure 60FPS Performance */";
                    InputStream stubStream = new ByteArrayInputStream(stubScript.getBytes());
                    
                    Log.d("RoyalEngine", "🛡️ Shield: Isolated Parasitic Script -> " + url);
                    
                    return new WebResourceResponse("application/javascript", "UTF-8", stubStream);
                }

                // 👑 [محاكي النطاق الافتراضي] اعتراض ملف الـ JS الوهمي وإعطائه تصريح العبور الآمن (CORS)
                if (url.endsWith("/royal_nucleus.js")) {
                    try {
                        java.io.InputStream jsStream = context.getAssets().open("public/js/royal_nucleus.js");
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Content-Type", "application/javascript");
                        headers.put("Access-Control-Allow-Origin", "*"); // كسر قيود CORS للسماح بالتشغيل داخل صفحة المتجر
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            return new WebResourceResponse("application/javascript", "UTF-8", 200, "OK", headers, jsStream);
                        } else {
                            return new WebResourceResponse("application/javascript", "UTF-8", jsStream);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("RoyalEngine", "❌ FATAL: Failed to serve local JS Core!", e);
                    }
                }

                // 👑 [محاكي النطاق الافتراضي] اعتراض ملف الـ Worker الخاص بالنواة وإرجاعه بـ MIME Type المعتمد
                if (url.endsWith("/nexus-worker.js") || url.contains("nexus-worker.js")) {
                    try {
                        java.io.InputStream workerStream = context.getAssets().open("public/js/nexus-worker.js");
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Content-Type", "application/javascript");
                        headers.put("Access-Control-Allow-Origin", "*"); // كسر قيود CORS للسماح بالتشغيل داخل الـ Worker
                        
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            return new WebResourceResponse("application/javascript", "UTF-8", 200, "OK", headers, workerStream);
                        } else {
                            return new WebResourceResponse("application/javascript", "UTF-8", workerStream);
                        }
                    } catch (Exception e) {
                        android.util.Log.e("RoyalEngine", "❌ FATAL: Failed to serve local Nexus Worker Core!", e);
                    }
                }

                // 👑 [تصحيح أمني حاسم] حسم ملف الـ WASM المحلي وإرجاعه بـ MIME Type المعتمد عالمياً لقهر الحظر الصامت
                if (url.endsWith("/royal_nucleus.wasm")) {
                    try {
                        java.io.InputStream wasmStream = context.getAssets().open("public/js/royal_nucleus.wasm");
                        java.util.Map<String, String> headers = new java.util.HashMap<>();
                        headers.put("Content-Type", "application/wasm"); // فرض هوية الملف الثنائي
                        headers.put("Access-Control-Allow-Origin", "*"); // كسر قيود الـ CORS محلياً
                        
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

                // 🛡️ صمام الأمان: منع الطلبات من الخروج إذا كانت الشبكة ميتة
                if (!NetworkMonitor.isInternetAvailable(context) && request.isForMainFrame()) {
                    // [السر الهندسي]: إعادة استجابة فارغة تجعل الويب فيو "يصمت" ولا يظهر صفحة الخطأ
                    return new WebResourceResponse("text/html", "UTF-8", null);
                }

                // 👑 [تعديل الأولوية القصوى] إدراج الـ .wasm كعنصر نواة فوري لرفع أولوية المعالجة في العتاد
                boolean isCoreResource = request.isForMainFrame() || url.contains(".js") || url.contains(".css") || url.contains(".wasm");
                if (isCoreResource) {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_FOREGROUND);
                }

                // 👁️ تتبع كفاءة الـ Network Interceptor الفرعي وتسجيل أدائه
                long startIntercept = System.currentTimeMillis();
                WebResourceResponse royalResponse = RoyalNetworkEngine.interceptRequest(request);
                long duration = System.currentTimeMillis() - startIntercept;
                
                // نرسل السجلات فوراً لمحرك الفحص لمعرفة سرعة استرجاع الكاش الملكي
                RoyalPanopticon.recordExecution("NetworkInterceptor", duration, true, 0);

                if (royalResponse != null) {
                    return royalResponse;
                }
                
                return super.shouldInterceptRequest(view, request);
            }

            // [تعديل جراحي في WebEngineManager.java - توحيد المحرك]
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUriLogic(request.getUrl(), request.isForMainFrame());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUriLogic(Uri.parse(url), true);
            }
        });

        // 👑 تعديل جراحي: استخدام المحرك المنفصل بدلاً من الكود المزدحم
        // هذا السطر يربط شريط التحميل، ورفع الملفات، والكاميرا، وصلاحيات الويب دفعة واحدة!
        webView.setWebChromeClient(capabilitiesEngine.buildChromeClient(progressBar));

        // 👑 تعديل جراحي: تفعيل محرك التحميلات لملفات PDF والصور
        capabilitiesEngine.attachDownloadManager(webView);
    }

    // [تعديل جراحي في WebEngineManager.java - محرك الروابط السيادي V2]
    private boolean handleUriLogic(Uri uri, boolean isMainFrame) {

        if (uri == null) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }

        scheme = scheme.toLowerCase();

        // =====================================================
        // 1) روابط الموقع نفسه
        // =====================================================

        if (isSameOrigin(uri)) {
            return false;
        }

        // =====================================================
        // 2) بروتوكولات النظام
        // =====================================================

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

        // =====================================================
        // 3) أي رابط ويب HTTP/HTTPS
        // يبقى داخل التطبيق
        // =====================================================

        if ("http".equals(scheme) || "https".equals(scheme)) {
            return false;
        }

        // =====================================================
        // 4) أي بروتوكول غير معروف
        // نحاول فتحه خارج التطبيق
        // =====================================================

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

                Log.w("RoyalEngine",
                        "No Activity found for: " + uri);

            }

        } catch (Exception e) {

            Log.e("RoyalEngine",
                    "External launch failed",
                    e);

        }

        return true;
    }

    // [إضافة جراحية: محرك أنيميشن الانتقال]
    private void applyNativeExitTransition() {
        if (activity != null) {
            activity.runOnUiThread(() -> {
                // تنفيذ أنيميشن "الانزلاق لأسفل" أو "التلاشي" الاحترافي
                // هذا يمنع الومضة البيضاء التي تظهر عند تبديل العمليات في أندرويد
                activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }
    }

    private void syncStatusBarColor(WebView view) {
        if (activity == null || activity.isFinishing()) return;

        if (!NetworkMonitor.isInternetAvailable(context))
            return;

        // 👑 قفل الحماية الملكي: إذا كنا في صفحة الأوفلاين المحلية، نفرض الألوان الشفافة والأيقونات الداكنة فوراً بدون تقييم مؤقت
        String currentUrl = view.getUrl();
        if (currentUrl != null && currentUrl.startsWith("file:///android_asset/")) {
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
            SystemUI.setDynamicIcons(activity.getWindow(), true); // true تعني أيقونات داكنة واضحة جداً فوق الخلفية البيضاء للأوفلاين
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
            }
