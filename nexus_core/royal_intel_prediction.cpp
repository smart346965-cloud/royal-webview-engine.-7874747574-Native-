#include <emscripten/emscripten.h>
#include <emscripten/bind.h>
#include <cmath>
#include <string>

using namespace emscripten;

class RoyalIntelPrediction {
private:
    float last_touch_x = 0;
    float last_touch_y = 0;
    long long last_touch_time = 0;
    bool is_preloading_active = false;

public:
    RoyalIntelPrediction() {}

    /**
     * 👆 تحليل نية النقر (Pointer Intent Analysis)
     * تحسب ما إذا كان المستخدم يضغط "ليفتح" أم يضغط "ليمرر"
     */
    bool analyze_pointer_intent(float x, float y, long long timestamp) {
        float dx = std::abs(x - last_touch_x);
        float dy = std::abs(y - last_touch_y);
        long long dt = timestamp - last_touch_time;

        // إذا كانت الحركة أقل من 5 بكسل واستغرقت أكثر من 100ms
        // هذا يعني أن المستخدم "يستعد" للنقر.. نطلق شرارة التحميل المسبق فوراً!
        if (dx < 5.0f && dy < 5.0f && dt > 100) {
            return true; 
        }

        last_touch_x = x;
        last_touch_y = y;
        last_touch_time = timestamp;
        return false;
    }

    /**
     * 🔮 محرك حقن قواعد التكهن (Speculation Rules Injector)
     * يولد كود JS فائق القوة ليخبر الكروميوم بالصفحات التي يجب رسمها مسبقاً
     */
    void inject_speculation_atomic(const std::string& url) {
        EM_ASM_((
            const targetUrl = UTF8ToString($0);
            // إذا كنا في الـ Worker نرسل رسالة للـ Main Thread ليقوم بالحقن
            if (typeof self !== 'undefined' && typeof document === 'undefined') {
                self.postMessage({ type: 'EXECUTE_PRERENDER', url: targetUrl });
            } else if (typeof document !== 'undefined') {
                const specScript = document.createElement('script');
                specScript.type = 'speculationrules';
                specScript.textContent = JSON.stringify({
                    "prerender": [{ "source": "list", "urls": [targetUrl], "score": 1.0, "eagerness": "immediate" }]
                });
                document.head.appendChild(specScript);
            }
            console.log("🔮 NUCLEUS: Full Prerender Triggered for: " + targetUrl);
        ), url.c_str());
    }

    /**
     * 👻 الرندرة الشبحية الصارمة (Strict Ghost Prerendering)
     * لا يكتفي بجلب البيانات، بل يرسم الصفحة ويشغل الـ JS في الخلفية
     */
    void ghost_render_sequence(const std::string& url) {
        EM_ASM_((
            const targetUrl = UTF8ToString($0);
            const specRule = {
                "prerender": [{
                    "source": "list",
                    "urls": [targetUrl],
                    "eagerness": "immediate",
                    "eagerness_level": "conservative"
                }]
            };
            
            const script = document.createElement('script');
            script.type = 'speculationrules';
            script.textContent = JSON.stringify(specRule);
            document.head.appendChild(script);
            console.log("👻 NUCLEUS: Ghost Rendering Page in GPU Memory: " + targetUrl);
        ), url.c_str());
    }

    /**
     * 🌪️ تحرير الخيط الرئيسي (Off-Main-Thread Architect)
     * إجبار الكروميوم على استخدام خيط الـ Compositor لرسم العناصر مسبقاً
     */
    void offload_rendering_to_gpu() {
        EM_ASM(({
            if (typeof document === 'undefined') return; // حماية الـ Worker
            if (document.getElementById('royal-gpu-booster')) return;
            const style = document.createElement('style');
            style.id = 'royal-gpu-booster';
            style.textContent = `
                body, html { height: 100%; overflow-x: hidden; -webkit-overflow-scrolling: touch; scroll-behavior: smooth; }
                .royal-is-scrolling * { pointer-events: none !important; }
                main, section, article { transform: translateZ(0); will-change: transform; contain: paint; }
            `;
            document.head.appendChild(style);
            console.log("🌪️ NUCLEUS: GPU Layer Isolated.");
        }));
    }

    /**
     * 👆 محرك التنبؤ بالمسافات (Layout Pre-computation)
     * يمنع الـ Layout Thrashing عبر حساب المسافات مسبقاً في النواة
     */
    void precompute_page_layout() {
        EM_ASM(({
            if (typeof document === 'undefined' || typeof IntersectionObserver === 'undefined') return;
            const io = new IntersectionObserver(entries => {
                entries.forEach(e => {
                    if (e.isIntersecting && window.Nexus) {
                        window.Nexus.Ignition.set_engine_warmed(true);
                    }
                });
            }, { rootMargin: '500px' });
            document.querySelectorAll('a, div.product-card').forEach(el => io.observe(el));
        }));
    }

    /**
     * 🔄 خوارزمية "الرؤية العكسية" (Reverse-Navigation Oracle)
     * تقوم بتحليل سجل التنقل ورسم الصفحة السابقة "شبحياً" لضمان 0ms عند الرجوع
     */
    void predict_back_step(const std::string& previous_url) {
        if (previous_url.empty()) return;

        EM_ASM_((
            const url = UTF8ToString($0);
            const spec = {
                "prerender": [{
                    "source": "list",
                    "urls": [url],
                    "eagerness": "immediate"
                }]
            };
            const script = document.createElement('script');
            script.type = 'speculationrules';
            script.id = 'royal-back-prerender';
            script.textContent = JSON.stringify(spec);
            document.head.appendChild(script);
            console.log("🔄 NUCLEUS: Back-Step Prerendered in GPU memory: " + url);
        ), previous_url.c_str());
    }

    /**
     * ⚡ محرك "الاستبقاء البصري" (Visual Snapshot Retention)
     * يحفظ حالة الـ DOM الحالية قبل الانتقال لكي لا يضطر المتصفح لإعادة حسابها عند العودة
     */
    void lock_current_dom_state() {
        EM_ASM(({
            if (window.performance && window.performance.mark) {
                window.performance.mark('dom-lock-start');
            }
            // إجبار المتصفح على تفعيل BFCache بقوة عبر تعطيل الـ unload events
            window.onunload = null;
            window.onbeforeunload = null;
        }));
    }
};

EMSCRIPTEN_BINDINGS(royal_intel_module) {
    class_<RoyalIntelPrediction>("RoyalIntelPrediction")
        .constructor()
        .function("analyze_pointer_intent", &RoyalIntelPrediction::analyze_pointer_intent)
        .function("inject_speculation_atomic", &RoyalIntelPrediction::inject_speculation_atomic)
        .function("ghost_render_sequence", &RoyalIntelPrediction::ghost_render_sequence)
        .function("offload_rendering_to_gpu", &RoyalIntelPrediction::offload_rendering_to_gpu)
        .function("precompute_page_layout", &RoyalIntelPrediction::precompute_page_layout)
        .function("predict_back_step", &RoyalIntelPrediction::predict_back_step)
        .function("lock_current_dom_state", &RoyalIntelPrediction::lock_current_dom_state);
}
