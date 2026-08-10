/**
 * 🧠 NEXUS NUCLEUS WORKER - ELITE EDITION
 * =========================================================
 * المضيف المستقل لنواة الـ C++ (Off-Main-Thread Architecture)
 */

// 1. استيراد شفرة الربط التي تم طبخها في GitHub Actions
importScripts('royal_nucleus.js');

let WasmModule = null;
let Maestro = null;
let Ignition = null;
let Core = null;
let Network = null;
let Predictor = null;
let Guardian = null;
let sharedWasmMemoryView = null;
let initialized = false;

/**
 * 🚀 مرحلة الانصهار (Fusion) داخل الـ Worker
 */
async function initNucleus(origin = '') {
    if (initialized) return;

    try {
        WasmModule = await createRoyalNucleusModule({
            print: (text) => console.log('🛰️ WORKER_WASM:', text),
            printErr: (text) => console.error('⚠️ WORKER_WASM_ERR:', text),

            locateFile: (path) => {
                return path;
            }
        });

        Maestro = new WasmModule.RoyalNucleus();

        Predictor = Maestro.getPredictor();
        Guardian = Maestro.getGuardian();
        Ignition = new WasmModule.RoyalIgnitionCore();
        Core = new WasmModule.RoyalCoreEngine();
        Network = new WasmModule.RoyalNetworkCore();

        if (origin) {
            Core.set_origin(origin);
        }

        initialized = true;

        console.log(
            "🏆 NUCLEUS WORKER: Maestro is alive in an independent thread."
        );

        self.postMessage({
            type: 'NUCLEUS_READY'
        });

    } catch (e) {
        console.error("❌ WORKER_INIT_FAILED:", e);

        self.postMessage({
            type: 'NUCLEUS_ERROR',
            error: String(e)
        });
    }
}

// [تعديل جراحي في nexus-worker.js]
self.onmessage = function (e) {
    const data = e.data || {};

    // ---------------------------------------------------------
    // INIT
    // ---------------------------------------------------------
    if (data.type === 'INIT') {
        initNucleus(data.origin || '');
        return;
    }

    // لا ننفذ أي أمر قبل جاهزية WASM
    if (!initialized) {
        return;
    }

    // ---------------------------------------------------------
    // INIT_MEMORY
    // ---------------------------------------------------------
    if (data.type === 'INIT_MEMORY') {
        try {
            const ptr = Core.get_shared_buffer_ptr();

            sharedWasmMemoryView = new Float32Array(
                WasmModule.HEAPF32.buffer,
                ptr,
                10
            );

            console.log(
                "⚡ WORKER: Zero-Allocation Memory Pool Linked."
            );

            self.postMessage({
                type: 'MEMORY_READY'
            });

        } catch (error) {
            console.error(
                "❌ MEMORY_LINK_FAILED:",
                error
            );
        }

        return;
    }

    // ---------------------------------------------------------
    // TOUCH_START
    // ---------------------------------------------------------
    if (data.type === 'TOUCH_START') {

        if (sharedWasmMemoryView) {
            sharedWasmMemoryView[0] = data.x || 0;
            sharedWasmMemoryView[1] = data.y || 0;
            sharedWasmMemoryView[2] = data.x || 0;
            sharedWasmMemoryView[3] = data.y || 0;
        }

        // أولاً: هل الرابط آمن؟
        if (!Core.evaluate_speculation(data.url || '')) {
            return;
        }

        // ثانيًا: تحليل نية المستخدم
        let willClick = false;

        try {
            willClick = Predictor.analyze_pointer_intent(
                data.x || 0,
                data.y || 0,
                data.timestamp || Date.now()
            );
        } catch (error) {
            console.warn(
                "⚠️ Predictor unavailable:",
                error
            );

            return;
        }

        if (willClick) {
            self.postMessage({
                type: 'EXECUTE_PRERENDER',
                url: data.url
            });
        }

        return;
    }

    // ---------------------------------------------------------
    // TOUCH_MOVE
    // ---------------------------------------------------------
    if (data.type === 'TOUCH_MOVE') {

        const intentional = Core.detect_scroll_slop(
            data.startX || 0,
            data.startY || 0,
            data.currentX || 0,
            data.currentY || 0,
            data.dpr || 1
        );

        if (intentional) {
            self.postMessage({
                type: 'CONFIRM_SCROLL'
            });
        }

        return;
    }

    // ---------------------------------------------------------
    // SCROLL_DATA
    // ---------------------------------------------------------
    if (data.type === 'SCROLL_DATA') {

        const fast = Core.analyze_scroll_velocity(
            data.y || 0,
            data.lastY || 0,
            data.delta || 0
        );

        if (fast) {
            self.postMessage({
                type: 'THROTTLE_RENDER',
                state: true
            });
        }

        return;
    }
};
