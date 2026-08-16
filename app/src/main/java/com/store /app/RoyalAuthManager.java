package com.store.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 👑 RoyalAuthManager
 *
 * Nexus Sensitive Flow Controller
 *
 * المسؤول عن:
 *
 * 1. إدارة تدفقات المصادقة الحساسة.
 * 2. إطلاق External User-Agent عبر Custom Tabs.
 * 3. إنشاء والتحقق من state عالي entropy.
 * 4. استقبال Deep Link callback.
 * 5. منع replay / callback injection.
 * 6. التحقق الصارم من scheme + host + path.
 * 7. استقبال Nexus One-Time Handoff Ticket.
 * 8. فصل AUTH عن PAYMENT.
 * 9. عدم معرفة أي OAuth Provider.
 *
 * مهم جداً:
 *
 * هذا الملف لا يعرف Google.
 * هذا الملف لا يعرف Apple.
 * هذا الملف لا يعرف Microsoft.
 * هذا الملف لا يعرف LinkedIn.
 * هذا الملف لا يعرف Shopify / Salla / WooCommerce / Laravel...
 *
 * Nexus Bridge هو الذي يعرف كيفية إنهاء عملية المصادقة
 * على موقع العميل وإنشاء One-Time Handoff Ticket.
 *
 * Browser:
 *
 * androidx.browser:browser:1.8.0
 *
 * لاحقاً يمكن إضافة Auth Tab كمسار أحدث
 * بدون إعادة تصميم هذا الـ Manager.
 */
public final class RoyalAuthManager {

    private static final String TAG = "RoyalAuthManager";

    // =========================================================
    // 🔐 CALLBACK CONFIGURATION
    // =========================================================

    /**
     * يجب أن يتطابق حرفياً مع AndroidManifest.xml
     *
     * com.store.app.auth://callback
     */
    public static final String AUTH_SCHEME =
            "com.store.app.auth";

    public static final String AUTH_HOST =
            "callback";

    /**
     * المسار المتوقع.
     *
     * نستخدم:
     *
     * com.store.app.auth://callback
     *
     * لذلك path الطبيعي يكون فارغاً أو "/".
     */
    private static final String AUTH_PATH = "";

    // =========================================================
    // 🔐 QUERY PARAMETERS
    // =========================================================

    private static final String PARAM_STATE =
            "state";

    private static final String PARAM_TICKET =
            "ticket";

    private static final String PARAM_ERROR =
            "error";

    private static final String PARAM_ERROR_DESCRIPTION =
            "error_description";

    /**
     * لا نقبل access_token / id_token / session_cookie
     * داخل callback.
     */
    private static final String PARAM_ACCESS_TOKEN =
            "access_token";

    private static final String PARAM_ID_TOKEN =
            "id_token";

    private static final String PARAM_SESSION_COOKIE =
            "session_cookie";

    private static final String PARAM_PASSWORD =
            "password";

    private static final String PARAM_CODE =
            "code";

    // =========================================================
    // ⏱ FLOW SECURITY
    // =========================================================

    /**
     * مدة صلاحية عملية المصادقة.
     *
     * 5 دقائق كافية عادة لتدفق Login.
     */
    private static final long AUTH_FLOW_TIMEOUT_MS =
            5L * 60L * 1000L;

    /**
     * مدة صلاحية عملية الدفع.
     *
     * الدفع قد يحتاج وقتاً أطول قليلاً.
     */
    private static final long PAYMENT_FLOW_TIMEOUT_MS =
            15L * 60L * 1000L;

    /**
     * طول state بالبايت.
     *
     * 32 bytes = 256 bits entropy.
     */
    private static final int STATE_BYTES = 32;

    /**
     * طول nonce الداخلي المستخدم لربط العملية.
     */
    private static final int FLOW_NONCE_BYTES = 32;

    // =========================================================
    // 🔥 FLOW TYPES
    // =========================================================

    private static final int FLOW_NONE = 0;
    private static final int FLOW_AUTH = 1;
    private static final int FLOW_PAYMENT = 2;

    // =========================================================
    // 🔥 AUTH CALLBACK
    // =========================================================

    /**
     * النتيجة الناجحة ليست OAuth access token.
     *
     * إنها Nexus One-Time Handoff Ticket.
     *
     * الـ Ticket يستخدمه Nexus Bridge / backend
     * لإتمام session bootstrap.
     */
    public interface AuthCallback {

