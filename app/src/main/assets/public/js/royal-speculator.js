/**
 * =========================================================
 * 🔮 ROYAL SPECULATION ENGINE (V5 - Wasm Delegator)
 * =========================================================
 * Architecture: Complete Delegation to C++ RoyalCoreEngine.
 * No JS Memory arrays, no JS math. Pure Wasm execution.
 */

(function () {
    'use strict';

    /**
     * 🎯 دالة التوجيه الخارقة (ترسل الرابط للـ C++ ليتخذ القرار)
     */
    function delegateToWasm(url) {
        // التأكد من أن محرك C++ جاهز للعمل
        if (window.RoyalWasm && window.RoyalWasm.core && window.RoyalWasm.intel) {
            
            // 1. إسأل النواة: هل هذا الرابط مؤهل؟ (تصفية + حماية الرام)
            let isEligible = window.RoyalWasm.core.evaluate_speculation(url);
            
            if (isEligible) {
                // 2. إذا وافقت النواة، اجعل محرك Intel يحقن الكود فوراً
                window.RoyalWasm.intel.inject_speculation_atomic(url);
            }
        }
    }

    // =========================================================
    // 🟢 VIEWPORT SENSOR (مجرد مستشعر دخول/خروج الشاشة)
    // =========================================================
    const ViewportPredictor = {
        init: function () {
            const observer = new IntersectionObserver((entries) => {
                entries.forEach(entry => {
                    const el = entry.target;
                    
                    if (entry.isIntersecting) {
                        if (el.tagName === 'A' && el.href) delegateToWasm(el.href);
                    } else {
                        // 🧹 إرسال أمر تنظيف الذاكرة للـ C++ عند خروج الرابط
                        if (el.tagName === 'A' && el.href && window.RoyalWasm) {
                            window.RoyalWasm.core.remove_speculation(el.href);
                        }
                    }
                });
            }, { rootMargin: "400px" });

            this.scanDOM = function () {
                document.querySelectorAll('a[href]:not([data-royal-warmed])').forEach(el => {
                    el.setAttribute('data-royal-warmed', 'true');
                    observer.observe(el);
                });
            };

            this.scanDOM();
            
            let scanScheduled = false;
            const mutationObserver = new MutationObserver(() => {
                if (scanScheduled) return;
                scanScheduled = true;
                setTimeout(() => {
                    scanScheduled = false;
                    this.scanDOM();
                }, 200);
            });
            mutationObserver.observe(document.body, { childList: true, subtree: true });
        }
    };

    // =========================================================
    // 🟠 NAVIGATION VELOCITY SENSOR (مستشعر سرعة السكرول)
    // =========================================================
    const NavigationPredictor = {
        init: function () {
            let lastY = window.scrollY;
            let lastTime = performance.now();
            let ticking = false;

            document.addEventListener('scroll', () => {
                if (!ticking) {
                    window.requestAnimationFrame(() => {
                        const currentY = window.scrollY;
                        const currentTime = performance.now();
                        const deltaTime = currentTime - lastTime;
                        
                        // 🧠 تفويض حساب السرعة والقرار للـ C++
                        if (window.RoyalWasm && window.RoyalWasm.core) {
                            let shouldPredictAhead = window.RoyalWasm.core.analyze_scroll_velocity(currentY, lastY, deltaTime);
                            
                            if (shouldPredictAhead) {
                                this.predictAhead();
                            }
                        }

                        lastY = currentY;
                        lastTime = currentTime;
                        ticking = false;
                    });
                    ticking = true;
                }
            }, { passive: true });
        },

        predictAhead: function () {
            const links = document.querySelectorAll('a[href]:not([data-royal-warmed])');
            let warmedCount = 0;
            for (let i = 0; i < links.length && warmedCount < 2; i++) {
                if (links[i].href) {
                    delegateToWasm(links[i].href);
                    links[i].setAttribute('data-royal-warmed', 'true');
                    warmedCount++;
                }
            }
        }
    };

    function startEngine() {
        ViewportPredictor.init();
        NavigationPredictor.init();
        console.log(`🔮 ROYAL SPECULATION V5: Sensors active, delegating decisions to Wasm Core.`);
    }

    window.RoyalSpeculator = { init: startEngine };
})();
