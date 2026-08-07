package com.store.app.offline;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.store.app.BuildConfig;
import com.store.app.NetworkMonitor;
import com.store.app.R;
import com.store.app.RoyalNetworkEngine;
import com.store.app.WebEngineManager;
import com.store.app.offline.OfflineStateManager; // ✅ صحيح

/**
 * 👑 OfflineUIController - المسؤول عن إدارة واجهات الأوفلاين
 * 
 * تم نقل جميع منطق الأوفلاين من MainActivity إلى هنا
 * - إدارة واجهة الأوفلاين الكبيرة (pureOfflineUI)
 * - إدارة شريط الأوفلاين النحيف (offlineBar)
 * - التعامل مع تغيرات حالة الشبكة
 * - التبديل السلس بين WebView والواجهات الناتيف
 */
public class OfflineUIController {

    private static final String TAG = "OfflineUIController";

    // ==========================================
    // 🔥 المتغيرات
    // ==========================================

    private final Activity activity;
    private final WebView webView;
    private final WebEngineManager engineManager;

    // عناصر الواجهة
    private FrameLayout pureOfflineUI;
    private TextView offlineBar;
    private ProgressBar progressBar;

    // حالة الأوفلاين
    private boolean isOfflineUIVisible = false;
    private boolean isPageLoaded = false;

    // مراجع للعناصر الأخرى (للوصول إليها من MainActivity)
    public interface OfflineUICallback {
        void onOfflineUIVisibilityChanged(boolean visible);
        void onOfflineBarVisibilityChanged(boolean visible);
    }
    private OfflineUICallback callback;

    // ==========================================
    // 🚀 دورة الحياة
    // ==========================================

    public OfflineUIController(Activity activity, WebView webView, WebEngineManager engineManager) {
        this.activity = activity;
        this.webView = webView;
        this.engineManager = engineManager;
    }

    /**
     * 🔥 تهيئة وحدة الأوفلاين
     * - إنشاء الواجهات
     * - تسجيل مستمع الشبكة
     */
    public void init() {
        Log.i(TAG, "🚀 Initializing OfflineUIController...");

        // 1. إنشاء الواجهات
        createOfflineBar();
        createPureOfflineUI();

        // 2. تسجيل مستمع الشبكة
        NetworkMonitor.setListener(connected -> {
            Log.i(TAG, "📡 Network state changed: " + connected);
            handleNetworkChange(connected);
        });

        // 🔥 ربط OfflineStateManager
        OfflineStateManager.getInstance().bind(webView, this);

        Log.i(TAG, "✅ OfflineUIController initialized.");
    }

    /**
     * 🔥 يُستدعى من MainActivity.onResume()
     */
    public void onResume() {
        Log.d(TAG, "🔄 onResume called");

        if (activity == null || activity.isFinishing()) return;

        // تحقق من حالة الشبكة عند العودة للتطبيق
        if (!NetworkMonitor.isInternetAvailable(activity)) {
            // الإنترنت مقطوع
            handleOfflineState();
        } else {
            // الإنترنت موجود
            handleOnlineState();
        }
    }

    /**
     * 🔥 يُستدعى من MainActivity.onDestroy()
     */
    public void destroy() {
        Log.i(TAG, "🧹 Destroying OfflineUIController...");
        // تنظيف المستمعين
        NetworkMonitor.setListener(null);
        // تنظيف المراجع
        pureOfflineUI = null;
        offlineBar = null;
        callback = null;
    }

    // ==========================================
    // 📡 معالجة تغيرات الشبكة
    // ==========================================

    private void handleNetworkChange(boolean connected) {
        if (connected) {
            // ✅ الإنترنت عاد
            handleOnlineState();
        } else {
            // ❌ الإنترنت انقطع
            handleOfflineState();
        }
    }

    private void handleOfflineState() {
        Log.i(TAG, "📡 Network lost. Handling offline state...");

        if (webView == null) return;

        // إذا كانت الصفحة فارغة أو غير صالحة
        if (webView.getUrl() == null || webView.getUrl().equals("about:blank")) {
            showOfflineUI();
        } else if (engineManager != null && !engineManager.isPageValid()) {
            // الصفحة غير صالحة (خطأ)
            showOfflineUI();
        } else {
            // صفحة موجودة وصالحة → إظهار الشريط النحيف
            showOfflineBar();
        }
    }

    private void handleOnlineState() {
        Log.i(TAG, "🌐 Network restored. Handling online state...");

        if (webView == null) return;

        // إخفاء الواجهة الكبيرة إن كانت ظاهرة
        if (isOfflineUIVisible) {
            hideOfflineUI();
        }

        // إخفاء الشريط النحيف إن كان ظاهراً (مع تأثير بصري)
        if (offlineBar != null && offlineBar.getVisibility() == View.VISIBLE) {
            hideOfflineBarWithAnimation();
        }

        // إذا كانت الصفحة فارغة وتحتاج تحميل
        if (!isPageLoaded && webView.getUrl() == null) {
            webView.loadUrl(BuildConfig.CLIENT_URL);
            isPageLoaded = true;
        }
    }