        void onAuthSuccess(
                @NonNull String ticket,
                @NonNull String state
        );

        void onAuthError(
                @NonNull Exception exception
        );

        void onAuthCancel();
    }

    // =========================================================
    // 💳 PAYMENT CALLBACK
    // =========================================================

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
    // 🔐 FLOW SESSION
    // =========================================================

    private static final class FlowSession {

        final int flowType;

        final String state;

        final String nonce;

        final long createdAtElapsed;

        final long expiresAtElapsed;

        /**
         * الرابط الذي بدأنا منه العملية.
         *
         * لا يتم تسجيله كاملاً في Log.
         */
        final Uri launchUri;

        FlowSession(
                int flowType,
                @NonNull String state,
                @NonNull String nonce,
                long createdAtElapsed,
                long expiresAtElapsed,
                @NonNull Uri launchUri
        ) {

            this.flowType = flowType;
            this.state = state;
            this.nonce = nonce;
            this.createdAtElapsed = createdAtElapsed;
            this.expiresAtElapsed = expiresAtElapsed;
            this.launchUri = launchUri;
        }

        boolean isExpired() {

            return SystemClock.elapsedRealtime()
                    > expiresAtElapsed;
        }
    }

    // =========================================================
    // 🚀 CORE
    // =========================================================

    private final Activity activity;

    private final Context context;

    private final CustomTabsIntent customTabsIntent;

    private final SecureRandom secureRandom;

    private final AtomicReference<AuthCallback>
            pendingAuthCallback =
            new AtomicReference<>(null);

    private final AtomicReference<PaymentCallback>
            pendingPaymentCallback =
            new AtomicReference<>(null);

    private final AtomicReference<FlowSession>
            activeSession =
            new AtomicReference<>(null);

    private final AtomicBoolean destroyed =
            new AtomicBoolean(false);

    /**
     * يمنع معالجة callback ثانية لنفس العملية
     * حتى لو وصل Intent مكرر.
     */
    private final AtomicBoolean callbackConsumed =
            new AtomicBoolean(false);

    // =========================================================
    // 🚀 CONSTRUCTOR
    // =========================================================

    public RoyalAuthManager(
            @NonNull Activity activity,
            @NonNull Context context
    ) {

        this.activity = activity;

        this.context =
                context.getApplicationContext();

        this.secureRandom =
                new SecureRandom();

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

                        /*
                         * لا نضيف share menu في تدفق المصادقة.
                         *
                         * تقليل الخيارات داخل sensitive flow
                         * أفضل من ناحية UX والأمان.
                         */

                        .build();

        Log.i(
                TAG,
                "RoyalAuthManager initialized"
        );
    }

    // =========================================================
    // 🔐 AUTH FLOW
    // =========================================================

