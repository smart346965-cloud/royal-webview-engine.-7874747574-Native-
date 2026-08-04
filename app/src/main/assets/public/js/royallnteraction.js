/**
 * =========================================================
 * ⚡ ROYAL INTERACTION ENGINE (V5 - Wasm Fusion Edition)
 * =========================================================
 * Architecture: Dumb Sensors -> Wasm Brain.
 * All mathematical slop detection and intent analysis are offloaded to C++.
 */

(function () {
    'use strict';

    // 1. CSS ACCELERATION (نفس كودك الرائع - لا يحتاج تغيير لأنها طبقة GPU)
    function injectHardwareAcceleration() {
        if (document.getElementById('royal-interaction-styles')) return;
        const style = document.createElement("style");
        style.id = 'royal-interaction-styles';
        style.textContent = `
            * { -webkit-tap-highlight-color: transparent !important; }
            a, button, [role="button"], input, select, textarea { touch-action: manipulation !important; }
            img { transform: translateZ(0); backface-visibility: hidden; }
            video, canvas, svg { transform: translateZ(0); backface-visibility: hidden; }
            body.royal-is-scrolling iframe { pointer-events: none !important; }
            body.royal-is-scrolling { will-change: scroll-position; }
            .royal-tap-active { opacity: 0.6 !important; transition: none !important; }
            .royal-tap-release { transition: opacity 0.3s ease-out !important; }
        `;
        document.head.appendChild(style);
    }

    // 2. TAP ENGINE (أصبح الآن مستشعراً فقط لـ C++)
    const TapEngine = {
        init: function () {
            let startX = 0, startY = 0;
            let isScrolling = false;
            let activeLink = null;

            document.addEventListener("touchstart", (e) => {
                if (e.touches.length === 0) return;
                startX = e.touches[0].clientX;
                startY = e.touches[0].clientY;
                isScrolling = false;

                const link = e.target.closest("a[href]");
                if (link && link.href) {
                    activeLink = link;
                    requestAnimationFrame(() => link.classList.add('royal-tap-active'));

                    // 🧠 استدعاء عقل الـ C++ لتحليل النية اللحظية (Pointer Intent)
                    if (window.RoyalWasm && window.RoyalWasm.intel) {
                        let timestamp = Date.now();
                        let willClick = window.RoyalWasm.intel.analyze_pointer_intent(startX, startY, timestamp);
                        if (willClick) {
                            // C++ يتوقع نقرة مؤكدة -> نطلق التنبؤ الفوري للرابط
                            window.RoyalWasm.intel.inject_speculation_atomic(link.href);
                        }
                    }
                }
            }, { passive: true });

            // [تعديل جراحي في royallnteraction.js]
            document.addEventListener("touchmove", (e) => {
                if (isScrolling || e.touches.length === 0 || !activeLink) return;
                
                // 🧠 القفل المنطقي: بمجرد أن نتأكد أن المستخدم يسحب، نتوقف عن سؤال الـ C++ تماماً
                // هذا يحرر الخيط الرئيسي فوراً لمعالجة الرسم
                const currentX = e.touches[0].clientX;
                const currentY = e.touches[0].clientY;

                if (window.RoyalWasm && window.RoyalWasm.core) {
                    // نرسل الحسابات للنواة مرة واحدة فقط لتأكيد نية السكرول
                    isScrolling = window.RoyalWasm.core.detect_scroll_slop(startX, startY, currentX, currentY);
                } else {
                    isScrolling = Math.abs(currentX - startX) > 10 || Math.abs(currentY - startY) > 10;
                }

                if (isScrolling) {
                    // 🚀 فور تأكيد السكرول، نطبق "كلاس السيولة" ونحرر الرابط
                    requestAnimationFrame(() => {
                        document.body.classList.add("royal-is-scrolling");
                        if (activeLink) activeLink.classList.remove('royal-tap-active');
                        activeLink = null;
                    });
                }
            }, { passive: true }); // passive ضرورية جداً هنا لضمان سلاسة المتصفح الأصلي

            document.addEventListener("touchend", (e) => {
                if (isScrolling || !activeLink) {
                    if (activeLink) activeLink.classList.remove('royal-tap-active');
                    activeLink = null;
                    return;
                }
                const link = activeLink;
                activeLink = null;
                link.classList.replace('royal-tap-active', 'royal-tap-release');
                
                if (link.origin === location.origin) {
                    window.location.href = link.href;
                }
            }, { passive: false });
        }
    };

    const BFCacheSanitizer = { /* نفس الكود الخاص بك دون تغيير */ };
    const RenderStabilizer = { /* نفس الكود الخاص بك دون تغيير */ };

    function startRoyalInteraction() {
        injectHardwareAcceleration();
        TapEngine.init();
        RenderStabilizer.init();
        // تم نقل ScrollEngine لملف التنبؤ لدمج حسابات السرعة
        console.log("⚡ ROYAL INTERACTION V5: UI Sensory Array Online & Synced with Wasm Core.");
    }

    window.RoyalInteraction = { init: startRoyalInteraction };
})();
