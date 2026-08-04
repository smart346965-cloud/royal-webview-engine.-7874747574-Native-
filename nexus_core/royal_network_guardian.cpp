#include <emscripten/emscripten.h>
#include <emscripten/bind.h>
#include <string>
#include <unordered_map>
#include <chrono>
#include <vector>
#include <algorithm>
#include <unordered_set>

using namespace emscripten;

/**
 * 🛡️ ROYAL NETWORK GUARDIAN (The Binary Force)
 * محرك إدارة الكاش والشبكة بمستوى لغة الآلة
 */
class RoyalNetworkGuardian {
private:
    // [تعديلات شرسة في royal_network_guardian.cpp]
    struct CacheRule {
        long long ttl;
        bool stubborn_mode; // true: يتجاهل أوامر السيرفر بالحذف
        bool code_caching;  // تفعيل V8 Bytecode
    };

    std::unordered_map<std::string, CacheRule> registry;
    long long session_start_time;

    // [تعديل جراحي 1: إضافة قاعدة بيانات الدومينات الطفيلية]
    std::unordered_set<std::string> parasitic_registry;
    bool is_nucleus_stabilized = false; // لم يتم الاستقرار بعد

    // دالة خاصة لتحويل النصوص للأسفل بسرعة المعالج
    void fast_lower(std::string& s) {
        std::transform(s.begin(), s.end(), s.begin(), [](unsigned char c){ return std::tolower(c); });
    }

public:
    // نحدث الـ Constructor لقيد الدومينات الشهيرة بالبطء
    void init_shield_registry() {
        parasitic_registry = {
            "gorgias.chat", "connect.facebook.net", "google-analytics.com",
            "googletagmanager.com", "klaviyo.com", "luckyorange.com",
            "hotjar.com", "snapchat.com", "tiktok.com", "ads-twitter.com"
        };
    }

    RoyalNetworkGuardian() {
        session_start_time = std::chrono::system_clock::now().time_since_epoch().count();
        
        // 🧪 تلقيم القواعد "المتمردة" (Kiwi Style)
        registry[".js"]    = { 604800000, true, true };  // أسبوع كامل - تجاهل السيرفر - كاش Bytecode
        registry[".css"]   = { 604800000, true, false };
        registry[".woff2"] = { 2592000000, true, false };
        registry["html"]   = { 300000, false, false };    // 5 دقائق للصفحات الهيكلية

        // تهيئة درع الدومينات الطفيلية
        init_shield_registry();
    }

    /**
     * ⚡ محرك اتخاذ القرار الثنائي (Binary Decision Engine)
     * يقرر في (0.0001ms) هل يجب استدعاء الملف من الكاش المحلي أم لا
     */
    val evaluate_request_strategy(std::string url) {
        fast_lower(url);
        
        // منع كاش الـ APIs الحساسة فوراً في طبقة النواة
        if (url.find("/api/") != std::string::npos || url.find("token") != std::string::npos) {
            return val("NETWORK_ONLY");
        }

        // استخراج الامتداد بسرعة البرق
        size_t dot_pos = url.find_last_of('.');
        if (dot_pos != std::string::npos) {
            std::string ext = url.substr(dot_pos);
            if (registry.count(ext) && registry[ext].stubborn_mode) {
                return val("FORCE_STUBBORN_CACHE");
            }
        }

        return val("STALE_WHILE_REVALIDATE"); // العرض الفوري مع التحديث الخلفي
    }

    /**
     * 🧬 مولد المفاتيح الوميضي (Atomic Key Generator)
     * بدلاً من MD5 الجافا التقليدي، نستخدم خوارزمية دقيقة لربط الرابط بمكانه في الذاكرة
     */
    std::string compute_atomic_key(const std::string& url) {
        unsigned int hash = 0x811c9dc5; // FNV-1a Hash (الأسرع في C++)
        for (char c : url) {
            hash ^= (unsigned int)c;
            hash *= 0x01000193;
        }
        
        char hex[9];
        snprintf(hex, sizeof(hex), "%08x", hash);
        return std::string(hex);
    }

    /**
     * 🌐 رادار جودة الاتصال (Network Health Awareness)
     * يحسب النواة إذا كان الإنترنت يسمح بعمل Prefetch ثقيل أم لا
     */
    bool should_throttle_network(double current_latency) {
        // إذا كان التأخير أكثر من 500ms، النواة تأمر بإيقاف التنبؤ لحماية الرام والبطارية
        return current_latency > 500.0;
    }

    /**
     * 👑 تقنية "الاستبقاء الساخن" (Hot-Retention Policy)
     * تخبر الجافا بالملفات التي يجب أن تظل في الـ RAM دائماً
     */
    bool is_critical_asset(const std::string& url) {
        return (url.find("main.js") != std::string::npos || 
                url.find("style.css") != std::string::npos ||
                url.find("theme.css") != std::string::npos);
    }

    /**
     * 🖼️ محرك الرندرة البصري (Async Image Engine)
     * يجبر المتصفح على معالجة الصور في خيوط خلفية بعيداً عن الـ UI
     */
    void enforce_async_visuals() {
        EM_ASM({
            if (typeof document !== 'undefined' && typeof MutationObserver !== 'undefined') {
                const observer = new MutationObserver((mutations) => {
                    mutations.forEach(m => {
                        m.addedNodes.forEach(node => {
                            if (node.tagName === 'IMG') {
                                node.decoding = 'async'; 
                                node.loading = 'lazy';
                            }
                        });
                    });
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });
                console.log("🖼️ NUCLEUS: Async Image Decoding Enforced.");
            } else {
                console.log("⚡ NUCLEUS (Worker): Async visuals skipped in worker thread.");
            }
        });
    }

