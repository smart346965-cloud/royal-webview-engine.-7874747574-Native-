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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 👑 MainActivity - النواة الأساسية لإدارة محرك الويب المخصص
 * تم تطهيرها بالكامل من مخلفات الـ TWA لتعمل بأقصى سرعة استجابة (Zero-friction)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "RoyalMainActivity";
    private static final long FIXED_SPLASH_TIME = 5000; // قيمة ثابتة 5 ثوانٍ بالتمام والكمال

    private boolean splashRemoved = false;
    private boolean isPageReady = false; // flag للرندرة

    private WebEngineManager engineManager;
    private WebView activeWebView;
    private ProgressBar progressBar;
    private TextView offlineBar;

    // [تعديل في MainActivity.java - منطقة التعريفات]
    private FrameLayout pureOfflineUI; // الحاوية الكبرى لواجهة أوفلاين
    private boolean isOfflineUIVisible = false;

    private long splashStartTime = 0;

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

        // 🚀 السطر الذهبي: حاول الإحياء الثنائي أولاً
        boolean sessionRestored = RoyalSessionSentinel.resurrect(activeWebView, this);

        if (!sessionRestored) {
            // إذا لم توجد جلسة، حمّل الرابط الافتراضي
            activeWebView.loadUrl(BuildConfig.CLIENT_URL);
        }

        // 4️⃣ نظام التحكم بالرجوع المستقل نيتف
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (activeWebView != null && activeWebView.canGoBack()) {
                    activeWebView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    activeWebView.goBack();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (activeWebView != null) {
                            activeWebView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
                        }
                    }, 1000);
                } else {
                    moveTaskToBack(true);
                }
            }
        });

        // 5️⃣ الحصانة البصرية وتخصيص شريط النظام بالكامل
        SystemUI.applyKingMode(this, activeWebView);
        SystemUI.setDynamicIcons(this.getWindow(), true);

        // [بناء واجهة الأوفلاين الناتيف فوراً]
        createPureOfflineUI();

        // 6️⃣ بناء وتجهيز طبقة شاشة التحميل (Splash Screen Overlay)
        setupSplashScreen();

        // 7️⃣ إنشاء شريط الأوفلاين السينمائي
        createOfflineBar();

        // 🚀 فحص الإنترنت الأولي (عند الإقلاع)
        if (!NetworkMonitor.isInternetAvailable(this)) {
            toggleOfflineUI(true);
        }

        // ربط الشريط بمراقب الشبكة
        NetworkMonitor.setListener(connected -> {
            if (connected) {
                if (isOfflineUIVisible) toggleOfflineUI(false);
                // إخفاء الشريط النحيف أيضاً إذا كان ظاهراً
                if (offlineBar != null) {
                    offlineBar.animate().translationY(100).setDuration(400).withEndAction(() -> offlineBar.setVisibility(View.GONE)).start();
                }

                // إعادة تحميل الموقع تلقائياً إذا كنا في صفحة بيضاء
                if (activeWebView.getUrl() == null || activeWebView.getUrl().equals("about:blank")) {
                    runOnUiThread(() -> activeWebView.loadUrl(BuildConfig.CLIENT_URL));
                }
            } else {
                // إذا كنا في بداية التشغيل، اظهر الواجهة الكبيرة، وإلا اظهر الشريط النحيف فقط
                if (activeWebView.getUrl() == null || activeWebView.getUrl().equals("about:blank")) {
                    toggleOfflineUI(true);
                } else if (offlineBar != null) {
                    offlineBar.setVisibility(View.VISIBLE);
                    offlineBar.animate().translationY(0).setDuration(400).start();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // إيقاف مؤقت للعمليات الرسومية غير النشطة في الخلفية للحفاظ على طاقة الجهاز
        if (activeWebView != null) {
            activeWebView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // استئناف العمليات الرسومية والـ JavaScript فور عودة المستخدم للتطبيق
        if (activeWebView != null) {
            activeWebView.onResume();
        }
    }

    @Override
    protected void onDestroy() {
        // 🛡️ التعديل: لا تحمل about:blank، فقط افصل الويب فيو بأمان
        if (activeWebView != null) {
            // نكتفي بإيقاف العمليات دون مسح السطح الرسومي
            activeWebView.stopLoading();
        }
        RoyalWebViewHost.detach();
        super.onDestroy();
    }

    // =========================================================
    // ⚙️ إعدادات واجهة السبلاش
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
    // 📡 شريط الأوفلاين
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
    // 🍏 واجهة الأوفلاين الناتيف فائقة الاحترافية (Apple Premium Style)
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
                    activeWebView.reload();
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
                pureOfflineUI.setVisibility(View.VISIBLE);
                pureOfflineUI.setAlpha(0f);
                pureOfflineUI.animate().alpha(1f).setDuration(500).start();
                activeWebView.setVisibility(View.GONE);
            } else {
                pureOfflineUI.animate().alpha(0f).setDuration(500)
                        .withEndAction(() -> pureOfflineUI.setVisibility(View.GONE)).start();
                activeWebView.setVisibility(View.VISIBLE);
            }
        });
    }

    // =========================================================
    // 🔄 نتائج النشاطات والصلاحيات
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
                }
