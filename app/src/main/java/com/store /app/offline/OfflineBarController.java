package com.store.app.offline;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
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

public class OfflineBarController {
    private static final String DEFAULT_TEXT = "لا يتوفر اتصال بالإنترنت";
    private static final String RESTORED_TEXT = "🔄 تم استعادة الاتصال، جاري التحديث...";
    private static final String WARNING_TEXT = "⚠️ لا يمكن التحميل، تحقق من الاتصال";
    private static final int BAR_TOP = Color.parseColor("#2C2C2E");
    private static final int BAR_BOTTOM = Color.parseColor("#1C1C1E");
    private static final int RESTORED_TOP = Color.parseColor("#283593");
    private static final int RESTORED_BOTTOM = Color.parseColor("#1A237E");
    private static final int TEXT_COLOR = Color.parseColor("#F2F2F7");
    private static final int BAR_HEIGHT_DP = 48;
    private static final int VEIL_MAX_DP = 64;

    private static final long SHOW_DURATION = 460L;
    private static final long HIDE_DURATION = 460L;
    private static final long RESTORE_DURATION = 260L;
    private static final TimeInterpolator PREMIUM_INTERPOLATOR = input -> {
        float t = input - 1f;
        return 1f + t * t * t + t * t;
    };

    private ValueAnimator navigationPositionAnimator;
    private int lastNavigationTarget = Integer.MIN_VALUE;
    private boolean systemInsetsAnimationRunning = false;

    private final Activity activity;
    private final Handler mainHandler;
    private TextView offlineBar;
    private View bottomVeil;
    private FrameLayout overlayRoot;
    private int navigationBottomInset = 0;
    private int gestureBottomInset = 0;
    private boolean navigationBarVisible = false;
    private boolean gestureNavigation = false;
    private boolean initialized = false;

    public interface VisibilityCallback { void onVisibilityChanged(boolean visible); }
    private VisibilityCallback callback;

