package com.store.app.offline;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 👑 OfflineBarController
 *
 * مسؤول حصراً عن شريط حالة الاتصال المنبثق
 * من أسفل الشاشة.
 *
 * المسؤوليات:
 *
 * - إنشاء Offline Bar.
 * - إدارة مظهره.
 * - إظهاره.
 * - إخفاؤه.
 * - انتقال استعادة الاتصال.
 * - Shake عند فشل التحميل.
 * - إدارة النصوص والألوان والـ animations.
 *
 * لا يحتوي على منطق اتخاذ قرار حالة الشبكة.
 * قرار إظهار الشريط أو الواجهة الكبيرة يبقى
 * في OfflineUIController.
 */
public class OfflineBarController {

    // =========================================================
    // 🔥 الثوابت
    // =========================================================

    private static final String DEFAULT_TEXT =
            "لا يتوفر اتصال بالإنترنت";

    private static final String RESTORED_TEXT =
            "🔄 تم استعادة الاتصال، جاري التحديث...";

    private static final String WARNING_TEXT =
            "⚠️ لا يمكن التحميل، تحقق من الاتصال";

    private static final int DEFAULT_BACKGROUND =
            Color.parseColor("#323232");

    private static final int RESTORED_BACKGROUND =
            Color.parseColor("#1A237E");

    private static final int BAR_HEIGHT_DP = 80;

    // =========================================================
    // 🔥 المراجع
    // =========================================================

    private final Activity activity;

    private TextView offlineBar;

    private Handler mainHandler;

    // =========================================================
    // 🔗 Callback
    // =========================================================

    public interface VisibilityCallback {

        void onVisibilityChanged(boolean visible);
    }

    private VisibilityCallback callback;

    // =========================================================
    // 🚀 Constructor
    // =========================================================

    public OfflineBarController(Activity activity) {

        this.activity = activity;

        this.mainHandler =
                new Handler(Looper.getMainLooper());
    }

    // =========================================================
    // 🚀 Initialization
    // =========================================================

    /**
     * إنشاء الشريط وإضافته إلى Activity.
     */
    public void init() {

        if (activity == null ||
                activity.isFinishing()) {

            return;
        }

        if (offlineBar != null) {
            return;
        }

        offlineBar =
                new TextView(activity);

        offlineBar.setText(
                DEFAULT_TEXT
        );

        offlineBar.setTextColor(
                Color.WHITE
        );

        offlineBar.setBackgroundColor(
                DEFAULT_BACKGROUND
        );

        offlineBar.setGravity(
                Gravity.CENTER
        );

        offlineBar.setPadding(
                0,
                12,
                0,
                12
        );

        offlineBar.setTextSize(
                14f
        );

        offlineBar.setVisibility(
                View.GONE
        );

        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(BAR_HEIGHT_DP),
                        Gravity.BOTTOM
                );

        params.bottomMargin = 0;

