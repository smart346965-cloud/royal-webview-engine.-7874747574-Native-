package com.store.app.offline;

import android.animation.TimeInterpolator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsAnimationCompat;

import java.util.List;

/**
 * 👑 OfflineBarController
 *
 * Native Offline Bar
 *
 * - Edge-to-edge compatible
 * - Navigation bar aware
 * - WindowInsetsAnimation driven
 * - 3-button navigation adaptive positioning
 * - Gesture navigation stable bottom position
 * - Native glass veil
 * - Premium gradient / shadow / elevation
 * - No direct swipe listeners
 * - No polling
 */
public class OfflineBarController {

    // =========================================================
    // 🎨 CONTENT
    // =========================================================

    private static final String DEFAULT_TEXT =
            "لا يتوفر اتصال بالإنترنت";

    private static final String RESTORED_TEXT =
            "🔄 تم استعادة الاتصال، جاري التحديث...";

    private static final String WARNING_TEXT =
            "⚠️ لا يمكن التحميل، تحقق من الاتصال";

    // =========================================================
    // 🎨 COLORS
    // =========================================================

    private static final int BAR_TOP =
            Color.parseColor("#2C2C2E");

    private static final int BAR_BOTTOM =
            Color.parseColor("#1C1C1E");

    private static final int RESTORED_TOP =
            Color.parseColor("#283593");

    private static final int RESTORED_BOTTOM =
            Color.parseColor("#1A237E");

    private static final int TEXT_COLOR =
            Color.parseColor("#F2F2F7");

    // =========================================================
    // 📐 DIMENSIONS
    // =========================================================

    /**
     * ارتفاع الشريط الحقيقي.
     *
     * ليس 80dp.
     * الشريط البصري = 48dp فقط.
     */
    private static final int BAR_HEIGHT_DP = 48;

    /**
     * مساحة آمنة ثابتة في وضع الإيماءات.
     *
     * هذه ليست ارتفاعاً إضافياً للشريط.
     */
    private static final int GESTURE_SAFE_DP = 24;

    /**
     * ارتفاع ستارة النظام عند الحاجة.
     */
    private static final int VEIL_MAX_DP = 64;

    // =========================================================
    // 🎬 ANIMATION
    // =========================================================

    private static final long SHOW_DURATION = 460L;
    private static final long HIDE_DURATION = 460L;
    private static final long RESTORE_DURATION = 260L;

    private static final TimeInterpolator PREMIUM_INTERPOLATOR =
            input -> {
                // cubic-bezier تقريبية:
                // 0.22, 1, 0.36, 1
                float t = input - 1f;
                return 1f + t * t * t + t * t;
            };

    // =========================================================
    // 🔗 REFERENCES
    // =========================================================

    private final Activity activity;

    private final Handler mainHandler;

    private TextView offlineBar;

    /**
     * الطبقة الخلفية التي تظهر فقط مع Navigation Bar.
     */
    private View bottomVeil;

    /**
     * Root مستقل يسمح لنا بوضع الـ bar والـ veil
     * في طبقات محسوبة بدقة.
     */
    private FrameLayout overlayRoot;

    // =========================================================
    // 📱 INSETS STATE
    // =========================================================

    private int navigationBottomInset = 0;

    private int gestureBottomInset = 0;

    private boolean navigationBarVisible = false;

    private boolean gestureNavigation = false;

    private boolean initialized = false;

    // =========================================================
    // 🔗 CALLBACK
    // =========================================================

    public interface VisibilityCallback {

        void onVisibilityChanged(boolean visible);
    }

    private VisibilityCallback callback;

    // =========================================================
    // 🚀 CONSTRUCTOR
    // =========================================================

    public OfflineBarController(Activity activity) {

        this.activity = activity;

        this.mainHandler =
                new Handler(Looper.getMainLooper());
    }

    // =========================================================
    // 🚀 INITIALIZATION
    // =========================================================

    public void init() {

        if (activity == null ||
                activity.isFinishing() ||
                initialized) {

            return;
        }

        initialized = true;

        createOverlay();

        installInsetsController();

        /*
         * احصل على الحالة الأولية مباشرة.
         */
        overlayRoot.post(() -> {

            WindowInsetsCompat insets =
                    ViewCompat.getRootWindowInsets(
                            overlayRoot
                    );

            if (insets != null) {
                applyInitialInsets(insets);
            }
        });
    }