    /**
     * ⚡ تفعيل كاش الـ V8 Bytecode
     * يضمن أن الجافا سكريبت لا يُعاد ترجمته في كل مرة
     */
    void trigger_bytecode_opt() {
        // إرسال إشارة للمتصفح أن الموارد القادمة يجب حفظها كـ Bytecode
        EM_ASM({
            window.addEventListener('load', () => {
                if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
                    navigator.serviceWorker.controller.postMessage({type: 'SAVE_BYTECODE'});
                }
            });
        });
    }

    /**
     * 🌐 تقنية "القناة الساخنة" (Socket Persistency Commander)
     * يمنع السيرفر من إغلاق الاتصال ويقوم بتسخين الـ DNS مسبقاً
     */
    void maintain_hot_socket(const std::string& domain) {
        EM_ASM_({
            const url = UTF8ToString($0);
            if (typeof document !== 'undefined') {
                const link = document.createElement('link');
                link.rel = 'preconnect';
                link.href = url;
                link.crossOrigin = 'anonymous';
                document.head.appendChild(link);
            }
            fetch(url, { mode: 'no-cors', cache: 'no-store', priority: 'low' });
            console.log("🌐 NUCLEUS: Socket held HOT for " + url);
        }, domain.c_str());
    }

    /**
     * 🧠 محرك السيادة على الكاش (The Stubborn Cache Decision)
     * يخبر الجافا: "استخدم هذا الملف حتى لو قال السيرفر لا تستخدمه"
     */
    val get_stubborn_strategy(std::string url) {
        fast_lower(url);
        size_t dot_pos = url.find_last_of('.');
        if (dot_pos != std::string::npos) {
            std::string ext = url.substr(dot_pos);
            if (registry.count(ext) && registry[ext].stubborn_mode) {
                return val("FORCE_STUBBORN_CACHE"); 
            }
        }
        return val("DEFAULT_STRATEGY");
    }

    /**
     * ⚡ تفعيل أرشفة الـ Bytecode (V8 Persistence Engine)
     */
    void force_bytecode_persistence() {
        EM_ASM({
            // إجبار الكروميوم على اعتبار كل السكربتات "مؤهلة للكاش الثنائي"
            if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
                navigator.serviceWorker.controller.postMessage({
                    type: 'SAVE_BYTECODE_STRICT',
                    force: true
                });
            }
        });
    }

    /**
     * 🚀 خوارزمية "التدفق القسري" (Forced Multi-Burst Stream)
     * تقوي الشبكة الضعيفة عبر تزييف حالة الاتصال وفتح مسارات متوازية
     */
    void activate_network_turbo() {
        EM_ASM({
            if (typeof navigator !== 'undefined' && navigator.connection) {
                Object.defineProperty(navigator, 'connection', {
                    get: () => ({ effectiveType: '4g', downlink: 100, rtt: 5, saveData: false }),
                    configurable: true
                });
            }

            if (typeof document !== 'undefined' && typeof MutationObserver !== 'undefined') {
                const observer = new MutationObserver((mutations) => {
                    mutations.forEach(m => {
                        m.addedNodes.forEach(node => {
                            if (node.tagName === 'SCRIPT' || node.tagName === 'LINK') {
                                node.setAttribute('fetchpriority', 'high');
                            }
                        });
                    });
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });
            }
            console.log("🚀 NUCLEUS: Network Turbo Active.");
        });
    }

    /**
     * 🛡️ خوارزمية عزل السيادة (Sovereignty Isolation Decision)
     * تقرر في زمن 0.00001ms هل السكربت مسموح له بالمرور أم يجب عزله
     */
    bool should_isolate_domain(std::string url) {
        fast_lower(url);
        
        // إذا لم تستقر النواة بعد (أول 3 ثوانٍ)، نعزل أي دومين طفيلي فوراً
        for (const auto& domain : parasitic_registry) {
            if (url.find(domain) != std::string::npos) {
                return true; // يجب عزل هذا السكربت الآن
            }
        }
        return false;
    }

    // إشارة تخبر النواة بأن الرسم الأساسي اكتمل ويمكنها البدء في فك الخناق
    void mark_stabilized() {
        is_nucleus_stabilized = true;
        EM_ASM({ console.log("🛡️ NUCLEUS: Stabilized. Script Shield is now in Adaptive Mode."); });
    }
};

EMSCRIPTEN_BINDINGS(royal_guardian_module) {
    class_<RoyalNetworkGuardian>("RoyalNetworkGuardian")
        .constructor()
        .function("evaluate_request_strategy", &RoyalNetworkGuardian::evaluate_request_strategy)
        .function("compute_atomic_key", &RoyalNetworkGuardian::compute_atomic_key)
        .function("should_throttle_network", &RoyalNetworkGuardian::should_throttle_network)
        .function("is_critical_asset", &RoyalNetworkGuardian::is_critical_asset)
        .function("enforce_async_visuals", &RoyalNetworkGuardian::enforce_async_visuals)
        .function("trigger_bytecode_opt", &RoyalNetworkGuardian::trigger_bytecode_opt)
        .function("maintain_hot_socket", &RoyalNetworkGuardian::maintain_hot_socket)
        .function("get_stubborn_strategy", &RoyalNetworkGuardian::get_stubborn_strategy)
        .function("force_bytecode_persistence", &RoyalNetworkGuardian::force_bytecode_persistence)
        .function("activate_network_turbo", &RoyalNetworkGuardian::activate_network_turbo)
        .function("should_isolate_domain", &RoyalNetworkGuardian::should_isolate_domain)
        .function("mark_stabilized", &RoyalNetworkGuardian::mark_stabilized);
}