    public OfflineBarController(Activity activity) {
        this.activity = activity;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void init() {
        if (activity == null || activity.isFinishing() || initialized) return;
        initialized = true;
        createOverlay();
        installInsetsController();
        overlayRoot.post(() -> {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(overlayRoot);
            if (insets != null) applyInitialInsets(insets);
        });
    }

    private void createOverlay() {
        overlayRoot = new FrameLayout(activity);
        overlayRoot.setClipChildren(false);
        overlayRoot.setClipToPadding(false);
        FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        activity.addContentView(overlayRoot, rootParams);
        createVeil();
        createBar();
    }

    private void createVeil() {
        bottomVeil = new View(activity);
        bottomVeil.setVisibility(View.GONE);
        GradientDrawable veilDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{
                Color.argb(0, 28, 28, 30),
                Color.argb(32, 44, 44, 46),
                Color.argb(105, 28, 28, 30),
                Color.argb(205, 28, 28, 30)
        });
        bottomVeil.setBackground(veilDrawable);
        bottomVeil.setAlpha(0f);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(VEIL_MAX_DP));
        params.gravity = Gravity.BOTTOM;
        overlayRoot.addView(bottomVeil, params);
    }

    private void createBar() {
        offlineBar = new TextView(activity);
        offlineBar.setText(DEFAULT_TEXT);
        offlineBar.setTextColor(TEXT_COLOR);
        offlineBar.setTextSize(15f);
        offlineBar.setGravity(Gravity.CENTER);
        offlineBar.setSingleLine(true);
        offlineBar.setPadding(dp(20), 0, dp(20), 0);
        offlineBar.setAlpha(0f);
        offlineBar.setVisibility(View.GONE);
        offlineBar.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        applyBarBackground(BAR_TOP, BAR_BOTTOM);
        offlineBar.setElevation(dp(12));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(BAR_HEIGHT_DP));
        params.gravity = Gravity.BOTTOM;
        overlayRoot.addView(offlineBar, params);
    }

    private void applyBarBackground(int topColor, int bottomColor) {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{topColor, bottomColor});
        background.setCornerRadii(new float[]{dp(12), dp(12), dp(12), dp(12), 0, 0, 0, 0});
        background.setStroke(dp(1), Color.argb(26, 255, 255, 255));
        offlineBar.setBackground(background);
    }

    private void installInsetsController() {
        ViewCompat.setOnApplyWindowInsetsListener(overlayRoot, (view, insets) -> {
            updateInsets(insets, false);
            return insets;
        });
        ViewCompat.setWindowInsetsAnimationCallback(overlayRoot, new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
            @Override public void onPrepare(WindowInsetsAnimationCompat animation) { systemInsetsAnimationRunning = true; }
            @Override public WindowInsetsCompat onProgress(WindowInsetsCompat insets, List<WindowInsetsAnimationCompat> runningAnimations) {
                updateInsets(insets, true);
                return insets;
            }
            @Override public void onEnd(WindowInsetsAnimationCompat animation) {
                systemInsetsAnimationRunning = false;
                WindowInsetsCompat finalInsets = ViewCompat.getRootWindowInsets(overlayRoot);
                if (finalInsets != null) updateInsets(finalInsets, true);
            }
        });
    }

    private void updateInsets(WindowInsetsCompat insets, boolean animatePosition) {
        if (insets == null || overlayRoot == null) return;
        Insets navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
        Insets gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures());
        navigationBottomInset = navigation.bottom;
        gestureBottomInset = gestures.bottom;
        navigationBarVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars());
        gestureNavigation = navigationBottomInset == 0 && gestureBottomInset > 0;
        applyNavigationPosition(animatePosition);
    }

    private void applyInitialInsets(WindowInsetsCompat insets) {
        updateInsets(insets, false);
        if (offlineBar != null) {
            int target = calculateBarTranslation();
            offlineBar.setTranslationY(target);
            lastNavigationTarget = target;
        }
    }

    private int calculateBarTranslation() {
        if (navigationBarVisible && navigationBottomInset > 0) return -navigationBottomInset;
        return 0;
    }

    private void applyNavigationPosition(boolean animate) {
        if (offlineBar == null || overlayRoot == null) return;
        final int targetTranslation = calculateBarTranslation();
        if (offlineBar.getVisibility() != View.VISIBLE) {
            cancelNavigationPositionAnimation();
            offlineBar.setTranslationY(targetTranslation);
            lastNavigationTarget = targetTranslation;
            updateVeil(targetTranslation, false);
            return;
        }
        if (lastNavigationTarget == targetTranslation) return;
        if (lastNavigationTarget == Integer.MIN_VALUE) {
            cancelNavigationPositionAnimation();
            offlineBar.setTranslationY(targetTranslation);
            lastNavigationTarget = targetTranslation;
            updateVeil(targetTranslation, false);
            return;
        }
        if (animate) animateNavigationPosition(targetTranslation);
        else {
            cancelNavigationPositionAnimation();
            offlineBar.setTranslationY(targetTranslation);
            lastNavigationTarget = targetTranslation;
            updateVeil(targetTranslation, false);
        }
    }

    private void animateNavigationPosition(int targetTranslation) {
        if (offlineBar == null) return;
        if (lastNavigationTarget == targetTranslation && navigationPositionAnimator != null && navigationPositionAnimator.isRunning()) return;
        float current = offlineBar.getTranslationY();
        cancelNavigationPositionAnimation();
        navigationPositionAnimator = ValueAnimator.ofFloat(current, targetTranslation);
        navigationPositionAnimator.setDuration(targetTranslation < current ? SHOW_DURATION : HIDE_DURATION);
        navigationPositionAnimator.setInterpolator(PREMIUM_INTERPOLATOR);
        navigationPositionAnimator.addUpdateListener(animator -> {
            if (offlineBar == null) return;
            float value = (Float) animator.getAnimatedValue();
            offlineBar.setTranslationY(value);
            updateVeil(Math.round(value), true);
        });
        navigationPositionAnimator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (offlineBar == null) return;
                offlineBar.setTranslationY(targetTranslation);
                lastNavigationTarget = targetTranslation;
                updateVeil(targetTranslation, false);
                navigationPositionAnimator = null;
            }
            @Override public void onAnimationCancel(android.animation.Animator animation) { navigationPositionAnimator = null; }
        });
        lastNavigationTarget = targetTranslation;
        navigationPositionAnimator.start();
    }

    private void cancelNavigationPositionAnimation() {
        if (navigationPositionAnimator != null) {
            navigationPositionAnimator.cancel();
            navigationPositionAnimator = null;
        }
    }

    private void updateVeil(int barTranslation, boolean animated) {
        if (bottomVeil == null) return;
        if (navigationBarVisible && navigationBottomInset > 0) {
            int targetHeight = Math.min(dp(VEIL_MAX_DP), Math.max(dp(24), navigationBottomInset));
            float targetAlpha = Math.min(1f, navigationBottomInset / (float) dp(64));
            ViewGroup.LayoutParams lp = bottomVeil.getLayoutParams();
            if (lp == null) return;
            if (!animated) {
                lp.height = targetHeight;
                bottomVeil.setLayoutParams(lp);
                bottomVeil.setVisibility(View.VISIBLE);
                bottomVeil.setAlpha(targetAlpha);
                return;
            }
            lp.height = targetHeight;
            bottomVeil.setLayoutParams(lp);
            bottomVeil.setVisibility(View.VISIBLE);
            bottomVeil.setAlpha(targetAlpha);
        } else {
            bottomVeil.setAlpha(0f);
            bottomVeil.setVisibility(View.GONE);
        }
    }

    public void show() {
        if (offlineBar == null) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            mainHandler.removeCallbacksAndMessages(null);
            offlineBar.animate().cancel();
            offlineBar.setText(DEFAULT_TEXT);
            applyBarBackground(BAR_TOP, BAR_BOTTOM);
            offlineBar.setTranslationY(calculateBarTranslation());
            offlineBar.setScaleX(0.96f);
            offlineBar.setScaleY(0.96f);
            offlineBar.setAlpha(0f);
            offlineBar.setVisibility(View.VISIBLE);
            updateVeil(calculateBarTranslation(), false);
            offlineBar.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(SHOW_DURATION).setInterpolator(PREMIUM_INTERPOLATOR).start();
        });
        notifyVisibilityChanged(true);
    }

    public void hideWithAnimation() {
        if (offlineBar == null) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            offlineBar.animate().cancel();
            applyBarBackground(RESTORED_TOP, RESTORED_BOTTOM);
            offlineBar.setText(RESTORED_TEXT);
            offlineBar.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(HIDE_DURATION).setInterpolator(PREMIUM_INTERPOLATOR).withEndAction(() -> {
                if (offlineBar == null) return;
                offlineBar.setVisibility(View.GONE);
                offlineBar.setAlpha(1f);
                offlineBar.setScaleX(1f);
                offlineBar.setScaleY(1f);
                offlineBar.setTranslationY(calculateBarTranslation());
                applyBarBackground(BAR_TOP, BAR_BOTTOM);
                offlineBar.setText(DEFAULT_TEXT);
            }).start();
        });
        notifyVisibilityChanged(false);
    }

    public void hideImmediately() {
        if (offlineBar == null) return;
        activity.runOnUiThread(() -> {
            offlineBar.animate().cancel();
            offlineBar.setVisibility(View.GONE);
            offlineBar.setAlpha(1f);
            offlineBar.setScaleX(1f);
            offlineBar.setScaleY(1f);
            offlineBar.setTranslationY(calculateBarTranslation());
            applyBarBackground(BAR_TOP, BAR_BOTTOM);
            offlineBar.setText(DEFAULT_TEXT);
            if (bottomVeil != null) {
                bottomVeil.setAlpha(0f);
                bottomVeil.setVisibility(View.GONE);
            }
        });
        notifyVisibilityChanged(false);
    }

    public void showOnlineTransition() {
        if (offlineBar == null) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            mainHandler.removeCallbacksAndMessages(null);
            applyBarBackground(RESTORED_TOP, RESTORED_BOTTOM);
            offlineBar.setText(RESTORED_TEXT);
            offlineBar.setTranslationY(calculateBarTranslation());
            if (offlineBar.getVisibility() != View.VISIBLE) {
                offlineBar.setVisibility(View.VISIBLE);
                offlineBar.setAlpha(0f);
                offlineBar.setScaleX(0.98f);
                offlineBar.setScaleY(0.98f);
                offlineBar.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(RESTORE_DURATION).setInterpolator(PREMIUM_INTERPOLATOR).start();
            } else {
                offlineBar.animate().scaleX(1.015f).scaleY(1.015f).setDuration(110).setInterpolator(PREMIUM_INTERPOLATOR).withEndAction(() -> {
                    if (offlineBar == null) return;
                    offlineBar.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(PREMIUM_INTERPOLATOR).start();
                }).start();
            }
            mainHandler.postDelayed(() -> {
                if (offlineBar != null && offlineBar.getVisibility() == View.VISIBLE) hideWithAnimation();
            }, 900);
        });
    }

    public void shake() {
        if (offlineBar == null) return;
        if (offlineBar.getVisibility() != View.VISIBLE) show();
        activity.runOnUiThread(() -> {
            if (offlineBar == null) return;
            offlineBar.animate().cancel();
            String originalText = offlineBar.getText().toString();
            offlineBar.setText(WARNING_TEXT);
            offlineBar.animate().translationX(dp(7)).setDuration(55).withEndAction(() -> offlineBar.animate().translationX(dp(-7)).setDuration(55).withEndAction(() -> offlineBar.animate().translationX(0).setDuration(65).start()).start()).start();
            mainHandler.postDelayed(() -> {
                if (offlineBar != null && offlineBar.getVisibility() == View.VISIBLE) offlineBar.setText(originalText);
            }, 1800);
        });
    }

    public boolean isVisible() { return offlineBar != null && offlineBar.getVisibility() == View.VISIBLE; }
    public TextView getView() { return offlineBar; }
    public View getVeilView() { return bottomVeil; }
    public boolean isGestureNavigation() { return gestureNavigation; }
    public int getNavigationBottomInset() { return navigationBottomInset; }
    public void setCallback(VisibilityCallback callback) { this.callback = callback; }
    private void notifyVisibilityChanged(boolean visible) { if (callback != null) callback.onVisibilityChanged(visible); }

    public void destroy() {
        cancelNavigationPositionAnimation();
        if (offlineBar != null) offlineBar.animate().cancel();
        if (bottomVeil != null) bottomVeil.animate().cancel();
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);
        if (overlayRoot != null) {
            ViewGroup parent = (ViewGroup) overlayRoot.getParent();
            if (parent != null) parent.removeView(overlayRoot);
        }
        offlineBar = null;
        bottomVeil = null;
        overlayRoot = null;
        callback = null;
        initialized = false;
    }

    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
            }
