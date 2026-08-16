package com.store.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 👑 RoyalAuthManager
 *
 * مسؤول فقط عن:
 *
 * 1. فتح عمليات المصادقة الخارجية في Custom Tabs.
 * 2. فتح بوابات الدفع الحساسة في Custom Tabs.
 * 3. استقبال Redirect العائد إلى التطبيق.
 * 4. إعادة نتيجة العملية إلى MainActivity / WebView.
 *
 * مهم:
 * هذا الملف لا يحول التصفح العادي إلى Custom Tabs.
 *
 * يعتمد على:
 * androidx.browser:browser:1.8.0
 *
 * لا يعتمد على AuthTabIntent لأن Auth Tab API أضيفت
 * في AndroidX Browser 1.9.0.
 */
public final class RoyalAuthManager {

    private static final String TAG = "RoyalAuthManager";

    // =========================================================
    // 🔐 Deep Link Configuration
    // =========================================================

    /**
     * يجب أن يتطابق هذا الـ scheme مع AndroidManifest.
     *
     * مثال:
     *
     * nexusauth://callback
     */
    public static final String AUTH_SCHEME = "nexusauth";

    public static final String AUTH_HOST = "callback";

    /**
     * نوع العملية الموجودة حالياً.
     */
    private static final int FLOW_NONE = 0;
    private static final int FLOW_AUTH = 1;
    private static final int FLOW_PAYMENT = 2;

    // =========================================================
    // 🔥 Callbacks
    // =========================================================

    public interface AuthCallback {

        void onAuthSuccess(
                @NonNull String code,
                @Nullable String state
        );

        void onAuthError(@NonNull Exception exception);

        void onAuthCancel();
    }

    public interface PaymentCallback {

        void onPaymentSuccess(
                @NonNull String transactionId
        );

        void onPaymentError(
                @NonNull Exception exception
        );

        void onPaymentCancel();
    }

    // =========================================================
    // 🔥 Core State
    // =========================================================

    private final Activity activity;
    private final Context context;

    private final CustomTabsIntent customTabsIntent;

    private final AtomicReference<AuthCallback> pendingAuthCallback =
            new AtomicReference<>(null);

    private final AtomicReference<PaymentCallback> pendingPaymentCallback =
            new AtomicReference<>(null);

    private final AtomicReference<String> pendingState =
            new AtomicReference<>(null);

    private final AtomicBoolean flowActive =
            new AtomicBoolean(false);

    private volatile int currentFlow = FLOW_NONE;

    // =========================================================
    // 🚀 Constructor
    // =========================================================

    public RoyalAuthManager(
            @NonNull Activity activity,
            @NonNull Context context
    ) {

        this.activity = activity;
        this.context = context.getApplicationContext();

        this.customTabsIntent =
                new CustomTabsIntent.Builder()

                        .setToolbarColor(
                                Color.parseColor("#111111")
                        )

                        .enableUrlBarHiding()

                        .setStartAnimations(
                                activity,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                        )

                        .setExitAnimations(
                                activity,
                                android.R.anim.fade_in,
                                android.R.anim.fade_out
                        )

                        .addDefaultShareMenuItem()

                        .build();

        Log.i(
                TAG,
                "✅ RoyalAuthManager initialized - Browser 1.8 compatible"
        );
    }

    // =========================================================
    // 🔐 AUTH FLOW
    // =========================================================