        activity.addContentView(
                offlineBar,
                params
        );
    }

    // =========================================================
    // 📡 Visibility
    // =========================================================

    /**
     * إظهار شريط انقطاع الاتصال.
     */
    public void show() {

        if (offlineBar == null) {
            return;
        }

        activity.runOnUiThread(() -> {

            if (activity.isFinishing()) {
                return;
            }

            offlineBar.animate().cancel();

            offlineBar.setBackgroundColor(
                    DEFAULT_BACKGROUND
            );

            offlineBar.setText(
                    DEFAULT_TEXT
            );

            offlineBar.setVisibility(
                    View.VISIBLE
            );

            offlineBar.animate()
                    .translationY(0)
                    .setDuration(400)
                    .start();
        });

        notifyVisibilityChanged(true);
    }

    /**
     * إخفاء شريط الاتصال مع انتقال الاستعادة.
     *
     * يحتفظ بنفس السلوك الموجود حالياً
     * في OfflineUIController.
     */
    public void hideWithAnimation() {

        if (offlineBar == null) {
            return;
        }

        activity.runOnUiThread(() -> {

            if (activity.isFinishing()) {
                return;
            }

            offlineBar.setBackgroundColor(
                    RESTORED_BACKGROUND
            );

            offlineBar.setText(
                    RESTORED_TEXT
            );

            offlineBar.animate()
                    .translationY(100)
                    .setDuration(400)
                    .withEndAction(() -> {

                        if (offlineBar == null) {
                            return;
                        }

                        offlineBar.setVisibility(
                                View.GONE
                        );

                        // إعادة الحالة الأصلية
                        // للاستخدام المستقبلي.
                        offlineBar.setBackgroundColor(
                                DEFAULT_BACKGROUND
                        );

                        offlineBar.setText(
                                DEFAULT_TEXT
                        );

                        offlineBar.setTranslationY(
                                0
                        );
                    })
                    .start();
        });

        notifyVisibilityChanged(false);
    }

    /**
     * إخفاء فوري بدون Transition.
     *
     * يستخدم عندما تكون الواجهة الكبيرة
     * هي التي ستصبح ظاهرة.
     */
    public void hideImmediately() {

        if (offlineBar == null) {
            return;
        }

        activity.runOnUiThread(() -> {

            offlineBar.animate().cancel();

            offlineBar.setVisibility(
                    View.GONE
            );

            offlineBar.setTranslationY(
                    0
            );

            offlineBar.setAlpha(
                    1f
            );

            offlineBar.setBackgroundColor(
                    DEFAULT_BACKGROUND
            );

            offlineBar.setText(
                    DEFAULT_TEXT
            );
        });

        notifyVisibilityChanged(false);
    }

    // =========================================================
    // 🔄 Online Transition
    // =========================================================

    /**
     * انتقال بصري عند عودة الاتصال
     * بينما الصفحة ما زالت جاهزة/صالحة.
     */
    public void showOnlineTransition() {

        if (offlineBar == null) {
            return;
        }

        activity.runOnUiThread(() -> {

            if (activity.isFinishing()) {
                return;
            }

            offlineBar.setBackgroundColor(
                    RESTORED_BACKGROUND
            );

            offlineBar.setText(
                    RESTORED_TEXT
            );

            if (offlineBar.getVisibility()
                    != View.VISIBLE) {

                offlineBar.setVisibility(
                        View.VISIBLE
                );

                offlineBar.setAlpha(
                        0f
                );

                offlineBar.animate()
                        .alpha(1f)
                        .setDuration(220)
                        .start();

            } else {

                offlineBar.animate()
                        .scaleX(1.02f)
                        .scaleY(1.02f)
                        .setDuration(110)
                        .withEndAction(() -> {

                            if (offlineBar == null) {
                                return;
                            }

                            offlineBar.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(110)
                                    .start();
                        })
                        .start();
            }

            /*
             * إخفاء تلقائي بعد مهلة قصيرة
             * إذا لم يتم إخفاؤه قبل ذلك.
             */
            mainHandler.postDelayed(() -> {

                if (offlineBar != null &&
                        offlineBar.getVisibility()
                                == View.VISIBLE) {

                    hideWithAnimation();
                }

            }, 900);
        });
    }

    // =========================================================
    // ⚠️ Shake
    // =========================================================

    /**
     * اهتزاز الشريط عند محاولة تحميل
     * شيء أثناء انقطاع الاتصال.
     */
    public void shake() {

        if (offlineBar == null) {
            return;
        }

        if (offlineBar.getVisibility()
                != View.VISIBLE) {

            show();
        }

        activity.runOnUiThread(() -> {

            if (offlineBar == null) {
                return;
            }

            offlineBar.animate()
                    .translationX(12f)
                    .setDuration(60)
                    .withEndAction(() ->
                            offlineBar.animate()
                                    .translationX(-12f)
                                    .setDuration(60)
                                    .withEndAction(() ->
                                            offlineBar.animate()
                                                    .translationX(0f)
                                                    .setDuration(60)
                                                    .start()
                                    )
                                    .start()
                    )
                    .start();

            String originalText =
                    offlineBar.getText().toString();

            offlineBar.setText(
                    WARNING_TEXT
            );

            mainHandler.postDelayed(() -> {

                if (offlineBar != null &&
                        offlineBar.getVisibility()
                                == View.VISIBLE) {

                    offlineBar.setText(
                            originalText
                    );
                }

            }, 1800);
        });
    }

    // =========================================================
    // 🔍 State
    // =========================================================

    public boolean isVisible() {

        return offlineBar != null &&
                offlineBar.getVisibility()
                        == View.VISIBLE;
    }

    public TextView getView() {

        return offlineBar;
    }

    // =========================================================
    // 🔗 Callback
    // =========================================================

    public void setCallback(
            VisibilityCallback callback) {

        this.callback = callback;
    }

    private void notifyVisibilityChanged(
            boolean visible) {

        if (callback != null) {
            callback.onVisibilityChanged(
                    visible
            );
        }
    }

    // =========================================================
    // 🧹 Destroy
    // =========================================================

    public void destroy() {

        if (mainHandler != null) {

            mainHandler.removeCallbacksAndMessages(
                    null
            );
        }

        if (offlineBar != null) {

            offlineBar.animate().cancel();

            ViewGroup parent =
                    (ViewGroup) offlineBar.getParent();

            if (parent != null) {
                parent.removeView(
                        offlineBar
                );
            }
        }

        offlineBar = null;
        callback = null;
        mainHandler = null;
    }

    // =========================================================
    // 📐 DP
    // =========================================================

    private int dp(float value) {

        return Math.round(
                value *
                        activity.getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
          }
