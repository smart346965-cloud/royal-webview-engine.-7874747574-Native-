package com.store.app;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkMonitor {

    private static final AtomicBoolean isConnected = new AtomicBoolean(true);
    private static boolean isRegistered = false;

    // 👑 جسر الإشعارات: واجهة للتحدث مع الواجهة الأمامية
    public interface NetworkStateListener {
        void onNetworkChanged(boolean connected);
    }
    private static NetworkStateListener listener;

    public static void setListener(NetworkStateListener l) { listener = l; }

    public static void init(Context context) {
        if (isRegistered) return;
        
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            // فحص الحالة المبدئية
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean connected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

            isConnected.set(connected);

            RoyalNetworkEngine.setNetworkPrefetchAllowed(connected);

            // مراقبة التغيرات اللحظية بدون استهلاك بطارية
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {

                        NetworkCapabilities caps =
                                cm.getNetworkCapabilities(network);

                        boolean ok =
                                caps != null
                                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

                        isConnected.set(ok);

                        RoyalNetworkEngine.setNetworkPrefetchAllowed(ok);

                        if (listener != null) {
                            new Handler(Looper.getMainLooper()).post(() -> listener.onNetworkChanged(ok));
                        }
                    }
                    @Override
                    public void onLost(Network network) {

                        isConnected.set(false);

                        RoyalNetworkEngine.setNetworkPrefetchAllowed(false);

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