    /**
     * إطلاق رابط تسجيل الدخول الخارجي.
     *
     * ملاحظة:
     * الرابط يجب أن يحتوي على redirect_uri الذي يعيد المستخدم
     * إلى:
     *
     * nexusauth://callback
     */
    public boolean launchAuthTab(
            @NonNull String url,
            @NonNull String state,
            @NonNull AuthCallback callback
    ) {

        if (flowActive.get()) {

            callback.onAuthError(
                    new IllegalStateException(
                            "Another sensitive flow is already active"
                    )
            );

            return false;
        }

        if (!isValidHttpUrl(url)) {

            callback.onAuthError(
                    new IllegalArgumentException(
                            "Invalid authentication URL"
                    )
            );

            return false;
        }

        pendingAuthCallback.set(callback);
        pendingState.set(state);

        pendingPaymentCallback.set(null);

        currentFlow = FLOW_AUTH;
        flowActive.set(true);

        try {

            Log.i(
                    TAG,
                    "🔐 Launching authentication Custom Tab"
            );

            customTabsIntent.launchUrl(
                    activity,
                    Uri.parse(url)
            );

            return true;

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "❌ Authentication Custom Tab launch failed",
                    e
            );

            clearFlow();

            callback.onAuthError(e);

            return false;
        }
    }

    // =========================================================
    // 💳 PAYMENT FLOW
    // =========================================================

    public boolean launchPaymentTab(
            @NonNull String url,
            @NonNull PaymentCallback callback
    ) {

        if (flowActive.get()) {

            callback.onPaymentError(
                    new IllegalStateException(
                            "Another sensitive flow is already active"
                    )
            );

            return false;
        }

        if (!isValidHttpUrl(url)) {

            callback.onPaymentError(
                    new IllegalArgumentException(
                            "Invalid payment URL"
                    )
            );

            return false;
        }

        pendingPaymentCallback.set(callback);
        pendingAuthCallback.set(null);
        pendingState.set(null);

        currentFlow = FLOW_PAYMENT;
        flowActive.set(true);

        try {

            Log.i(
                    TAG,
                    "💳 Launching payment Custom Tab"
            );

            customTabsIntent.launchUrl(
                    activity,
                    Uri.parse(url)
            );

            return true;

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "❌ Payment Custom Tab launch failed",
                    e
            );

            clearFlow();

            callback.onPaymentError(e);

            return false;
        }
    }

    // =========================================================
    // 🔗 CALLBACK ROUTER
    // =========================================================

    /**
     * هذه أهم دالة في النظام.
     *
     * MainActivity تستدعيها عند وصول Intent جديد.
     *
     * مثال:
     *
     * nexusauth://callback?code=ABC&state=XYZ
     */
    public boolean handleRedirect(
            @Nullable Intent intent
    ) {

        if (intent == null) {
            return false;
        }

        Uri uri = intent.getData();

        if (uri == null) {
            return false;
        }

        Log.i(
                TAG,
                "🔗 Redirect received: " + sanitizeUriForLog(uri)
        );

        if (!AUTH_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return false;
        }

        if (!AUTH_HOST.equalsIgnoreCase(uri.getHost())) {
            return false;
        }

        if (currentFlow == FLOW_AUTH) {

            handleAuthRedirect(uri);

            return true;
        }

        if (currentFlow == FLOW_PAYMENT) {

            handlePaymentRedirect(uri);

            return true;
        }

        Log.w(
                TAG,
                "⚠️ Redirect received without active flow"
        );

        return false;
    }

    // =========================================================
    // 🔐 AUTH REDIRECT
    // =========================================================

    private void handleAuthRedirect(
            @NonNull Uri uri
    ) {

        AuthCallback callback =
                pendingAuthCallback.getAndSet(null);

        String returnedState =
                uri.getQueryParameter("state");

        String expectedState =
                pendingState.get();

        /**
         * حماية مهمة جداً:
         *
         * لا نقبل callback إذا كان state مختلفاً.
         */
        if (expectedState != null) {

            if (returnedState == null ||
                    !constantTimeEquals(
                            expectedState,
                            returnedState
                    )) {

                clearFlow();

                if (callback != null) {

                    callback.onAuthError(
                            new SecurityException(
                                    "OAuth state validation failed"
                            )
                    );
                }

                return;
            }
        }

        String error =
                uri.getQueryParameter("error");

        if (error != null && !error.isEmpty()) {

            String description =
                    uri.getQueryParameter(
                            "error_description"
                    );

            clearFlow();

            if (callback != null) {

                callback.onAuthError(
                        new Exception(
                                description != null
                                        ? description
                                        : error
                        )
                );
            }

            return;
        }

        String code =
                uri.getQueryParameter("code");

        if (code == null || code.isEmpty()) {

            clearFlow();

            if (callback != null) {

                callback.onAuthError(
                        new Exception(
                                "OAuth callback did not contain authorization code"
                        )
                );
            }

            return;
        }

        clearFlow();

        if (callback != null) {

            callback.onAuthSuccess(
                    code,
                    returnedState
            );
        }
    }

    // =========================================================
    // 💳 PAYMENT REDIRECT
    // =========================================================

    private void handlePaymentRedirect(
            @NonNull Uri uri
    ) {

        PaymentCallback callback =
                pendingPaymentCallback.getAndSet(null);

        String error =
                uri.getQueryParameter("error");

        if (error != null && !error.isEmpty()) {

            String description =
                    uri.getQueryParameter(
                            "error_description"
                    );

            clearFlow();

            if (callback != null) {

                callback.onPaymentError(
                        new Exception(
                                description != null
                                        ? description
                                        : error
                        )
                );
            }

            return;
        }

        String transactionId =
                uri.getQueryParameter(
                        "transaction_id"
                );

        if (transactionId == null ||
                transactionId.isEmpty()) {

            clearFlow();

            if (callback != null) {

                callback.onPaymentError(
                        new Exception(
                                "Payment callback did not contain transaction_id"
                        )
                );
            }

            return;
        }

        clearFlow();

        if (callback != null) {

            callback.onPaymentSuccess(
                    transactionId
            );
        }
    }

    // =========================================================
    // ❌ CANCEL
    // =========================================================

    /**
     * استدعاؤها عندما يقرر المستخدم إغلاق
     * العملية الحساسة قبل إتمامها.
     */
    public void notifyFlowCancelled() {

        int flow = currentFlow;

        AuthCallback authCallback =
                pendingAuthCallback.getAndSet(null);

        PaymentCallback paymentCallback =
                pendingPaymentCallback.getAndSet(null);

        clearFlow();

        if (flow == FLOW_AUTH) {

            if (authCallback != null) {
                authCallback.onAuthCancel();
            }

        } else if (flow == FLOW_PAYMENT) {

            if (paymentCallback != null) {
                paymentCallback.onPaymentCancel();
            }
        }
    }

    // =========================================================
    // 🔎 STATE
    // =========================================================

    public boolean isFlowActive() {
        return flowActive.get();
    }

    public boolean isAuthFlowActive() {
        return currentFlow == FLOW_AUTH &&
                flowActive.get();
    }

    public boolean isPaymentFlowActive() {
        return currentFlow == FLOW_PAYMENT &&
                flowActive.get();
    }

    // =========================================================
    // 🧹 CLEAR
    // =========================================================

    private void clearFlow() {

        pendingAuthCallback.set(null);
        pendingPaymentCallback.set(null);
        pendingState.set(null);

        currentFlow = FLOW_NONE;

        flowActive.set(false);
    }

    // =========================================================
    // 🔒 URL VALIDATION
    // =========================================================

    private boolean isValidHttpUrl(
            @NonNull String url
    ) {

        try {

            Uri uri = Uri.parse(url);

            String scheme = uri.getScheme();

            return "https".equalsIgnoreCase(scheme);

        } catch (Exception e) {

            return false;
        }
    }

    // =========================================================
    // 🔐 CONSTANT TIME STATE COMPARISON
    // =========================================================

    private boolean constantTimeEquals(
            @NonNull String a,
            @NonNull String b
    ) {

        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;

        for (int i = 0; i < a.length(); i++) {

            result |= a.charAt(i) ^ b.charAt(i);
        }

        return result == 0;
    }

    // =========================================================
    // 🛡️ SAFE LOGGING
    // =========================================================

    private String sanitizeUriForLog(
            @NonNull Uri uri
    ) {

        Uri.Builder builder =
                uri.buildUpon()
                        .clearQuery();

        return builder.build().toString();
    }

    // =========================================================
    // 🧹 DESTROY
    // =========================================================

    public void destroy() {

        clearFlow();

        Log.i(
                TAG,
                "🧹 RoyalAuthManager destroyed"
        );
    }
    }
