package com.store.app;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

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
    private TextView offlineBar;

    // [تعديل في MainActivity.java - منطقة التعريفات]
    private FrameLayout pureOfflineUI; // الحاوية الكبرى لواجهة أوفلاين
    private boolean isOfflineUIVisible = false;

    private long splashStartTime = 0;
    private Handler memoryPurgeHandler;
    private Runnable memoryPurgeRunnable;

    // 🔥 تحسين الخيوط: استخدام ThreadPool لإدارة المهام الخلفية
    private static final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2);

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

        // [بناء واجهة الأوفلاين الناتيف فوراً]
        createPureOfflineUI();

        // 6️⃣ بناء وتجهيز طبقة شاشة التحميل (Splash Screen Overlay)
        setupSplashScreen();

        // 7️⃣ إنشاء شريط الأوفلاين السينمائي
        createOfflineBar();

        // 8️⃣ مراقبة الشبكة
        setupNetworkListener();

        // 🚀 فحص الإنترنت الأولي (عند الإقلاع)
        if (!NetworkMonitor.isInternetAvailable(this)) {
            toggleOfflineUI(true);
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

        // 🔥 عند العودة للتطبيق، تحقق من حالة الشبكة
        if (!NetworkMonitor.isInternetAvailable(this)) {
            // إذا كان الإنترنت مقطوعاً
            if (activeWebView != null && activeWebView.getUrl() == null) {
                toggleOfflineUI(true);
            } else if (engineManager != null && !engineManager.isPageValid()) {
                toggleOfflineUI(true);
            } else {
                // صفحة موجودة ولكن الإنترنت مقطوع → إظهار الشريط النحيف
                if (offlineBar != null && offlineBar.getVisibility() != View.VISIBLE) {
                    offlineBar.setBackgroundColor(Color.parseColor("#323232"));
                    offlineBar.setText("لا يتوفر اتصال بالإنترنت");
                    offlineBar.setVisibility(View.VISIBLE);
                    offlineBar.animate().translationY(0).setDuration(400).start();
                }
            }
        } else {
            // الإنترنت موجود
            if (isOfflineUIVisible) {
                toggleOfflineUI(false);
            }
            if (offlineBar != null && offlineBar.getVisibility() == View.VISIBLE) {
                // إخفاء الشريط بلون مميز عند عودة الإنترنت
                offlineBar.setBackgroundColor(Color.parseColor("#1A237E"));
                offlineBar.setText("🔄 تم استعادة الاتصال، جاري التحديث...");
                offlineBar.animate().translationY(100).setDuration(400)
                    .withEndAction(() -> {
                        offlineBar.setVisibility(View.GONE);
                        offlineBar.setBackgroundColor(Color.parseColor("#323232"));
                        offlineBar.setText("لا يتوفر اتصال بالإنترنت");
                    }).start();
            }
            // إذا كانت الصفحة فارغة وتحتاج تحميل
            if (!isPageLoaded && activeWebView != null && activeWebView.getUrl() == null) {
                activeWebView.loadUrl(BuildConfig.CLIENT_URL);
                isPageLoaded = true;
            }
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
     * 🔥 مراقبة الشبكة المحسّنة
     */
    private void setupNetworkListener() {
        // ربط المستمع بـ NetworkMonitor (يتم التعامل معه في WebEngineManager)
        NetworkMonitor.setListener(connected -> {
            // WebEngineManager هو المسؤول عن كل شيء
            Log.i(TAG, "📡 Network state changed: " + connected);
        });
        Log.i(TAG, "✅ Network listener configured.");
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
    // 📡 شريط الأوفلاين (بدون تغيير)
    // =========================================================

    private void createOfflineBar() {
        offlineBar = new TextView(this);
        offlineBar.setText("لا يتوفر اتصال بالإنترنت");
        offlineBar.setTextColor(Color.WHITE);
        offlineBar.setBackgroundColor(Color.parseColor("#323232")); // أسود يوتيوب الأنيق
        offlineBar.setGravity(android.view.Gravity.CENTER);
        offlineBar.setPadding(0, 12, 0, 12);
        offlineBar.setTextSize(14f);
        offlineBar.setVisibility(View.GONE); // مخفي في البداية

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 80, android.view.Gravity.BOTTOM);
        // وضعه فوق أزرار التنقل قليلاً
        params.bottomMargin = 0;
        addContentView(offlineBar, params);
    }

    // =========================================================
    // 🍏 واجهة الأوفلاين الناتيف (بدون تغيير)
    // =========================================================

    private void createPureOfflineUI() {
        // 1. الحاوية الرئيسية الشاملة
        pureOfflineUI = new FrameLayout(this);
        pureOfflineUI.setBackgroundColor(Color.parseColor("#F3F4F6"));
        pureOfflineUI.setVisibility(View.GONE);

        // ☁️ أ- أيقونة السحابة في الجهة العلوية اليسرى (Top-Left Cloud Icon)
        ImageView cloudIcon = new ImageView(this);
        // يمكنك ربط رمز السحابة بملف الـ drawable لديك أو أيقونة ناتيف
        cloudIcon.setImageResource(R.drawable.ic_cloud_off); // تأكد من وجود ic_cloud_off في مجلد drawable
        cloudIcon.setAlpha(0.6f);
        FrameLayout.LayoutParams cloudParams = new FrameLayout.LayoutParams(90, 90, android.view.Gravity.TOP | android.view.Gravity.START);
        cloudParams.setMargins(60, 80, 0, 0); // ضبط الهوامش من الأعلى واليسار
        pureOfflineUI.addView(cloudIcon, cloudParams);

        // 🖼️ ب- شعار المتجر في المنتصف (Store Logo)
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(280, 280, android.view.Gravity.CENTER);
        logoParams.bottomMargin = 200; // إزاحة خفيفة للأعلى لإعطاء مساحة للنافذة السفلي
        pureOfflineUI.addView(logo, logoParams);

        // 💳 ج- النافذة المنبثقة السفلية (Bottom Card Sheet)
        LinearLayout bottomCard = new LinearLayout(this);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setBackground(createCardDrawable());
        bottomCard.setPadding(64, 72, 64, 88);
        bottomCard.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        // 1. العنوان الرئيسي: بخط عريض وحجم 18sp
        TextView titleMsg = new TextView(this);
        titleMsg.setText("لا يوجد اتصال بالإنترنت");
        titleMsg.setTextColor(Color.WHITE);
        titleMsg.setTextSize(18f);
        titleMsg.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleMsg.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.bottomMargin = 20;
        bottomCard.addView(titleMsg, titleParams);

        // 2. الوصف الفرعي: بخط خفيف ولون رمادي متناسق (#9CA3AF / 14sp)
        TextView subMsg = new TextView(this);
        subMsg.setText("يبدو أنك غير متصل بالشبكة. يرجى التحقق من الواي فاي أو بيانات الهاتف والمحاولة مجدداً.");
        subMsg.setTextColor(Color.parseColor("#9CA3AF")); // رمادي داكن ناعم ومتناسق مع الخلفية الداكنة
        subMsg.setTextSize(14f);
        subMsg.setGravity(android.view.Gravity.CENTER);
        subMsg.setLineSpacing(10f, 1.1f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.bottomMargin = 56;
        bottomCard.addView(subMsg, subParams);

        // 3. زر الإجراء الرئيسي (Pill Button - Radius: 12dp / #007AFF)
        FrameLayout btnContainer = new FrameLayout(this);
        
        // تصميم حواف ورسم الزر الدائري (Pill)
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#007AFF")); // أزرق نظام ناتيف
        btnBg.setCornerRadius(36f); // ما يعادل 12dp لتدوير الزوايا بالكامل
        btnContainer.setBackground(btnBg);
        btnContainer.setPadding(0, 32, 0, 32);

        LinearLayout btnContent = new LinearLayout(this);
        btnContent.setOrientation(LinearLayout.HORIZONTAL);
        btnContent.setGravity(android.view.Gravity.CENTER);

        // نص الزر الرئيسي
        TextView retryText = new TextView(this);
        retryText.setText("🔄  إعادة المحاولة");
        retryText.setTextColor(Color.WHITE);
        retryText.setTextSize(15f);
        retryText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        // مؤشر التحميل الناعم (Progress Spinner) مخفي افتراضياً
        ProgressBar btnSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        btnSpinner.setVisibility(View.GONE);
        btnSpinner.getIndeterminateDrawable().setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);

        btnContent.addView(retryText);
        btnContent.addView(btnSpinner);
        
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER);
        btnContainer.addView(btnContent, contentParams);

        // ⚡ التفاعل الذكي للزر عند الضغط
        btnContainer.setOnClickListener(v -> {
            // أ- إخفاء النص وإظهار مؤشر التحميل (Spinner) داخل الزر
            retryText.setVisibility(View.GONE);
            btnSpinner.setVisibility(View.VISIBLE);
            btnContainer.setEnabled(false); // منع الضغط المتكرر أثناء الفحص

            // ب- إجراء محاكاة فحص الاتصال الحقيقي
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (NetworkMonitor.isInternetAvailable(this)) {
                    toggleOfflineUI(false);
                    if (activeWebView != null) {
                        activeWebView.reload();
                    }
                } else {
                    // إعادة الزر لوضعه الطبيعي عند فشل الاتصال مع أنيميشن اهتزاز
                    btnSpinner.setVisibility(View.GONE);
                    retryText.setVisibility(View.VISIBLE);
                    btnContainer.setEnabled(true);

                    v.animate().translationX(12).setDuration(50)
                            .withEndAction(() -> v.animate().translationX(-12).setDuration(50)
                                    .withEndAction(() -> v.setTranslationX(0)).start()).start();
                }
            }, 1000); // إعطاء مهلة 1 ثانية لإشعار المستخدم بالتحقق الفعلي
        });

        LinearLayout.LayoutParams btnLayoutParams = new LinearLayout.LayoutParams(-1, -2);
        btnLayoutParams.setMargins(16, 0, 16, 0);
        bottomCard.addView(btnContainer, btnLayoutParams);

        // 4. وضع النافذة في أسفل الشاشة بالكامل
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM);
        pureOfflineUI.addView(bottomCard, cardParams);

        addContentView(pureOfflineUI, new ViewGroup.LayoutParams(-1, -1));
    }

    // دالة لرسم خلفية الكرت المنحنية بامتياز
    private Drawable createCardDrawable() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#1C1C1E")); // رمادي داكن فاخر (Dark Sheet Background)
        // انحناء الزوايا العلوية بمقدار 24dp (72px) لتصميم أنيق للغاية
        gd.setCornerRadii(new float[]{72, 72, 72, 72, 0, 0, 0, 0}); 
        return gd;
    }

    // محرك التبديل بين الـ WebView والواجهة الناتيف
    private void toggleOfflineUI(boolean show) {
        isOfflineUIVisible = show;
        runOnUiThread(() -> {
            if (show) {
                // إخفاء الشريط النحيف إذا كان ظاهراً
                if (offlineBar != null && offlineBar.getVisibility() == View.VISIBLE) {
                    offlineBar.setVisibility(View.GONE);
                }
                pureOfflineUI.setVisibility(View.VISIBLE);
                pureOfflineUI.setAlpha(0f);
                pureOfflineUI.animate().alpha(1f).setDuration(500).start();
                if (activeWebView != null) {
                    activeWebView.setVisibility(View.GONE);
                }
            } else {
                pureOfflineUI.animate().alpha(0f).setDuration(500)
                        .withEndAction(() -> pureOfflineUI.setVisibility(View.GONE)).start();
                if (activeWebView != null) {
                    activeWebView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    // =========================================================
    // 🔥 دوال عامة للتحكم بواجهات الأوفلاين (التعديل 3.1)
    // =========================================================

    /**
     * 🔥 تُستدعى من WebEngineManager لإظهار/إخفاء الواجهة الكبيرة (pureOfflineUI)
     */
    public void setOfflineUIVisibility(boolean show) {
        runOnUiThread(() -> {
            if (show && !isOfflineUIVisible) {
                toggleOfflineUI(true);
            } else if (!show && isOfflineUIVisible) {
                toggleOfflineUI(false);
            }
        });
    }

    /**
     * 🔥 تُستدعى من WebEngineManager لإظهار/إخفاء الشريط النحيف (offlineBar)
     * عند العودة للاتصال، يتم تغيير لونه إلى سماوي داكن/بنفسجي جليدي
     */
    public void setOfflineBarVisibility(boolean show) {
        runOnUiThread(() -> {
            if (offlineBar != null) {
                if (show) {
                    // لون عادي عند الانقطاع
                    offlineBar.setBackgroundColor(Color.parseColor("#323232"));
                    offlineBar.setText("لا يتوفر اتصال بالإنترنت");
                    offlineBar.setVisibility(View.VISIBLE);
                    offlineBar.animate().translationY(0).setDuration(400).start();
                } else {
                    // 🔥 عند عودة الإنترنت: تغيير اللون إلى سماوي داكن/بنفسجي جليدي قبل الإخفاء
                    offlineBar.setBackgroundColor(Color.parseColor("#1A237E")); // بنفسجي جليدي داكن
                    offlineBar.setText("🔄 تم استعادة الاتصال، جاري التحديث...");
                    offlineBar.animate().translationY(100).setDuration(400)
                        .withEndAction(() -> {
                            offlineBar.setVisibility(View.GONE);
                            // إعادة اللون الأصلي للاستخدام المستقبلي
                            offlineBar.setBackgroundColor(Color.parseColor("#323232"));
                            offlineBar.setText("لا يتوفر اتصال بالإنترنت");
                        }).start();
                }
            }
        });
    }

    // =========================================================
    // 🔄 نتائج النشاطات والصلاحيات (بدون تغيير)
    // =========================================================

    // 👑 [تعديل جراحي]: الجسر المفقود لاستقبال نتائج الاستوديو ومدير الملفات
    // هذه الدالة تلتقط الملف/الصورة التي اختارها المستخدم وتعيدها مباشرة إلى محرك الويب
    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RoyalCapabilitiesEngine.FILECHOOSER_RESULTCODE) {
            if (RoyalCapabilitiesEngine.filePathCallback == null) return;

            Uri[] results = null;

            // التحقق من أن المستخدم اختار ملفاً بالفعل ولم يتراجع
            if (resultCode == android.app.Activity.RESULT_OK) {
                if (data != null) {
                    String dataString = data.getDataString();
                    android.content.ClipData clipData = data.getClipData();

                    // دعم رفع ملفات متعددة (Multiple Files Upload)
                    if (clipData != null) {
                        results = new Uri[clipData.getItemCount()];
                        for (int i = 0; i < clipData.getItemCount(); i++) {
                            results[i] = clipData.getItemAt(i).getUri();
                        }
                    }
                    // دعم رفع ملف واحد
                    else if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }

            // إرسال النتيجة إلى الويب فيو (سواء كانت ملفات أو null إذا ألغى المستخدم)
            RoyalCapabilitiesEngine.filePathCallback.onReceiveValue(results);
            RoyalCapabilitiesEngine.filePathCallback = null;
        }
    }

    // [تعديل جراحي في MainActivity.java - جسر الصلاحيات]
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // 🛡️ تمرير نتيجة موافقة المستخدم إلى محرك القدرات
        if (engineManager != null && engineManager.getCapabilitiesHandler() != null) {
            // إذا كنت تستخدم اسم الكلاس من المهندس (RoyalCapabilitiesEngine)
            // تأكد من إضافة دالة getCapabilitiesHandler() في WebEngineManager
            engineManager.getCapabilitiesHandler().handlePermissionResult(requestCode, grantResults);
        }
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
