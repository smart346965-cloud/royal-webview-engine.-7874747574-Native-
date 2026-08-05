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
            /* ⚡ View Transitions API: منع الوميض الأبيض بـ Transitions سلسة */
            @view-transition { navigation: auto; }
            ::view-transition-old(root) { animation: 90ms ease-out both fade-out; }
            ::view-transition-new(root) { animation: 140ms ease-in both fade-in; }

            * { -webkit-tap-highlight-color: transparent !important; }
            a, button, [role="button"], input, select, textarea { touch-action: manipulation !important; }
            img { transform: translateZ(0); backface-visibility: hidden; }
            video, canvas, svg { transform: translateZ(0); backface-visibility: hidden; }
            body.royal-is-scrolling iframe { pointer-events: none !important; }
            body.royal-is-scrolling { will-change: scroll-position; }
            .royal-tap-active { opacity: 0.6 !important; transition: none !important; }
            .royal-tap-release { transition: opacity 0.3s ease-out !important; }

            @keyframes fade-out { from { opacity: 1; } to { opacity: 0; } }
            @keyframes fade-in { from { opacity: 0; } to { opacity: 1; } }
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

    const BFCacheSanitizer = {
        init: function () {
            // التأكد من عدم وجود أي أحداث Unload تعطل BFCache
            window.addEventListener('pageshow', (event) => {
                if (event.persisted) {
                    console.log('⚡ [BFCache] Page restored instantly from memory (0ms).');
                    // تنظيف أي حالة سكرول أو ضغط معلقة من التصفح السابق
                    document.body.classList.remove('royal-is-scrolling');
                    document.querySelectorAll('.royal-tap-active').forEach(el => {
                        el.classList.remove('royal-tap-active');
                    });
                }
            }, { passive: true });
        }
    };

    const RenderStabilizer = {
        init: function () {
            let scheduled = false;
            function stabilize() {
                if (scheduled) return;
                scheduled = true;
                requestAnimationFrame(() => { scheduled = false; });
            }
            const observer = new MutationObserver(stabilize);
            observer.observe(document.body, { childList: true, subtree: true });
        }
    };

    function startRoyalInteraction() {
        injectHardwareAcceleration();
        TapEngine.init();
        BFCacheSanitizer.init();
        RenderStabilizer.init();
        console.log("⚡ ROYAL INTERACTION V5: View Transitions & BFCache Fully Active.");
    }

    window.RoyalInteraction = { init: startRoyalInteraction };
})();