    /**
     * إطلاق Sensitive Authentication Flow.
     *
     * الـ state يتم توليده داخلياً.
     *
     * لا نقبل state من caller.
     *
     * وهذا مهم جداً حتى لا يصبح caller نقطة ضعف.
     */
    public boolean launchAuthTab(
            @NonNull String url,
            @NonNull AuthCallback callback
    ) {

        if (destroyed.get()) {

            callback.onAuthError(
                    new IllegalStateException(
                            "RoyalAuthManager has been destroyed"
                    )
            );

            return false;
        }

        if (callback == null) {
            return false;
        }

        if (!isValidHttpsUrl(url)) {

            callback.onAuthError(
                    new IllegalArgumentException(
                            "Authentication URL must use HTTPS"
                    )
            );

            return false;
        }

        if (!beginExclusiveFlow(FLOW_AUTH)) {

            callback.onAuthError(
                    new IllegalStateException(
                            "Another sensitive flow is already active"
                    )
            );

            return false;
        }

        final String state =
                generateSecureRandomToken(
                        STATE_BYTES
                );

        final String nonce =
                generateSecureRandomToken(
                        FLOW_NONCE_BYTES
                );

        final Uri uri =
                Uri.parse(url);

        final long now =
                SystemClock.elapsedRealtime();

        final FlowSession session =
                new FlowSession(
                        FLOW_AUTH,
                        state,
                        nonce,
                        now,
                        now + AUTH_FLOW_TIMEOUT_MS,
                        uri
                );

        activeSession.set(session);

        callbackConsumed.set(false);

        pendingAuthCallback.set(callback);

        pendingPaymentCallback.set(null);

        /*
         * لا نعتمد على أن caller أضاف state.
         *
         * Nexus يضيف state بنفسه.
         */
        Uri finalUri =
                appendOrReplaceQueryParameter(
                        uri,
                        PARAM_STATE,
                        state
                );

        try {

            Log.i(
                    TAG,
                    "Launching AUTH Custom Tab"
            );

            customTabsIntent.launchUrl(
                    activity,
                    finalUri
            );

            return true;

        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "Authentication Custom Tab launch failed",
                    e
            );

            clearActiveFlow();

            callback.onAuthError(
                    new IllegalStateException(
                            "Unable to launch authentication flow",
                            e
                    )
            );

            return false;
        }
    }

    // =========================================================
    // 🔐 GENERIC SENSITIVE FLOW
    // =========================================================

    /**
     * نقطة مهمة جداً لـ Nexus:
     *
     * WebEngineManager لا يحتاج معرفة Google أو Apple
     * أو أي Provider.
     *
     * عندما يقرر Navigation Classifier أن الرابط
     * Sensitive، يمكنه تمرير الرابط إلى هذه الدالة.
     *
     * هذه الدالة تبدأ AUTH flow عام.
     *
     * لاحقاً Nexus Bridge هو الذي يحدد كيفية إنهاء العملية.
     */
    public boolean launchSensitiveFlow(
            @NonNull String url,
            @NonNull AuthCallback callback
    ) {

        return launchAuthTab(
                url,
                callback
        );
    }

    /**
     * overload مناسب إذا كان caller يريد فقط إطلاق
     * sensitive flow ولا يحتاج callback UI مباشر.
     *
     * النتيجة ستصل لاحقاً عبر handleRedirectIntent().
     */
    public boolean launchSensitiveFlow(
            @NonNull String url
    ) {

        return launchAuthTab(
                url,
                new AuthCallback() {

                    @Override
                    public void onAuthSuccess(
                            @NonNull String ticket,
                            @NonNull String state
                    ) {

                        Log.i(
                                TAG,
                                "Sensitive flow completed"
                        );
                    }

                    @Override
                    public void onAuthError(
                            @NonNull Exception exception
                    ) {

                        Log.w(
                                TAG,
                                "Sensitive flow failed",
                                exception
                        );
                    }

                    @Override
                    public void onAuthCancel() {

                        Log.i(
                                TAG,
                                "Sensitive flow cancelled"
                        );
                    }
                }
        );
    }

    // =========================================================
    // 💳 PAYMENT FLOW
    // =========================================================

    public boolean launchPaymentTab(
            @NonNull String url,
            @NonNull PaymentCallback callback
    ) {

        if (destroyed.get()) {

            callback.onPaymentError(
                    new IllegalStateException(
                            "RoyalAuthManager has been destroyed"
                    )
            );

            return false;
        }

        if (!isValidHttpsUrl(url)) {

            callback.onPaymentError(
                    new IllegalArgumentException(
                            "Payment URL must use HTTPS"
                    )
            );

            return false;
        }

        if (!beginExclusiveFlow(FLOW_PAYMENT)) {

            callback.onPaymentError(
                    new IllegalStateException(
                            "Another sensitive flow is already active"
                    )
            );

            return false;
        }

        final String state =
                generateSecureRandomToken(
                        STATE_BYTES
                );

        final String nonce =
                generateSecureRandomToken(
                        FLOW_NONCE_BYTES
                );

        final Uri uri =
                Uri.parse(url);

        final long now =
                SystemClock.elapsedRealtime();

        final FlowSession session =
                new FlowSession(
                        FLOW_PAYMENT,
                        state,
                        nonce,
                        now,
                        now + PAYMENT_FLOW_TIMEOUT_MS,
                        uri
                );

        activeSession.set(session);

        callbackConsumed.set(false);

        pendingPaymentCallback.set(callback);

        pendingAuthCallback.set(null);

        Uri finalUri =
                appendOrReplaceQueryParameter(
                        uri,
                        PARAM_STATE,
                        state
                );

        try {

            Log.i(
                    TAG,
                    "Launching PAYMENT Custom Tab"
            );

            customTabsIntent.launchUrl(
                    activity,
                    finalUri
            );

            return true;

        } catch (Throwable e) {

            Log.e(
                    TAG,
                    "Payment Custom Tab launch failed",
                    e
            );

            clearActiveFlow();

            callback.onPaymentError(
                    new IllegalStateException(
                            "Unable to launch payment flow",
                            e
                    )
            );

            return false;
        }
    }

    // =========================================================
    // 🔗 REDIRECT ENTRY POINT
    // =========================================================

    /**
     * MainActivity تستدعي هذه الدالة.
     *
     * مهم:
     *
     * هذه الدالة لا تثق في Intent القادم.
     *
     * بل تتحقق من:
     *
     * 1. Intent action
     * 2. scheme
     * 3. host
     * 4. path
     * 5. active flow
     * 6. timeout
     * 7. state
     * 8. single consumption
     * 9. result type
     */
    public boolean handleRedirectIntent(
            @Nullable Intent intent
    ) {

        if (destroyed.get()) {
            return false;
        }

        if (intent == null) {
            return false;
        }

        final Uri uri =
                intent.getData();

        if (uri == null) {
            return false;
        }

        /*
         * لا نثق في Intent implicit بشكل كامل.
         *
         * يجب أن يكون VIEW.
         */
        String action =
                intent.getAction();

        if (action != null &&
                !Intent.ACTION_VIEW.equals(action)) {

            return false;
        }

        if (!isExpectedCallbackUri(uri)) {

            return false;
        }

        /*
         * لا نقبل callback إذا لم تبدأ عملية
         * من داخل Nexus.
         */
        FlowSession session =
                activeSession.get();

        if (session == null) {

            Log.w(
                    TAG,
                    "Rejected callback: no active flow"
            );

            return false;
        }

        /*
         * Timeout.
         */
        if (session.isExpired()) {

            Log.w(
                    TAG,
                    "Rejected callback: flow expired"
            );

            notifyExpiredFlow(session.flowType);

            clearActiveFlow();

            return true;
        }

        /*
         * Replay protection.
         */
        if (!callbackConsumed.compareAndSet(
                false,
                true
        )) {

            Log.w(
                    TAG,
                    "Rejected duplicate callback"
            );

            return true;
        }

        Log.i(
                TAG,
                "Valid Nexus callback received"
        );

        if (session.flowType == FLOW_AUTH) {

            handleAuthRedirect(
                    uri,
                    session
            );

            return true;
        }

        if (session.flowType == FLOW_PAYMENT) {

            handlePaymentRedirect(
                    uri,
                    session
            );

            return true;
        }

        clearActiveFlow();

        return false;
    }

    /**
     * compatibility alias.
     *
     * لأن النسخة السابقة من MainActivity قد تستخدم:
     *
     * handleRedirect(...)
     */
    public boolean handleRedirect(
            @Nullable Intent intent
    ) {

        return handleRedirectIntent(
                intent
        );
    }

    // =========================================================
    // 🔐 AUTH REDIRECT
    // =========================================================

    private void handleAuthRedirect(
            @NonNull Uri uri,
            @NonNull FlowSession session
    ) {

        final AuthCallback callback =
                pendingAuthCallback.getAndSet(null);

        /*
         * أول شيء:
         *
         * state.
         */
        final String returnedState =
                uri.getQueryParameter(
                        PARAM_STATE
                );

        if (returnedState == null ||
                returnedState.isEmpty()) {

            finishAuthWithError(
                    callback,
                    new SecurityException(
                            "Missing OAuth state"
                    )
            );

            return;
        }

        if (!constantTimeEquals(
                session.state,
                returnedState
        )) {

            finishAuthWithError(
                    callback,
                    new SecurityException(
                            "OAuth state validation failed"
                    )
            );

            return;
        }

        /*
         * لا نسمح بتمرير credentials حساسة
         * عبر Deep Link.
         */
        if (containsForbiddenCredential(
                uri
        )) {

            finishAuthWithError(
                    callback,
                    new SecurityException(
                            "Sensitive credential found in redirect"
                    )
            );

            return;
        }

        /*
         * OAuth error.
         */
        final String error =
                uri.getQueryParameter(
                        PARAM_ERROR
                );

        if (error != null &&
                !error.isEmpty()) {

            final String description =
                    uri.getQueryParameter(
                            PARAM_ERROR_DESCRIPTION
                    );

            finishAuthWithError(
                    callback,
                    new Exception(
                            sanitizeErrorMessage(
                                    description,
                                    error
                            )
                    )
            );

            return;
        }

        /*
         * Nexus لا يقبل code كجلسة.
         *
         * النتيجة المطلوبة:
         *
         * one-time handoff ticket
         */
        final String ticket =
                uri.getQueryParameter(
                        PARAM_TICKET
                );

        if (!isValidTicket(ticket)) {

            finishAuthWithError(
                    callback,
                    new SecurityException(
                            "Invalid or missing Nexus handoff ticket"
                    )
            );

            return;
        }

        /*
         * انتهت العملية بنجاح.
         *
         * ticket لا يتم تسجيله.
         */
        clearActiveFlow();

        if (callback != null) {

            callback.onAuthSuccess(
                    ticket,
                    returnedState
            );
        }
    }

    // =========================================================
    // 💳 PAYMENT REDIRECT
    // =========================================================

    private void handlePaymentRedirect(
            @NonNull Uri uri,
            @NonNull FlowSession session
    ) {

        final PaymentCallback callback =
                pendingPaymentCallback.getAndSet(null);

        /*
         * الدفع أيضاً مرتبط بالـ state.
         */
        final String returnedState =
                uri.getQueryParameter(
                        PARAM_STATE
                );

        if (returnedState == null ||
                returnedState.isEmpty() ||
                !constantTimeEquals(
                        session.state,
                        returnedState
                )) {

            finishPaymentWithError(
                    callback,
                    new SecurityException(
                            "Payment state validation failed"
                    )
            );

            return;
        }

        /*
         * لا نقبل tokens في callback.
         */
        if (containsForbiddenCredential(
                uri
        )) {

            finishPaymentWithError(
                    callback,
                    new SecurityException(
                            "Sensitive credential found in payment redirect"
                    )
            );

            return;
        }

        final String error =
                uri.getQueryParameter(
                        PARAM_ERROR
                );

        if (error != null &&
                !error.isEmpty()) {

            final String description =
                    uri.getQueryParameter(
                            PARAM_ERROR_DESCRIPTION
                    );

            finishPaymentWithError(
                    callback,
                    new Exception(
                            sanitizeErrorMessage(
                                    description,
                                    error
                            )
                    )
            );

            return;
        }

        final String transactionId =
                uri.getQueryParameter(
                        "transaction_id"
                );

        if (!isValidOpaqueIdentifier(
                transactionId
        )) {

            finishPaymentWithError(
                    callback,
                    new SecurityException(
                            "Invalid payment transaction identifier"
                    )
            );

            return;
        }

        clearActiveFlow();

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
     * تستخدم عندما يعرف النظام أن المستخدم
     * أغلق العملية الحساسة.
     *
     * ملاحظة:
     *
     * Custom Tabs التقليدية لا تعطي دائماً callback
     * موثوقاً لتمييز "الإغلاق" عن كل حالات lifecycle.
     */
    public void notifyFlowCancelled() {

        FlowSession session =
                activeSession.get();

        if (session == null) {
            return;
        }

        if (!callbackConsumed.compareAndSet(
                false,
                true
        )) {
            return;
        }

        AuthCallback authCallback =
                pendingAuthCallback.getAndSet(null);

        PaymentCallback paymentCallback =
                pendingPaymentCallback.getAndSet(null);

        int flow =
                session.flowType;

        clearActiveFlow();

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
    // ⏱ EXPIRATION
    // =========================================================

    private void notifyExpiredFlow(
            int flowType
    ) {

        AuthCallback authCallback =
                pendingAuthCallback.getAndSet(null);

        PaymentCallback paymentCallback =
                pendingPaymentCallback.getAndSet(null);

        if (flowType == FLOW_AUTH) {

            if (authCallback != null) {

                authCallback.onAuthError(
                        new java.util.concurrent.TimeoutException(
                                "Authentication flow expired"
                        )
                );
            }

        } else if (flowType == FLOW_PAYMENT) {

            if (paymentCallback != null) {

                paymentCallback.onPaymentError(
                        new java.util.concurrent.TimeoutException(
                                "Payment flow expired"
                        )
                );
            }
        }
    }

    // =========================================================
    // 🔐 FLOW CONTROL
    // =========================================================

    private boolean beginExclusiveFlow(
            int flowType
    ) {

        FlowSession existing =
                activeSession.get();

        if (existing != null) {

            if (!existing.isExpired()) {
                return false;
            }

            clearActiveFlow();
        }

        /*
         * null → temporary reservation
         *
         * activeSession سيتم وضعه مباشرة بعد ذلك.
         */
        return true;
    }

    // =========================================================
    // 🔎 STATE
    // =========================================================

    public boolean isFlowActive() {

        FlowSession session =
                activeSession.get();

        return session != null &&
                !session.isExpired();
    }

    public boolean isAuthFlowActive() {

        FlowSession session =
                activeSession.get();

        return session != null &&
                session.flowType == FLOW_AUTH &&
                !session.isExpired();
    }

    public boolean isPaymentFlowActive() {

        FlowSession session =
                activeSession.get();

        return session != null &&
                session.flowType == FLOW_PAYMENT &&
                !session.isExpired();
    }

    // =========================================================
    // 🔎 CALLBACK VALIDATION
    // =========================================================

    private boolean isExpectedCallbackUri(
            @NonNull Uri uri
    ) {

        String scheme =
                uri.getScheme();

        String host =
                uri.getHost();

        if (scheme == null ||
                host == null) {

            return false;
        }

        if (!AUTH_SCHEME.equalsIgnoreCase(
                scheme
        )) {

            return false;
        }

        if (!AUTH_HOST.equalsIgnoreCase(
                host
        )) {

            return false;
        }

        /*
         * بالنسبة إلى:
         *
         * com.store.app.auth://callback
         *
         * path عادة null أو "".
         */
        String path =
                uri.getPath();

        if (path != null &&
                !path.isEmpty() &&
                !"/".equals(path)) {

            return false;
        }

        /*
         * لا نقبل fragment.
         *
         * callback يجب أن يكون query فقط.
         */
        if (uri.getFragment() != null) {

            return false;
        }

        return true;
    }

    // =========================================================
    // 🔐 CREDENTIAL PROTECTION
    // =========================================================

    private boolean containsForbiddenCredential(
            @NonNull Uri uri
    ) {

        return hasQueryParameter(
                    uri,
                    PARAM_ACCESS_TOKEN
                )
                || hasQueryParameter(
                    uri,
                    PARAM_ID_TOKEN
                )
                || hasQueryParameter(
                    uri,
                    PARAM_SESSION_COOKIE
                )
                || hasQueryParameter(
                    uri,
                    PARAM_PASSWORD
                );
    }

    // =========================================================
    // 🎫 TICKET VALIDATION
    // =========================================================

    /**
     * الـ ticket ليس JWT مطلوباً من Android.
     *
     * الأفضل أن يكون opaque random identifier
     * قصير العمر ومستخدم مرة واحدة على server.
     *
     * Android يتحقق فقط من:
     *
     * - وجوده
     * - حجمه
     * - أنه ليس credential واضحاً
     * - أنه لا يحتوي على control characters
     */
    private boolean isValidTicket(
            @Nullable String ticket
    ) {

        if (ticket == null ||
                ticket.isEmpty()) {

            return false;
        }

        /*
         * حد أعلى لمنع payload abuse.
         */
        if (ticket.length() > 512) {
            return false;
        }

        /*
         * لا نقبل whitespace/control characters.
         */
        for (int i = 0;
             i < ticket.length();
             i++) {

            char c =
                    ticket.charAt(i);

            if (Character.isWhitespace(c) ||
                    Character.isISOControl(c)) {

                return false;
            }
        }

        /*
         * لا نقبل قيم تبدو كـ JWT.
         *
         * Nexus ticket المفترض opaque.
         */
        if (ticket.startsWith("eyJ")) {
            return false;
        }

        return true;
    }

    // =========================================================
    // 🔒 OPAQUE IDENTIFIER
    // =========================================================

    private boolean isValidOpaqueIdentifier(
            @Nullable String value
    ) {

        if (value == null ||
                value.isEmpty() ||
                value.length() > 256) {

            return false;
        }

        for (int i = 0;
             i < value.length();
             i++) {

            char c =
                    value.charAt(i);

            if (Character.isWhitespace(c) ||
                    Character.isISOControl(c)) {

                return false;
            }
        }

        return true;
    }

    // =========================================================
    // 🔐 HTTPS URL VALIDATION
    // =========================================================

    private boolean isValidHttpsUrl(
            @NonNull String url
    ) {

        try {

            Uri uri =
                    Uri.parse(url);

            String scheme =
                    uri.getScheme();

            String host =
                    uri.getHost();

            if (!"https".equalsIgnoreCase(
                    scheme
            )) {

                return false;
            }

            if (host == null ||
                    host.isEmpty()) {

                return false;
            }

            /*
             * لا credentials داخل authority.
             *
             * مثال مرفوض:
             *
             * https://user:password@example.com
             */
            if (uri.getUserInfo() != null) {
                return false;
            }

            return true;

        } catch (Throwable e) {

            return false;
        }
    }

    // =========================================================
    // 🔐 SECURE RANDOM
    // =========================================================

    private String generateSecureRandomToken(
            int numberOfBytes
    ) {

        byte[] bytes =
                new byte[numberOfBytes];

        secureRandom.nextBytes(bytes);

        return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE
                        | Base64.NO_WRAP
                        | Base64.NO_PADDING
        );
    }

    // =========================================================
    // 🔐 CONSTANT-TIME COMPARISON
    // =========================================================

    private boolean constantTimeEquals(
            @NonNull String a,
            @NonNull String b
    ) {

        byte[] aBytes =
                a.getBytes(
                        StandardCharsets.UTF_8
                );

        byte[] bBytes =
                b.getBytes(
                        StandardCharsets.UTF_8
                );

        return MessageDigest.isEqual(
                aBytes,
                bBytes
        );
    }

    // =========================================================
    // 🔗 URI BUILDER
    // =========================================================

    private Uri appendOrReplaceQueryParameter(
            @NonNull Uri uri,
            @NonNull String parameter,
            @NonNull String value
    ) {

        Uri.Builder builder =
                uri.buildUpon();

        /*
         * نحذف أي state قدمه الموقع/المصدر
         * ثم نضع state الذي أنشأه Nexus.
         */
        builder.clearQuery();

        for (String key :
                uri.getQueryParameterNames()) {

            if (parameter.equalsIgnoreCase(
                    key
            )) {
                continue;
            }

            for (String existingValue :
                    uri.getQueryParameters(key)) {

                if (existingValue != null) {

                    builder.appendQueryParameter(
                            key,
                            existingValue
                    );
                }
            }
        }

        builder.appendQueryParameter(
                parameter,
                value
        );

        return builder.build();
    }

    // =========================================================
    // 🔎 QUERY PARAMETER
    // =========================================================

    private boolean hasQueryParameter(
            @NonNull Uri uri,
            @NonNull String parameter
    ) {

        return uri.getQueryParameter(
                parameter
        ) != null;
    }

    // =========================================================
    // 🧹 AUTH ERROR
    // =========================================================

    private void finishAuthWithError(
            @Nullable AuthCallback callback,
            @NonNull Exception exception
    ) {

        clearActiveFlow();

        if (callback != null) {

            callback.onAuthError(
                    exception
            );
        }
    }

    // =========================================================
    // 🧹 PAYMENT ERROR
    // =========================================================

    private void finishPaymentWithError(
            @Nullable PaymentCallback callback,
            @NonNull Exception exception
    ) {

        clearActiveFlow();

        if (callback != null) {

            callback.onPaymentError(
                    exception
            );
        }
    }

    // =========================================================
    // 🧹 CLEAR
    // =========================================================

    private void clearActiveFlow() {

        activeSession.set(null);

        pendingAuthCallback.set(null);

        pendingPaymentCallback.set(null);
    }

    // =========================================================
    // 🛡️ SAFE ERROR MESSAGE
    // =========================================================

    private String sanitizeErrorMessage(
            @Nullable String description,
            @NonNull String fallback
    ) {

        String value =
                description != null &&
                        !description.isEmpty()
                        ? description
                        : fallback;

        /*
         * لا نسمح بتسريب payload ضخم إلى UI/logs.
         */
        if (value.length() > 512) {

            value =
                    value.substring(
                            0,
                            512
                    );
        }

        return value;
    }

    // =========================================================
    // 🧹 DESTROY
    // =========================================================

    public void destroy() {

        if (!destroyed.compareAndSet(
                false,
                true
        )) {

            return;
        }

        clearActiveFlow();

        Log.i(
                TAG,
                "RoyalAuthManager destroyed"
        );
    }
                }
