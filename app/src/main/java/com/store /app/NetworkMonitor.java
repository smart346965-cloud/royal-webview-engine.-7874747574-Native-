package com.store.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkMonitor {

    private static final AtomicBoolean isConnected = new AtomicBoolean(true);
    private static boolean isRegistered = false;

    // 👑 جسر الإشعارات: واجهة للتحدث مع الواجهة الأمامية
    public interface NetworkStateListener {
        void onNetworkChanged(boolean connected);
    }
    private static NetworkStateListener listener;

    // 🔥 مرجع للـ WebView لإرسال الأحداث مباشرة إلى الصفحة
    private static WebView webView;

    public static void setListener(NetworkStateListener l) { listener = l; }
    public static void setWebView(WebView wv) { webView = wv; }

    public static void init(Context context) {
        if (isRegistered) return;
        
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            // فحص الحالة المبدئية
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean connected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

            isConnected.set(connected);
            RoyalNetworkEngine.setNetworkPrefetchAllowed(connected);

            // [تعديل جراحي في NetworkMonitor.java - الرادار الصارم]
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                    
                    // 🛡️ يتم استدعاؤها عند توفر شبكة جديدة (إشارة online)
                    @Override
                    public void onAvailable(Network network) {
                        // نحتاج للحصول على الـ NetworkCapabilities للتحقق من الصلاحية
                        ConnectivityManager cmLocal = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                        if (cmLocal == null) return;
                        
                        NetworkCapabilities caps = cmLocal.getNetworkCapabilities(network);
                        if (caps == null) return;
                        
                        boolean hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) 
                                           && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                        
                        // تحديث الحالة وإرسال الإشارة فقط عند تغير حقيقي
                        boolean oldState = isConnected.getAndSet(hasInternet);
                        RoyalNetworkEngine.setNetworkPrefetchAllowed(hasInternet);
                        
                        if (oldState != hasInternet && webView != null) {
                            // 🔥 إرسال حدث online مباشرة للصفحة عبر evaluateJavascript
                            webView.evaluateJavascript(
                                "window.dispatchEvent(new Event('online'));",
                                null
                            );
                        }
                        
                        // إبلاغ المستمع إن وجد
                        if (oldState != hasInternet && listener != null) {
                            new Handler(Looper.getMainLooper()).post(() -> listener.onNetworkChanged(hasInternet));
                        }
                    }

                    // 🛡️ يتم استدعاؤها عند تغير خصائص الشبكة (هنا يكمن سر التحقق من الإنترنت الحقيقي)
                    @Override
                    public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                        // فحص الصلاحية: هل الشبكة تمتلك إنترنت فعلي ومصدق من جوجل؟
                        boolean hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) 
                                           && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                        // تحديث الحالة الذرية
                        boolean oldState = isConnected.getAndSet(hasInternet);
                        RoyalNetworkEngine.setNetworkPrefetchAllowed(hasInternet);

                        // 🚀 لا نرسل الإشارة إلا إذا حدث "تغير حقيقي" في جودة الوصول للإنترنت
                        if (oldState != hasInternet && listener != null) {
                            new Handler(Looper.getMainLooper()).post(() -> listener.onNetworkChanged(hasInternet));
                        }
                    }

                    @Override
                    public void onLost(Network network) {
                        isConnected.set(false);
                        RoyalNetworkEngine.setNetworkPrefetchAllowed(false);
                        
                        // إبلاغ فوري عند فقدان السلك أو الإشارة
                        if (listener != null) {
                            new Handler(Looper.getMainLooper()).post(() -> listener.onNetworkChanged(false));
                        }
                    }
                });
            }
        }
        isRegistered = true;
    }

    public static boolean isInternetAvailable(Context context) {
        return isConnected.get();
    }
                                                                     }