    // =========================================================
    // 🧱 OVERLAY
    // =========================================================

    private void createOverlay() {

        overlayRoot = new FrameLayout(activity);

        overlayRoot.setClipChildren(false);
        overlayRoot.setClipToPadding(false);

        /*
         * Root يغطي الشاشة بالكامل.
         */
        FrameLayout.LayoutParams rootParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );

        activity.addContentView(
                overlayRoot,
                rootParams
        );

        createVeil();

        createBar();
    }

    // =========================================================
    // 🌫️ VEIL
    // =========================================================

    private void createVeil() {

        bottomVeil = new View(activity);

        bottomVeil.setVisibility(View.GONE);

        /*
         * الـ veil ليس مستطيلاً أسود.
         *
         * هو تدرج ناعم جداً يبدأ شفافاً
         * وينتهي بدرجة من لون الـ Offline Bar.
         */
        GradientDrawable veilDrawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{

                                Color.argb(0, 28, 28, 30),

                                Color.argb(
                                        32,
                                        44,
                                        44,
                                        46
                                ),

                                Color.argb(
                                        105,
                                        28,
                                        28,
                                        30
                                ),

                                Color.argb(
                                        205,
                                        28,
                                        28,
                                        30
                                )
                        }
                );

        bottomVeil.setBackground(veilDrawable);

        bottomVeil.setAlpha(0f);

        /*
         * لا نستخدم elevation هنا.
         * الـ veil يجب أن يكون ناعماً وغير محسوس.
         */
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(VEIL_MAX_DP)
                );

        params.gravity = Gravity.BOTTOM;

        overlayRoot.addView(
                bottomVeil,
                params
        );
    }

    // =========================================================
    // 🖤 BAR
    // =========================================================

    private void createBar() {

        offlineBar = new TextView(activity);

        offlineBar.setText(
                DEFAULT_TEXT
        );

        offlineBar.setTextColor(
                TEXT_COLOR
        );

        offlineBar.setTextSize(
                15f
        );

        offlineBar.setGravity(
                Gravity.CENTER
        );

        offlineBar.setSingleLine(
                true
        );

        offlineBar.setPadding(
                dp(20),
                0,
                dp(20),
                0
        );

        offlineBar.setAlpha(0f);

        offlineBar.setVisibility(
                View.GONE
        );

        /*
         * يمنع الوميض أثناء تغيّر insets.
         */
        offlineBar.setLayerType(
                View.LAYER_TYPE_HARDWARE,
                null
        );

        applyBarBackground(
                BAR_TOP,
                BAR_BOTTOM
        );

        /*
         * ظل احترافي.
         */
        offlineBar.setElevation(
                dp(12)
        );

        /*
         * الشريط نفسه 48dp فقط.
         */
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(BAR_HEIGHT_DP)
                );

        params.gravity = Gravity.BOTTOM;

        overlayRoot.addView(
                offlineBar,
                params
        );
    }

    // =========================================================
    // 🎨 BAR BACKGROUND
    // =========================================================

    private void applyBarBackground(
            int topColor,
            int bottomColor) {

        GradientDrawable background =
                new GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{

                                topColor,
                                bottomColor
                        }
                );

        background.setCornerRadii(
                new float[]{

                        dp(12), dp(12),
                        dp(12), dp(12),
                        0, 0,
                        0, 0
                }
        );

        background.setStroke(
                dp(1),
                Color.argb(
                        26,
                        255,
                        255,
                        255
                )
        );

        offlineBar.setBackground(
                background
        );
    }

    // =========================================================
    // 📱 WINDOW INSETS
    // =========================================================

    private void installInsetsController() {

        ViewCompat.setOnApplyWindowInsetsListener(
                overlayRoot,
                (view, insets) -> {

                    updateInsets(
                            insets
                    );

                    return insets;
                }
        );

        ViewCompat.setWindowInsetsAnimationCallback(
                overlayRoot,

                new WindowInsetsAnimationCompat.Callback(
                        WindowInsetsAnimationCompat
                                .Callback
                                .DISPATCH_MODE_CONTINUE_ON_SUBTREE
                ) {

                    @Override
                    public void onPrepare(
                            WindowInsetsAnimationCompat animation) {

                        /*
                         * لا نحرك الشريط هنا.
                         *
                         * النظام سيبدأ animation
                         * و onProgress سيقود الحركة.
                         */
                    }

                    @Override
                    public WindowInsetsCompat onProgress(
                            WindowInsetsCompat insets,
                            List<WindowInsetsAnimationCompat> runningAnimations) {

                        updateInsets(
                                insets
                        );

                        return insets;
                    }

                    @Override
                    public void onEnd(
                            WindowInsetsAnimationCompat animation) {

                        WindowInsetsCompat finalInsets =
                                ViewCompat.getRootWindowInsets(
                                        overlayRoot
                                );

                        if (finalInsets != null) {

                            updateInsets(
                                    finalInsets
                            );
                        }
                    }
                }
        );
    }

    // =========================================================
    // 📐 INSETS UPDATE
    // =========================================================

    private void updateInsets(
            WindowInsetsCompat insets) {

        if (insets == null ||
                overlayRoot == null) {

            return;
        }

        Insets navigation =
                insets.getInsets(
                        WindowInsetsCompat.Type.navigationBars()
                );

        Insets gestures =
                insets.getInsets(
                        WindowInsetsCompat.Type.systemGestures()
                );

        navigationBottomInset =
                navigation.bottom;

        gestureBottomInset =
                gestures.bottom;

        navigationBarVisible =
                insets.isVisible(
                        WindowInsetsCompat.Type.navigationBars()
                );

        /*
         * تحديد وضع الإيماءات:
         *
         * في Gesture Navigation غالباً
         * navigationBars.bottom = 0
         * بينما systemGestures.bottom > 0.
         */
        gestureNavigation =
                navigationBottomInset == 0 &&
                        gestureBottomInset > 0;

        applyNavigationPosition();
    }

    // =========================================================
    // 📐 INITIAL INSETS
    // =========================================================

    private void applyInitialInsets(
            WindowInsetsCompat insets) {

        updateInsets(
                insets
        );

        /*
         * الحالة الأولية بدون animation.
         */
        if (offlineBar != null) {

            offlineBar.setTranslationY(
                    calculateBarTranslation()
            );
        }
    }

    // =========================================================
    // 🧠 POSITION ENGINE
    // =========================================================

    private int calculateBarTranslation() {

        /*
         * 3-button navigation:
         *
         * الشريط يرتفع فوق مساحة أزرار النظام
         * بالضبط.
         */
        if (navigationBarVisible &&
                navigationBottomInset > 0) {

            return -navigationBottomInset;
        }

        /*
         * Gesture navigation:
         *
         * لا نرفع الشريط مع gesture inset.
         *
         * يبقى في مكان ثابت.
         */
        return 0;
    }

    // =========================================================
    // 🎯 APPLY POSITION
    // =========================================================

    private void applyNavigationPosition() {

        if (offlineBar == null ||
                overlayRoot == null) {

            return;
        }

        int targetTranslation =
                calculateBarTranslation();

        /*
         * الشريط غير ظاهر:
         * لا نحتاج animation.
         */
        if (offlineBar.getVisibility()
                != View.VISIBLE) {

            offlineBar.setTranslationY(
                    targetTranslation
            );

            updateVeil(
                    targetTranslation
            );

            return;
        }

        /*
         * أثناء WindowInsetsAnimation:
         * onProgress يحدد الموقع مباشرة.
         *
         * لا نستخدم animate().
         *
         * هذا يمنع الـ lag والـ double interpolation.
         */
        offlineBar.setTranslationY(
                targetTranslation
        );

        updateVeil(
                targetTranslation
        );
    }

    // =========================================================
    // 🌫️ VEIL POSITION
    // =========================================================

    private void updateVeil(
            int barTranslation) {

        if (bottomVeil == null) {
            return;
        }

        /*
         * الـ veil يظهر فقط عندما تكون
         * Navigation Bar حقيقية ظاهرة.
         *
         * Gesture Navigation:
         * لا veil.
         */
        if (navigationBarVisible &&
                navigationBottomInset > 0) {

            int height =
                    Math.min(
                            dp(VEIL_MAX_DP),
                            Math.max(
                                    dp(24),
                                    navigationBottomInset
                            )
                    );

            ViewGroup.LayoutParams lp =
                    bottomVeil.getLayoutParams();

            if (lp != null) {

                lp.height = height;

                bottomVeil.setLayoutParams(
                        lp
                );
            }

            /*
             * كلما ارتفعت مساحة النظام،
             * يصبح الـ veil أكثر حضوراً.
             */
            float intensity =
                    Math.min(
                            1f,
                            navigationBottomInset /
                                    (float) dp(64)
                    );

            bottomVeil.setVisibility(
                    View.VISIBLE
            );

            bottomVeil.setAlpha(
                    intensity
            );

        } else {

            /*
             * مهم جداً:
             *
             * عند اختفاء Navigation Bar
             * لا نترك veil ظاهراً.
             */
            bottomVeil.setAlpha(
                    0f
            );

            bottomVeil.setVisibility(
                    View.GONE
            );
        }
    }

    // =========================================================
    // 📡 SHOW
    // =========================================================

    public void show() {

        if (offlineBar == null) {
            return;
        }

        activity.runOnUiThread(() -> {

            if (activity.isFinishing()) {
                return;
            }

            mainHandler.removeCallbacksAndMessages(
                    null
            );

            offlineBar.animate().cancel();

            offlineBar.setText(
                    DEFAULT_TEXT
            );

            applyBarBackground(
                    BAR_TOP,
                    BAR_BOTTOM
            );

            /*
             * احسب مكانه الصحيح قبل إظهاره.
             */
            offlineBar.setTranslationY(
                    calculateBarTranslation()
            );

            offlineBar.setScaleX(
                    0.96f
            );

            offlineBar.setScaleY(
                    0.96f
            );

            offlineBar.setAlpha(
                    0f
            );

            offlineBar.setVisibility(
                    View.VISIBLE
            );

            /*
             * الـ veil لا يظهر بسبب show().
             *
             * يظهر فقط عندما يظهر Navigation Bar.
             */
            updateVeil(
                    calculateBarTranslation()
            );

            offlineBar.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(
                            SHOW_DURATION
                    )
                    .setInterpolator(
                            PREMIUM_INTERPOLATOR
                    )
                    .start();
        });

        notifyVisibilityChanged(
                true
        );
    }

    // =========================================================
    // 🔄 HIDE
    // =========================================================

    public void hideWithAnimation() {

        if (offlineBar == null) {
            return;
        }

        activity.runOnUiThread(() -> {

            if (activity.isFinishing()) {
                return;
            }

            offlineBar.animate().cancel();

            /*
             * Restore state.
             */
            applyBarBackground(
                    RESTORED_TOP,
                    RESTORED_BOTTOM
            );

            offlineBar.setText(
                    RESTORED_TEXT
            );

            /*
             * لا نستخدم translationY = 100.
             *
             * لأن ذلك كان يجعل الحركة
             * مرتبطة بحجم ثابت خاطئ.
             */
            offlineBar.animate()
                    .alpha(0f)
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(
                            HIDE_DURATION
                    )
                    .setInterpolator(
                            PREMIUM_INTERPOLATOR
                    )
                    .withEndAction(() -> {

                        if (offlineBar == null) {
                            return;
                        }

                        offlineBar.setVisibility(
                                View.GONE
                        );

                        offlineBar.setAlpha(
                                1f
                        );

                        offlineBar.setScaleX(
                                1f
                        );

                        offlineBar.setScaleY(
                                1f
                        );

                        offlineBar.setTranslationY(
                                calculateBarTranslation()
                        );

                        applyBarBackground(
                                BAR_TOP,
                                BAR_BOTTOM
                        );

                        offlineBar.setText(
                                DEFAULT_TEXT
                        );
                    })
                    .start();
        });

        notifyVisibilityChanged(
                false
        );
    }

    // =========================================================
    // ⚡ HIDE IMMEDIATELY
    // =========================================================

    public void hideImmediately() {

        if (offlineBar == null) {
            return;
        }

        activity.runOnUiThread(() -> {

            offlineBar.animate().cancel();

            offlineBar.setVisibility(
                    View.GONE
            );

            offlineBar.setAlpha(
                    1f
            );

            offlineBar.setScaleX(
                    1f
            );

            offlineBar.setScaleY(
                    1f
            );

            offlineBar.setTranslationY(
                    calculateBarTranslation()
            );

            applyBarBackground(
                    BAR_TOP,
                    BAR_BOTTOM
            );

            offlineBar.setText(
                    DEFAULT_TEXT
            );

            if (bottomVeil != null) {

                bottomVeil.setAlpha(
                        0f
                );

                bottomVeil.setVisibility(
                        View.GONE
                );
            }
        });

        notifyVisibilityChanged(
                false
        );
    }

    // =========================================================
    // 🔵 ONLINE TRANSITION
    // =========================================================

    public void showOnlineTransition() {

        if (offlineBar == null) {
            return;
        }

        activity.runOnUiThread(() -> {

            if (activity.isFinishing()) {
                return;
            }

            mainHandler.removeCallbacksAndMessages(
                    null
            );

            applyBarBackground(
                    RESTORED_TOP,
                    RESTORED_BOTTOM
            );

            offlineBar.setText(
                    RESTORED_TEXT
            );

            offlineBar.setTranslationY(
                    calculateBarTranslation()
            );

            if (offlineBar.getVisibility()
                    != View.VISIBLE) {

                offlineBar.setVisibility(
                        View.VISIBLE
                );

                offlineBar.setAlpha(
                        0f
                );

                offlineBar.setScaleX(
                        0.98f
                );

                offlineBar.setScaleY(
                        0.98f
                );

                offlineBar.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(
                                RESTORE_DURATION
                        )
                        .setInterpolator(
                                PREMIUM_INTERPOLATOR
                        )
                        .start();

            } else {

                offlineBar.animate()
                        .scaleX(1.015f)
                        .scaleY(1.015f)
                        .setDuration(110)
                        .setInterpolator(
                                PREMIUM_INTERPOLATOR
                        )
                        .withEndAction(() -> {

                            if (offlineBar == null) {
                                return;
                            }

                            offlineBar.animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(150)
                                    .setInterpolator(
                                            PREMIUM_INTERPOLATOR
                                    )
                                    .start();
                        })
                        .start();
            }

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
    // ⚠️ SHAKE
    // =========================================================

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

            offlineBar.animate().cancel();

            String originalText =
                    offlineBar.getText().toString();

            offlineBar.setText(
                    WARNING_TEXT
            );

            offlineBar.animate()
                    .translationX(dp(7))
                    .setDuration(55)
                    .withEndAction(() ->
                            offlineBar.animate()
                                    .translationX(dp(-7))
                                    .setDuration(55)
                                    .withEndAction(() ->
                                            offlineBar.animate()
                                                    .translationX(0)
                                                    .setDuration(65)
                                                    .start()
                                    )
                                    .start()
                    )
                    .start();

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
    // 🔍 STATE
    // =========================================================

    public boolean isVisible() {

        return offlineBar != null &&
                offlineBar.getVisibility()
                        == View.VISIBLE;
    }

    public TextView getView() {

        return offlineBar;
    }

    public View getVeilView() {

        return bottomVeil;
    }

    public boolean isGestureNavigation() {

        return gestureNavigation;
    }

    public int getNavigationBottomInset() {

        return navigationBottomInset;
    }

    // =========================================================
    // 🔗 CALLBACK
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
    // 🧹 DESTROY
    // =========================================================

    public void destroy() {

        if (mainHandler != null) {

            mainHandler.removeCallbacksAndMessages(
                    null
            );
        }

        if (offlineBar != null) {

            offlineBar.animate().cancel();
        }

        if (bottomVeil != null) {

            bottomVeil.animate().cancel();
        }

        if (overlayRoot != null) {

            ViewGroup parent =
                    (ViewGroup) overlayRoot.getParent();

            if (parent != null) {

                parent.removeView(
                        overlayRoot
                );
            }
        }

        offlineBar = null;
        bottomVeil = null;
        overlayRoot = null;
        callback = null;
        initialized = false;
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