    // ==========================================
    // 🎨 إنشاء الواجهات
    // ==========================================

    /**
     * 📡 شريط الأوفلاين النحيف
     */
    private void createOfflineBar() {
        if (activity == null) return;

        offlineBar = new TextView(activity);
        offlineBar.setText("لا يتوفر اتصال بالإنترنت");
        offlineBar.setTextColor(Color.WHITE);
        offlineBar.setBackgroundColor(Color.parseColor("#323232"));
        offlineBar.setGravity(android.view.Gravity.CENTER);
        offlineBar.setPadding(0, 12, 0, 12);
        offlineBar.setTextSize(14f);
        offlineBar.setVisibility(View.GONE);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 80, android.view.Gravity.BOTTOM);
        params.bottomMargin = 0;
        activity.addContentView(offlineBar, params);

        Log.d(TAG, "📡 Offline bar created.");
    }

    /**
     * 🍏 واجهة الأوفلاين الناتيف الكبيرة
     */
    private void createPureOfflineUI() {
        if (activity == null) return;

        // 1. الحاوية الرئيسية
        pureOfflineUI = new FrameLayout(activity);
        pureOfflineUI.setBackgroundColor(Color.parseColor("#F3F4F6"));
        pureOfflineUI.setVisibility(View.GONE);

        // ☁️ أيقونة السحابة
        ImageView cloudIcon = new ImageView(activity);
        cloudIcon.setImageResource(R.drawable.ic_cloud_off);
        cloudIcon.setAlpha(0.6f);
        FrameLayout.LayoutParams cloudParams = new FrameLayout.LayoutParams(90, 90,
                android.view.Gravity.TOP | android.view.Gravity.START);
        cloudParams.setMargins(60, 80, 0, 0);
        pureOfflineUI.addView(cloudIcon, cloudParams);

        // 🖼️ شعار المتجر
        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.mipmap.ic_launcher);
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(280, 280,
                android.view.Gravity.CENTER);
        logoParams.bottomMargin = 200;
        pureOfflineUI.addView(logo, logoParams);

        // 💳 النافذة المنبثقة السفلية (Bottom Card Sheet)
        LinearLayout bottomCard = new LinearLayout(activity);
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setBackground(createCardDrawable());
        bottomCard.setPadding(64, 72, 64, 88);
        bottomCard.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        // العنوان الرئيسي
        TextView titleMsg = new TextView(activity);
        titleMsg.setText("لا يوجد اتصال بالإنترنت");
        titleMsg.setTextColor(Color.WHITE);
        titleMsg.setTextSize(18f);
        titleMsg.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleMsg.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.bottomMargin = 20;
        bottomCard.addView(titleMsg, titleParams);

        // الوصف الفرعي
        TextView subMsg = new TextView(activity);
        subMsg.setText("يبدو أنك غير متصل بالشبكة. يرجى التحقق من الواي فاي أو بيانات الهاتف والمحاولة مجدداً.");
        subMsg.setTextColor(Color.parseColor("#9CA3AF"));
        subMsg.setTextSize(14f);
        subMsg.setGravity(android.view.Gravity.CENTER);
        subMsg.setLineSpacing(10f, 1.1f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.bottomMargin = 56;
        bottomCard.addView(subMsg, subParams);

        // زر الإجراء الرئيسي (Pill Button)
        FrameLayout btnContainer = new FrameLayout(activity);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#007AFF"));
        btnBg.setCornerRadius(36f);
        btnContainer.setBackground(btnBg);
        btnContainer.setPadding(0, 32, 0, 32);

        LinearLayout btnContent = new LinearLayout(activity);
        btnContent.setOrientation(LinearLayout.HORIZONTAL);
        btnContent.setGravity(android.view.Gravity.CENTER);

        TextView retryText = new TextView(activity);
        retryText.setText("🔄  إعادة المحاولة");
        retryText.setTextColor(Color.WHITE);
        retryText.setTextSize(15f);
        retryText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);

        ProgressBar btnSpinner = new ProgressBar(activity, null, android.R.attr.progressBarStyleSmall);
        btnSpinner.setVisibility(View.GONE);
        btnSpinner.getIndeterminateDrawable().setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);

        btnContent.addView(retryText);
        btnContent.addView(btnSpinner);

        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(-2, -2,
                android.view.Gravity.CENTER);
        btnContainer.addView(btnContent, contentParams);

        // تفاعل الزر
        btnContainer.setOnClickListener(v -> {
            retryText.setVisibility(View.GONE);
            btnSpinner.setVisibility(View.VISIBLE);
            btnContainer.setEnabled(false);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (NetworkMonitor.isInternetAvailable(activity)) {
                    hideOfflineUI();
                    if (webView != null) {
                        webView.reload();
                    }
                } else {
                    btnSpinner.setVisibility(View.GONE);
                    retryText.setVisibility(View.VISIBLE);
                    btnContainer.setEnabled(true);

                    v.animate().translationX(12).setDuration(50)
                            .withEndAction(() -> v.animate().translationX(-12).setDuration(50)
                                    .withEndAction(() -> v.setTranslationX(0)).start()).start();
                }
            }, 1000);
        });

        LinearLayout.LayoutParams btnLayoutParams = new LinearLayout.LayoutParams(-1, -2);
        btnLayoutParams.setMargins(16, 0, 16, 0);
        bottomCard.addView(btnContainer, btnLayoutParams);

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM);
        pureOfflineUI.addView(bottomCard, cardParams);

        activity.addContentView(pureOfflineUI, new ViewGroup.LayoutParams(-1, -1));

        Log.d(TAG, "🍏 Pure Offline UI created.");
    }

    /**
     * 🔲 خلفية الكرت المنحنية
     */
    private Drawable createCardDrawable() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor("#1C1C1E"));
        gd.setCornerRadii(new float[]{72, 72, 72, 72, 0, 0, 0, 0});
        return gd;
    }

    // ==========================================
    // 🎯 التحكم بالواجهات (public لاستدعائها من WebEngineManager)
    // ==========================================

    /**
     * 🔥 إظهار/إخفاء الواجهة الكبيرة
     */
    public void setOfflineUIVisibility(boolean show) {
        if (show && !isOfflineUIVisible) {
            showOfflineUI();
        } else if (!show && isOfflineUIVisible) {
            hideOfflineUI();
        }
    }

    /**
     * 🔥 إظهار/إخفاء الشريط النحيف
     */
    public void setOfflineBarVisibility(boolean show) {
        if (offlineBar == null) return;

        if (show) {
            showOfflineBar();
        } else {
            hideOfflineBarWithAnimation();
        }
    }

    // ==========================================
    // 🔧 الدوال الداخلية للتحكم بالواجهات
    // ==========================================

    private void showOfflineUI() {
        if (pureOfflineUI == null) return;

        isOfflineUIVisible = true;

        // إخفاء الشريط النحيف إذا كان ظاهراً
        if (offlineBar != null && offlineBar.getVisibility() == View.VISIBLE) {
            offlineBar.setVisibility(View.GONE);
        }

        activity.runOnUiThread(() -> {
            pureOfflineUI.setVisibility(View.VISIBLE);
            pureOfflineUI.setAlpha(0f);
            pureOfflineUI.animate().alpha(1f).setDuration(500).start();

            if (webView != null) {
                webView.setVisibility(View.GONE);
            }
        });

        if (callback != null) {
            callback.onOfflineUIVisibilityChanged(true);
        }

        Log.d(TAG, "🟠 Offline UI shown.");
    }

    private void hideOfflineUI() {
        if (pureOfflineUI == null) return;

        isOfflineUIVisible = false;

        activity.runOnUiThread(() -> {
            pureOfflineUI.animate().alpha(0f).setDuration(500)
                    .withEndAction(() -> pureOfflineUI.setVisibility(View.GONE)).start();

            if (webView != null) {
                webView.setVisibility(View.VISIBLE);
            }
        });

        if (callback != null) {
            callback.onOfflineUIVisibilityChanged(false);
        }

        Log.d(TAG, "🟢 Offline UI hidden.");
    }

    private void showOfflineBar() {
        if (offlineBar == null) return;

        activity.runOnUiThread(() -> {
            offlineBar.setBackgroundColor(Color.parseColor("#323232"));
            offlineBar.setText("لا يتوفر اتصال بالإنترنت");
            offlineBar.setVisibility(View.VISIBLE);
            offlineBar.animate().translationY(0).setDuration(400).start();
        });

        if (callback != null) {
            callback.onOfflineBarVisibilityChanged(true);
        }

        Log.d(TAG, "📡 Offline bar shown.");
    }

    private void hideOfflineBarWithAnimation() {
        if (offlineBar == null) return;

        activity.runOnUiThread(() -> {
            // تغيير اللون إلى بنفسجي جليدي قبل الإخفاء
            offlineBar.setBackgroundColor(Color.parseColor("#1A237E"));
            offlineBar.setText("🔄 تم استعادة الاتصال، جاري التحديث...");

            offlineBar.animate().translationY(100).setDuration(400)
                    .withEndAction(() -> {
                        offlineBar.setVisibility(View.GONE);
                        // إعادة اللون الأصلي للاستخدام المستقبلي
                        offlineBar.setBackgroundColor(Color.parseColor("#323232"));
                        offlineBar.setText("لا يتوفر اتصال بالإنترنت");
                    }).start();
        });

        if (callback != null) {
            callback.onOfflineBarVisibilityChanged(false);
        }

        Log.d(TAG, "📡 Offline bar hidden with animation.");
    }

    // ==========================================
    // 🔗 الدوال العامة للاستعلام عن الحالة
    // ==========================================

    public boolean isOfflineUIVisible() {
        return isOfflineUIVisible;
    }

    public boolean isPageLoaded() {
        return isPageLoaded;
    }

    public void setPageLoaded(boolean loaded) {
        this.isPageLoaded = loaded;
    }

    public void setCallback(OfflineUICallback callback) {
        this.callback = callback;
    }
                           }
