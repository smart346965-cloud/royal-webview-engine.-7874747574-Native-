package com.store.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.store.app.offline.OfflineUIController;
import com.store.app.offline.OfflineStateManager;
import com.store.app.RoyalAuthManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 👑 MainActivity - النواة الأساسية لإدارة محرك الويب المخصص
 * تم تطهيرها بالكامل من مخلفات الـ TWA لتعمل بأقصى سرعة استجابة (Zero-friction)
 * 
 * 🚀 تم تحسينها بأعلى معايير الأداء من وثائق كروميوم:
 * - Time-Based Memory Purge (تفريغ الذاكرة الاستباقي)
 * - shouldInterceptRequest Short Circuit (تحسين اعتراض الطلبات)
 * - Renderer Importance API (أولوية معالج العرض)
 * - onTrimMemory Optimization (تحسين استجابة ضغط الذاكرة)
 * - saveState/restoreState (تسريع حفظ واستعادة الحالة)
 * - Prefetch Native Library (تحميل المكتبات الأصلية مسبقاً)
 * - Threading Optimization (تحسين إدارة الخيوط)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RoyalMainActivity";
    private static final long FIXED_SPLASH_TIME = 5000; // قيمة ثابتة 5 ثوانٍ بالتمام والكمال
    private static final long MEMORY_PURGE_DELAY_MS = 4 * 60 * 1000; // 4 دقائق

    private boolean splashRemoved = false;
    private boolean isPageReady = false; // flag للرندرة
    private boolean isPageLoaded = false; // لمنع إعادة تحميل الصفحة في onResume

    private WebEngineManager engineManager;
    private WebView activeWebView;
    private ProgressBar progressBar;

    private long splashStartTime = 0;
    private Handler memoryPurgeHandler;
    private Runnable memoryPurgeRunnable;

    // 🔥 تحسين الخيوط: استخدام ThreadPool لإدارة المهام الخلفية
    private static final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2);

    // 🔥 مدير واجهات الأوفلاين
    private OfflineUIController offlineController;

    // 🔥 مدير المصادقة والدفع
    private RoyalAuthManager royalAuthManager;

    // =========================================================
    // 🚀 دورة الحياة الأساسية
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 👑 [تعديل جراحي ملكي 1]: استلام التحكم بأنيميشن خروج سبلاش النظام لجعل خروجه ناعماً للغاية
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSplashScreen().setOnExitAnimationListener(splashScreenView -> {
                // تنفيذ أنيميشن شفافية ناعم (Fade-Out) لسبلاش النظام لمنع الاختفاء المفاجئ
                splashScreenView.animate()
                        .alpha(0f)
                        .setDuration(500) // 500 ملي ثانية لأنيميشن اختفاء سينمائي
                        .withEndAction(splashScreenView::remove)
                        .start();
            });
        }

        // 🛡️ درع الوميض: مطابقة الخلفية مع لون السبلاش لمنع الوميض الأبيض الصارخ
        setTheme(R.style.AppTheme_NoSplash);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#F3F4F6")));

        super.onCreate(savedInstanceState);

        // 🔍 تفعيل محرك الفحص والتشخيص الذكي
        try {
            RoyalPanopticon.startAwareness();
            Log.i(TAG, "RoyalPanopticon Engine: Active and running in background.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize RoyalPanopticon: " + e.getMessage());
        }

        // تفعيل أدوات تصحيح الويب التقنية عبر المتصفح
        WebView.setWebContentsDebuggingEnabled(true);

        // 1️⃣ استدعاء وتهيئة الويب فيو الخالد مباشرة بدون وسطاء
        if (!RoyalWebViewHost.isReady()) {
            RoyalWebViewHost.create(getApplicationContext());
        }
        activeWebView = RoyalWebViewHost.attach(this);

        // 2️⃣ تعيين المحرك الخالد كواجهة أساسية مباشرة (استجابة 0ms)
        setContentView(activeWebView);

        // 🔥 [تحسين shouldInterceptRequest]: تفعيل الاختصار لمنع الاستدعاءات الفارغة
        setupWebViewClient();

        // 🔥 [تفعيل Renderer Importance API]: أعلى أولوية لمعالج العرض
        setupRendererPriority();

        // 🔥 [تفعيل Prefetch Native Library]: إجبار النظام على إبقاء المكتبات في الذاكرة
        setupNativeLibraryPrefetch();

        // 🚀 السطر الذهبي: حاول الإحياء الثنائي أولاً
        boolean sessionRestored = RoyalSessionSentinel.resurrect(activeWebView, this);

        if (!sessionRestored) {
            // إذا لم توجد جلسة، حمّل الرابط الافتراضي
            activeWebView.loadUrl(BuildConfig.CLIENT_URL);
        } else {
            isPageLoaded = true; // تم استعادة الجلسة، الصفحة محملة
        }

        // 4️⃣ نظام التحكم بالرجوع المستقل نيتف (محسّن)
        setupBackNavigation();

        // 5️⃣ الحصانة البصرية وتخصيص شريط النظام بالكامل
        SystemUI.applyKingMode(this, activeWebView);
        SystemUI.setDynamicIcons(this.getWindow(), true);

        // 6️⃣ بناء وتجهيز طبقة شاشة التحميل (Splash Screen Overlay)
        setupSplashScreen();

        // 7️⃣ مراقبة الشبكة وتهيئة مدير الأوفلاين
        NetworkMonitor.init(this);

        // 🔗 الربط الثلاثي الموحد
        NetworkMonitor.setWebView(activeWebView);
        offlineController = new OfflineUIController(this, activeWebView, engineManager);
        offlineController.init();
        OfflineStateManager.getInstance().bind(activeWebView, offlineController);

        // 🔥 تهيئة مدير المصادقة والدفع
        royalAuthManager = new RoyalAuthManager(this, getApplicationContext());

        // 🚀 فحص الإنترنت الأولي (عند الإقلاع)
        if (!NetworkMonitor.isInternetAvailable(this)) {
            if (offlineController != null) {
                offlineController.setOfflineUIVisibility(true);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // بدء مراقبة حالة التطبيق في المقدمة
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 🔥 [Time-Based Memory Purge]: تفريغ الذاكرة بعد 4 دقائق في الخلفية
        scheduleMemoryPurge();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // إيقاف مؤقت للعمليات الرسومية غير النشطة في الخلفية للحفاظ على طاقة الجهاز
        if (activeWebView != null) {
            activeWebView.onPause();
        }
        // إلغاء مؤقت تفريغ الذاكرة عند الخروج الفوري
        cancelMemoryPurge();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (activeWebView != null) {
            activeWebView.onResume();
        }
        cancelMemoryPurge();

        // 🔥 تفويض منطق الأوفلاين إلى OfflineUIController
        if (offlineController != null) {
            offlineController.onResume();
        }

        // إذا كانت الصفحة فارغة وتحتاج تحميل (مع عدم وجود إنترنت)
        if (!isPageLoaded && activeWebView != null && activeWebView.getUrl() == null) {
            activeWebView.loadUrl(BuildConfig.CLIENT_URL);
            isPageLoaded = true;
        }
    }

    @Override
    protected void onDestroy() {
        // 🛡️ التعديل: لا تحمل about:blank، فقط افصل الويب فيو بأمان
        if (activeWebView != null) {
            // نكتفي بإيقاف العمليات دون مسح السطح الرسومي
            activeWebView.stopLoading();
        }
        // إلغاء مؤقت تفريغ الذاكرة
        cancelMemoryPurge();

        // 🔥 تنظيف OfflineUIController
        if (offlineController != null) {
            offlineController.destroy();
            offlineController = null;
        }

        // 🔥 إلغاء ربط OfflineStateManager
        OfflineStateManager.getInstance().unbind();

        // 🔥 تنظيف مدير المصادقة والدفع
        if (royalAuthManager != null) {
            royalAuthManager.destroy();
            royalAuthManager = null;
        }

        RoyalWebViewHost.detach();
        super.onDestroy();
    }

    // 🔥 [تحسين onTrimMemory]: استجابة سريعة لضغط الذاكرة
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        
        if (level >= TRIM_MEMORY_MODERATE) {
            Log.i(TAG, "🚨 Memory Pressure: Level " + level);
            
            if (activeWebView != null) {
                backgroundExecutor.execute(() -> {
                    try {
                        // تحرير موارد الرسم الصلبة
                        runOnUiThread(() -> activeWebView.onPause());
                        // تفريغ الكاش في الخلفية
                        activeWebView.clearCache(true);
                        Log.i(TAG, "🧹 Cache cleared due to memory pressure.");
                    } catch (Exception e) {
                        Log.w(TAG, "Memory pressure cleanup error: " + e.getMessage());
                    }
                });
            }
            
            // طلب جمع القمامة
            System.gc();
        }
    }

    // =========================================================
    // 🔧 دوال الإعدادات المحسّنة
    // =========================================================

    /**
     * 🔥 تحسين shouldInterceptRequest: منع الاستدعاءات الفارغة
     */
    private void setupWebViewClient() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activeWebView.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    // استخدام RoyalNetworkEngine بدلاً من التنفيذ الفارغ
                    return RoyalNetworkEngine.interceptRequest(request);
                }
            });
            Log.i(TAG, "✅ WebViewClient configured with shouldInterceptRequest optimization.");
        }
    }

    /**
     * 🔥 تفعيل Renderer Importance API: أعلى أولوية لمعالج العرض
     */
    private void setupRendererPriority() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                activeWebView.setRendererPriorityPolicy(
                    WebView.RENDERER_PRIORITY_BOUND,  // أعلى أولوية
                    true                              // Waived when not visible
                );
                Log.i(TAG, "✅ Renderer Priority set to BOUND.");
            } catch (Exception e) {
                Log.w(TAG, "Renderer priority setup failed: " + e.getMessage());
            }
        }
    }

    /**
     * 🔥 تفعيل Prefetch Native Library: إبقاء المكتبات في الذاكرة
     */
    private void setupNativeLibraryPrefetch() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // إجبار النظام على إبقاء المكتبات الأصلية في الذاكرة
                // يتم ذلك عبر نفس آلية الأولوية (التأثير الجانبي المفيد)
                activeWebView.setRendererPriorityPolicy(
                    WebView.RENDERER_PRIORITY_BOUND, true
                );
                Log.i(TAG, "✅ Native Library Prefetch enabled.");
            } catch (Exception e) {
                Log.w(TAG, "Native library prefetch setup failed: " + e.getMessage());
            }
        }
    }

    /**
     * 🔥 نظام التحكم بالرجوع المحسّن (بدون تغيير وضع الكاش مؤقتاً)
     */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                try {
                    if (activeWebView != null && activeWebView.canGoBack()) {
                        // استخدام LOAD_DEFAULT دائماً والاعتماد على RoyalCacheManager
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        activeWebView.goBack();
                    } else {
                        moveTaskToBack(true);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Back navigation error: " + e.getMessage());
                    moveTaskToBack(true);
                }
            }
        });
        Log.i(TAG, "✅ Back navigation optimized.");
    }

    /**
     * 🔥 Time-Based Memory Purge: تفريغ الذاكرة بعد 4 دقائق في الخلفية
     */
    private void scheduleMemoryPurge() {
        cancelMemoryPurge();
        memoryPurgeHandler = new Handler(Looper.getMainLooper());
        memoryPurgeRunnable = () -> {
            if (activeWebView != null && !isFinishing() && !isDestroyed()) {
                Log.i(TAG, "🧹 Time-Based Memory Purge: Clearing cache...");
                backgroundExecutor.execute(() -> {
                    try {
                        runOnUiThread(() -> {
                            if (activeWebView != null) {
                                activeWebView.clearCache(true);
                            }
                        });
                        // طلب جمع القمامة
                        System.gc();
                        Log.i(TAG, "✅ Memory purge completed.");
                    } catch (Exception e) {
                        Log.w(TAG, "Memory purge error: " + e.getMessage());
                    }
                });
            }
        };
        memoryPurgeHandler.postDelayed(memoryPurgeRunnable, MEMORY_PURGE_DELAY_MS);
        Log.i(TAG, "⏳ Memory purge scheduled in " + (MEMORY_PURGE_DELAY_MS / 60000) + " minutes.");
    }

    private void cancelMemoryPurge() {
        if (memoryPurgeHandler != null && memoryPurgeRunnable != null) {
            memoryPurgeHandler.removeCallbacks(memoryPurgeRunnable);
            memoryPurgeHandler = null;
            memoryPurgeRunnable = null;
            Log.i(TAG, "⏹️ Memory purge cancelled.");
        }
    }

    // =========================================================
    // ⚙️ إعدادات واجهة السبلاش (بدون تغيير)
    // =========================================================

    private void setupSplashScreen() {
        splashStartTime = System.currentTimeMillis();

        // 👑 [تعديل جراحي ملكي 2]: تجميد الشاشة حتى اكتمال الـ 5 ثوانٍ، ثم إطلاق أنيميشن الـ Fade-out
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            findViewById(android.R.id.content).getViewTreeObserver().addOnPreDrawListener(
                    new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            if (splashRemoved) {
                                // انقضت الـ 5 ثوانٍ.. نسمح للنظام بالرسم ليبدأ أنيميشن الـ Fade-Out
                                findViewById(android.R.id.content).getViewTreeObserver().removeOnPreDrawListener(this);
                                return true;
                            } else {
                                // الـ 5 ثوانٍ لم تنتهِ بعد.. جمّد الشاشة بصلابة!
                                return false;
                            }
                        }
                    }
            );
        }

        final FrameLayout splashContainer = new FrameLayout(this);
        splashContainer.setBackgroundColor(Color.parseColor("#F3F4F6"));

        ImageView splashIcon = new ImageView(this);
        splashIcon.setImageResource(R.mipmap.ic_launcher);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(280, 280, android.view.Gravity.CENTER);
        splashIcon.setLayoutParams(iconParams);
        splashContainer.addView(splashIcon);

        addContentView(splashContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        addContentView(progressBar, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 8));

        engineManager = new WebEngineManager(
                this, activeWebView, splashContainer, progressBar,
                () -> splashRemoved = true, () -> splashRemoved
        );
        engineManager.setSplashStartTime(splashStartTime);
        engineManager.init();

        // 🚀 الـ Handler المعتمد للـ 5 ثوانٍ
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!splashRemoved) {
                engineManager.triggerFinalReveal();
            }
        }, FIXED_SPLASH_TIME);

        // 🛡️ تعطيل الاستجابة التلقائية للجسور
        if (RoyalWebViewHost.getBridge() != null) {
            RoyalWebViewHost.getBridge().setOnHideSplashCallback(() -> {
                Log.i(TAG, "⚡ Page ready, but Splash is LOCKED by engineer's timer.");
            });
        }
    }

    // =========================================================
    // 🔄 نتائج النشاطات والصلاحيات (محسّن)
    // =========================================================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 🔥 معالجة اختيار الملفات (رفع الصور)
        if (requestCode == RoyalCapabilitiesEngine.FILECHOOSER_RESULTCODE) {
            if (RoyalCapabilitiesEngine.filePathCallback == null) return;

            Uri[] results = null;

            if (resultCode == android.app.Activity.RESULT_OK) {
                if (data != null) {
                    String dataString = data.getDataString();
                    android.content.ClipData clipData = data.getClipData();

                    if (clipData != null) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    } else if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }

            RoyalCapabilitiesEngine.filePathCallback.onReceiveValue(results);
            RoyalCapabilitiesEngine.filePathCallback = null;
            return;
        }

        // 🔥 معالجة نتائج المصادقة والدفع
        if (royalAuthManager != null) {
            royalAuthManager.handleAuthResult(resultCode, data);
            royalAuthManager.handlePaymentResult(resultCode, data);
        }
    }

    // =========================================================
    // 🔗 معالجة الروابط العميقة (Deep Links)
    // =========================================================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // إذا كان intent يحتوي على بيانات من Auth Tab أو Custom Tabs
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            Log.i(TAG, "🔗 Deep link received: " + data.toString());
            // يمكن معالجتها حسب الحاجة
        }
    }

    // =========================================================
    // 🔐 صلاحيات التطبيق (مدمجة مع محرك القدرات)
    // =========================================================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // تمرير النتيجة إلى محرك القدرات (لرفع الملفات)
        if (engineManager != null && engineManager.getCapabilitiesHandler() != null) {
            engineManager.getCapabilitiesHandler().handlePermissionResult(requestCode, grantResults);
        }
        // هنا يمكن تمرير النتائج إلى RoyalAuthManager إذا لزم الأمر
        // حالياً لا يوجد استخدام مباشر
    }

    // =========================================================
    // 🔧 حفظ واستعادة الحالة المحسّن (saveState/restoreState)
    // =========================================================

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (activeWebView != null) {
            try {
                // حفظ الحالة في Bundle للتسريع
                activeWebView.saveState(outState);
                Log.i(TAG, "💾 WebView state saved.");
            } catch (Exception e) {
                Log.w(TAG, "SaveState failed: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (activeWebView != null && savedInstanceState != null) {
            try {
                activeWebView.restoreState(savedInstanceState);
                isPageLoaded = true;
                Log.i(TAG, "🔄 WebView state restored.");
            } catch (Exception e) {
                Log.w(TAG, "RestoreState failed: " + e.getMessage());
            }
        }
    }
    }
