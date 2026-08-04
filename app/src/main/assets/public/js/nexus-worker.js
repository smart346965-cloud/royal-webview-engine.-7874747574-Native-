/**
 * 🧠 NEXUS NUCLEUS WORKER - ELITE EDITION
 * =========================================================
 * المضيف المستقل لنواة الـ C++ (Off-Main-Thread Architecture)
 */

// 1. استيراد شفرة الربط التي تم طبخها في GitHub Actions
importScripts('royal_nucleus.js');

let Maestro = null;
let Ignition = null;
let Core = null;
let Network = null;
let sharedWasmMemoryView = null;

/**
 * 🚀 مرحلة الانصهار (Fusion) داخل الـ Worker
 */
async function initNucleus() {
    try {
        const module = await createRoyalNucleusModule({
            print: (text) => console.log('🛰️ WORKER_WASM:', text),
            printErr: (text) => console.error('⚠️ WORKER_WASM_ERR:', text),
            locateFile: (path) => path // ملف الـ .wasm سيكون في نفس المجلد
        });

        // إيقاظ المايسترو داخل الخيط المنفصل
        Maestro = new module.RoyalNucleus();
        
        // ربط المحركات التخصصية
        window_proxy.Nexus = {
            Predictor: Maestro.getPredictor(),
            Guardian: Maestro.getGuardian(),
            Ignition: new module.RoyalIgnitionCore(),
            Core: new module.RoyalCoreEngine(),
            Network: new module.RoyalNetworkCore()
        };

        console.log("🏆 NUCLEUS WORKER: Maestro is alive in an independent thread.");
        
        // إخطار الخيط الرئيسي بالجاهزية
        self.postMessage({ type: 'NUCLEUS_READY' });

    } catch (e) {
        console.error("❌ WORKER_INIT_FAILED:", e);
    }
}

// [تعديل جراحي في nexus-worker.js]
self.onmessage = function(e) {
    const data = e.data;

    if (!Maestro && data.type !== 'INIT') return;

    // 👑 تجهيز الذاكرة الصفرية (Zero-Allocation Pool) عند الإقلاع
    if (data.type === 'INIT_MEMORY' && window_proxy.Nexus.Core) {
        const ptr = window_proxy.Nexus.Core.get_shared_buffer_ptr();
        // إنشاء نافذة زجاجية فوق ذاكرة C++ للكتابة فيها مباشرة دون إنشاء كائنات
        sharedWasmMemoryView = new Float32Array(Maestro.module.HEAPF32.buffer, ptr, 10);
        console.log("⚡ WORKER: Zero-Allocation Memory Pool Linked.");
        return;
    }

    switch(data.type) {
        case 'INIT':
            initNucleus();
            break;

        case 'TOUCH_START':
            // 1. الكتابة المباشرة في ذاكرة C++ (Zero-Allocation)
            if (sharedWasmMemoryView) {
                sharedWasmMemoryView[0] = data.x;
                sharedWasmMemoryView[1] = data.y;
                // يمكنك إضافة Timestamp في [2] و [3]
            }

            // 2. تحليل النية
            const willClick = window_proxy.Nexus.Predictor.analyze_pointer_intent(data.x, data.y, data.timestamp);
            
            if (willClick) {
                // 🚀 التصحيح العبقري: لا نلمس الـ DOM هنا! بل نأمر الخيط الرئيسي بذلك
                self.postMessage({ type: 'EXECUTE_PRERENDER', url: data.url });
            }
            break;

        case 'SCROLL_DATA':
            const isFast = window_proxy.Nexus.Core.analyze_scroll_velocity(data.y, data.lastY, data.delta);
            if (isFast) {
                // أمر للخيط الرئيسي بإيقاف الصور مؤقتاً
                self.postMessage({ type: 'THROTTLE_RENDER', state: true }); 
            }
            break;
    }
};

// محاكاة بسيطة لبيئة window داخل الـ Worker لضمان عمل EM_ASM
const window_proxy = { location: { origin: '' } };

// تشغيل المحرك فوراً
initNucleus();
