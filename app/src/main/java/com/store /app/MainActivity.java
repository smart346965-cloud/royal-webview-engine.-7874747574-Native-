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
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.store.app.offline.OfflineUIController;
import com.store.app.offline.OfflineStateManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG =
            "ROYAL_MAIN_DIAG";

    private static final long FIXED_SPLASH_TIME =
            5000L;

    private boolean splashRemoved = false;
    private boolean isPageLoaded = false;
    private boolean webViewReady = false;
    private boolean visualStateReady = false;

    private WebEngineManager engineManager;
    private RoyalCapabilitiesEngine capabilitiesEngine;
    private WebView activeWebView;
    private ProgressBar progressBar;

    private long splashStartTime = 0;

    private OfflineUIController offlineController;
    private RoyalAuthManager royalAuthManager;

    private FrameLayout rootContainer;

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    // =========================================================
    // CREATE
    // =========================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        final SplashScreen splashScreen =
                SplashScreen.installSplashScreen(this);

        splashStartTime =
                System.currentTimeMillis();

        splashScreen.setKeepOnScreenCondition(
                () ->
                        System.currentTimeMillis()
                                - splashStartTime
                                < FIXED_SPLASH_TIME
        );

        splashScreen.setOnExitAnimationListener(
                splashScreenView -> {

                    splashScreenView
                            .getView()
                            .animate()
                            .alpha(0f)
                            .setDuration(500L)
                            .withEndAction(
                                    splashScreenView::remove
                            )
                            .start();
                }
        );

        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(
                getWindow(),
                false
        );

        int initialColor =
                SystemUI.getDefaultSystemColor(
                        this
                );

        getWindow().setBackgroundDrawable(
                new ColorDrawable(initialColor)
        );

        diag(
                "01_CREATE",
                "Activity created | initial="
                        + hex(initialColor)
        );

        try {

            RoyalPanopticon.startAwareness();

            diag(
                    "02_PANOPTICON",
                    "started"
            );

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "STEP[02_PANOPTICON] failed",
                    e
            );
        }

        rootContainer =
                new FrameLayout(this);

        rootContainer.setBackgroundColor(
                initialColor
        );

        View topVisualSurface =
                new View(this);

        topVisualSurface.setId(
                View.generateViewId()
        );

        topVisualSurface.setTag(
                "TOP_VISUAL_SURFACE"
        );

        topVisualSurface.setBackgroundColor(
                initialColor
        );

        FrameLayout.LayoutParams surfaceParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0
                );

        topVisualSurface.setLayoutParams(
                surfaceParams
        );

        setContentView(rootContainer);

        ViewCompat.setOnApplyWindowInsetsListener(
                rootContainer,
                (v, insets) -> {

                    int insetTop =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.statusBars()
                                            | WindowInsetsCompat.Type.displayCutout()
                            ).top;

                    ViewGroup.LayoutParams lp =
                            topVisualSurface
                                    .getLayoutParams();

                    if (lp.height != insetTop) {

                        lp.height =
                                insetTop;

                        topVisualSurface
                                .setLayoutParams(lp);
                    }

                    diag(
                            "03_INSETS",
                            "top=" + insetTop
                    );

                    return insets;
                }
        );

        rootContainer.addView(
                topVisualSurface
        );

        RoyalWebViewHost.whenStartupReady(
                () ->
                        initializeWebView(
                                savedInstanceState
                        )
        );
    }

    // =========================================================
    // WEBVIEW INITIALIZATION
    // =========================================================

    private void initializeWebView(
            Bundle savedInstanceState
    ) {

        if (isFinishing() ||
                (Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.JELLY_BEAN_MR1
                        && isDestroyed())) {

            diag(
                    "04_WEB_INIT",
                    "ABORTED: Activity destroyed"
            );

            return;
        }

        RoyalWebViewHost.create(this);

        activeWebView =
                RoyalWebViewHost.attach(this);

        diag(
                "05_WEB_ATTACH",
                "WebView attached"
        );

        rootContainer.addView(
                activeWebView,
                0,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        ViewCompat.setOnApplyWindowInsetsListener(
                activeWebView,
                (v, insets) -> {

                    int insetTop =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.statusBars()
                                            | WindowInsetsCompat.Type.displayCutout()
                            ).top;

                    ViewGroup.LayoutParams lp =
                            v.getLayoutParams();

                    if (lp instanceof
                            ViewGroup.MarginLayoutParams) {

                        ViewGroup.MarginLayoutParams params =
                                (ViewGroup.MarginLayoutParams) lp;

                        if (params.topMargin !=
                                insetTop) {

                            params.topMargin =
                                    insetTop;

                            v.setLayoutParams(
                                    params
                            );
                        }
                    }

                    return insets;
                }
        );

        int initialColor =
                SystemUI.getDefaultSystemColor(
                        this
                );

        SystemUI.applyKingMode(
                this,
                activeWebView,
                initialColor
        );

        SystemUI.applyHeaderColor(
                this,
                initialColor
        );

        diag(
                "06_SYSTEM_UI",
                "initialized"
        );

        engineManager =
                new WebEngineManager(
                        this,
                        activeWebView,
                        null,
                        null,
                        () ->
                                splashRemoved = true,
                        () ->
                                splashRemoved
                );

        capabilitiesEngine =
                engineManager
                        .getCapabilitiesHandler();

        RoyalWebViewHost.bindEngineManager(
                engineManager
        );

        activeWebView.addJavascriptInterface(
                new RoyalJsBridge(
                        activeWebView,
                        engineManager
                ),
                "RoyalJsBridge"
        );

        engineManager.setSplashStartTime(
                splashStartTime
        );

        engineManager.init();

        new com.store.app.navigation.RoyalBackNavigation(
                this,
                activeWebView,
                engineManager,
                null
        ).setupBackNavigation();

        // =====================================================
        // RESTORE / LOAD
        // =====================================================

        boolean restored = false;

        if (savedInstanceState != null) {

            try {

                activeWebView.restoreState(
                        savedInstanceState
                );

                restored = true;
                isPageLoaded = true;

                diag(
                        "07_RESTORE",
                        "WebView state restored"
                );

            } catch (Throwable t) {

                Log.w(
                        TAG,
                        "STEP[07_RESTORE] failed",
                        t
                );
            }
        }

        if (!restored) {

            restored =
                    RoyalSessionSentinel.resurrect(
                            activeWebView,
                            this
                    );

            if (restored) {

                isPageLoaded = true;

                diag(
                        "08_SESSION",
                        "session resurrected"
                );
            }
        }

        if (!restored) {

            activeWebView.loadUrl(
                    BuildConfig.CLIENT_URL
            );

            /*
             * Web owns Status Bar هنا.
             */
            SystemUI.scheduleStatusBarSync(
                    MainActivity.this,
                    activeWebView
            );

            isPageLoaded = true;

            diag(
                    "09_INITIAL_LOAD",
                    BuildConfig.CLIENT_URL
            );
        }

        // =====================================================
        // OFFLINE
        // =====================================================

        NetworkMonitor.init(this);

        offlineController =
                new OfflineUIController(
                        this,
                        activeWebView,
                        engineManager
                );

        offlineController.init();

        OfflineStateManager
                .getInstance()
                .bind(
                        activeWebView,
                        offlineController
                );

        diag(
                "10_OFFLINE",
                "controller initialized"
        );

        // =====================================================
        // AUTH
        // =====================================================

        royalAuthManager =
                new RoyalAuthManager(
                        this,
                        getApplicationContext()
                );

        handleInitialAuthIntent(
                getIntent()
        );

        // =====================================================
        // INITIAL OFFLINE STATE
        // =====================================================

        if (!NetworkMonitor
                .isInternetAvailable(this)) {

            diag(
                    "11_OFFLINE_STATE",
                    "OFFLINE → Native UI requested"
            );

            /*
             * مهم:
             *
             * هنا نثبت ملكية Native.
             * WebView لن يستطيع الكتابة فوقها.
             *
             * لون مؤقت للتشخيص فقط:
             * سنرى هل الوميض يأتي من هذه النقطة.
             */
            SystemUI.syncWithNativeUI(
                    this,
                    initialColor
            );

            offlineController
                    .setOfflineUIVisibility(
                            true
                    );
        }
    }

    // =========================================================
    // PAUSE
    // =========================================================

    @Override
    protected void onPause() {

        diag(
                "12_PAUSE",
                "Activity pause"
        );

        if (activeWebView != null) {
            activeWebView.onPause();
        }

        super.onPause();
    }

    // =========================================================
    // RESUME
    // =========================================================

    @Override
    protected void onResume() {

        super.onResume();

        diag(
                "13_RESUME",
                "Activity resume"
        );

        if (activeWebView != null) {

            activeWebView.onResume();

            /*
             * لا نغير owner هنا.
             *
             * هذه هي النقطة التي كانت تسبب جزءًا مهمًا
             * من الـ race condition.
             */
            SystemUI.restoreHeaderOnResume(
                    this
            );

            SystemUI.scheduleStatusBarSync(
                    this,
                    activeWebView
            );
        }

        if (offlineController != null) {

            offlineController.onResume();
        }

        if (!isPageLoaded
                && activeWebView != null
                && activeWebView.getUrl() == null) {

            activeWebView.loadUrl(
                    BuildConfig.CLIENT_URL
            );

            isPageLoaded = true;

            diag(
                    "14_RESUME_LOAD",
                    "CLIENT_URL loaded"
            );
        }
    }

    // =========================================================
    // DESTROY
    // =========================================================

    @Override
    protected void onDestroy() {

        diag(
                "15_DESTROY",
                "Activity destroying"
        );

        SystemUI.cancelStatusBarSync();

        if (capabilitiesEngine != null) {
            capabilitiesEngine.destroy();
        }

        mainHandler.removeCallbacksAndMessages(
                null
        );

        if (activeWebView != null) {
            activeWebView.stopLoading();
        }

        if (offlineController != null) {

            offlineController.destroy();

            offlineController = null;
        }

        OfflineStateManager
                .getInstance()
                .unbind();

        if (royalAuthManager != null) {

            royalAuthManager.destroy();

            royalAuthManager = null;
        }

        if (!isChangingConfigurations()) {

            RoyalWebViewHost.detach();
        }

        activeWebView = null;

        super.onDestroy();
    }

    // =========================================================
    // SAVE STATE
    // =========================================================

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {

        if (activeWebView != null) {

            try {

                if (androidx.webkit.WebViewFeature
                        .isFeatureSupported(
                                androidx.webkit.WebViewFeature
                                        .SAVE_STATE
                        )) {

                    androidx.webkit.WebViewCompat.saveState(
                            activeWebView,
                            outState,
                            1024 * 1024,
                            false
                    );

                } else {

                    activeWebView.saveState(
                            outState
                    );
                }

            } catch (Throwable t) {

                Log.w(
                        TAG,
                        "STEP[16_SAVE] WebView state save failed.",
                        t
                );
            }
        }

        super.onSaveInstanceState(
                outState
        );
    }

    // =========================================================
    // ACTIVITY RESULT
    // =========================================================

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (capabilitiesEngine != null
                && capabilitiesEngine
                .handleActivityResult(
                        requestCode,
                        resultCode,
                        data
                )) {

            return;
        }
    }

    // =========================================================
    // NEW INTENT
    // =========================================================

    @Override
    protected void onNewIntent(
            Intent intent
    ) {

        super.onNewIntent(intent);

        setIntent(intent);

        if (intent == null) {
            return;
        }

        Uri data =
                intent.getData();

        if (data == null) {
            return;
        }

        diag(
                "17_NEW_INTENT",
                data.toString()
        );

        if (royalAuthManager != null) {

            boolean handled =
                    royalAuthManager
                            .handleRedirectIntent(
                                    intent
                            );

            if (!handled
                    && activeWebView != null
                    && RoyalAuthManager
                    .isAuthCallback(data)) {

                dispatchAuthUrlToWebView(
                        data.toString()
                );
            }

        } else if (activeWebView != null
                && RoyalAuthManager
                .isAuthCallback(data)) {

            dispatchAuthUrlToWebView(
                    data.toString()
            );
        }
    }

    // =========================================================
    // INITIAL AUTH
    // =========================================================

    private void handleInitialAuthIntent(
            Intent intent
    ) {

        if (intent == null) {
            return;
        }

        Uri data =
                intent.getData();

        if (data == null) {
            return;
        }

        diag(
                "18_INITIAL_AUTH",
                data.toString()
        );

        if (royalAuthManager != null) {

            royalAuthManager
                    .handleRedirectIntent(
                            intent
                    );

        } else if (activeWebView != null
                && RoyalAuthManager
                .isAuthCallback(data)) {

            dispatchAuthUrlToWebView(
                    data.toString()
            );
        }
    }

    // =========================================================
    // PERMISSIONS
    // =========================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (capabilitiesEngine != null) {

            capabilitiesEngine
                    .handlePermissionResult(
                            requestCode,
                            permissions,
                            grantResults
                    );
        }
    }

    // =========================================================
    // AUTH CALLBACK
    // =========================================================

    public void dispatchAuthUrlToWebView(
            @NonNull String url
    ) {

        runOnUiThread(() -> {

            Intent intent =
                    new Intent(
                            this,
                            MainActivity.class
                    );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            );

            startActivity(intent);

            if (engineManager != null) {

                engineManager
                        .handleAuthReturn(url);

            } else if (activeWebView != null) {

                Log.i(
                        TAG,
                        "STEP[19_AUTH] dispatching callback"
                );

                activeWebView.loadUrl(url);

                android.webkit.CookieManager
                        .getInstance()
                        .flush();

            } else {

                Log.w(
                        TAG,
                        "STEP[19_AUTH] WebView unavailable"
                );
            }
        });
    }

    // =========================================================
    // 👑 DIAGNOSTIC HELPERS
    // =========================================================

    private void diag(
            String step,
            String message
    ) {

        Log.i(
                TAG,
                "STEP[" + step + "] "
                        + message
        );
    }

    private String hex(int color) {

        return String.format(
                "#%08X",
                color
        );
    }
            }
