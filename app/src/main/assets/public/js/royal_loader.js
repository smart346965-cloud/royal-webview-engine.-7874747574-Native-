/* 👑 ROYAL NUCLEUS ELITE LOADER (Streaming Edition + Telemetry Engine) */
(function() {
    // =========================================================================
    // 🔬 NEXUS TELEMETRY ENGINE: نظام الرادار التشخيصي الدقيق
    // =========================================================================
    window.NexusTelemetry = {
        metrics: { wasm_start: 0, wasm_end: 0, total_blocking_time: 0, bfcache_hit: false },
        longTasks: [],
        
        startMark: function(name) { performance.mark(name + '_start'); },
        endMark: function(name) { 
            performance.mark(name + '_end'); 
            try { performance.measure(name, name + '_start', name + '_end'); } catch(e){}
        },
        
        initObservers: function() {
            // مراقبة المهام الثقيلة التي تسبب التقطيع (Long Tasks > 50ms)
            if ('PerformanceObserver' in window) {
                try {
                    new PerformanceObserver((list) => {
                        list.getEntries().forEach(entry => {
                            window.NexusTelemetry.total_blocking_time += (entry.duration - 50);
                            window.NexusTelemetry.longTasks.push({ name: entry.name, duration: entry.duration.toFixed(2) });
                        });
                    }).observe({ type: 'longtask', buffered: true });
                } catch(e) {}
            }

            // مراقبة دقيقة لرجوع الصفحة (BFCache Monitor)
            window.addEventListener('pageshow', (event) => {
                window.NexusTelemetry.metrics.bfcache_hit = event.persisted;
                if (event.persisted) {
                    console.log("%c⚡ [NEXUS] BFCache HIT: تم الرجوع بـ 0ms (استرجاع لحظي من الذاكرة)", "color:#00ff00; font-weight:bold; background:#003300; padding:2px 5px;");
                } else {
                    const navType = performance.getEntriesByType("navigation")[0]?.type;
                    if (navType === 'back_forward') {
                        console.log("%c❌ [NEXUS] BFCache MISS: فشل الاسترجاع اللحظي! الموقع أعاد تحميل نفسه.", "color:#ff3333; font-weight:bold; background:#330000; padding:2px 5px;");
                    }
                }
            });
        },

        generateReport: function() {
            console.groupCollapsed("%c📊 NEXUS DIAGNOSTIC REPORT (اضغط لفتح التقرير الشامل)", "color: #00ffff; font-size: 14px; font-weight: bold; background: #111; padding: 6px; border-radius: 4px;");
            
            // 1. تقرير نواة الانصهار (WASM Core) - تم تعديل الخط للأسود الداكن
            const wasmMeasure = performance.getEntriesByName('WASM_IGNITION')[0];
            const wasmTime = wasmMeasure ? wasmMeasure.duration.toFixed(2) : 'N/A';
            console.log(`%c🧠 زمن بناء واستيقاث النواة (C++): %c${wasmTime} ms`, "color: #d97706; font-weight:bold;", "color: #000000; font-weight: bold; font-size: 12px;");
            
            // 2. تقرير سرعة الرسم والتنقل (Render & Navigation)
            const nav = performance.getEntriesByType("navigation")[0];
            const paint = performance.getEntriesByType("paint");
            const fcp = paint.find(p => p.name === 'first-contentful-paint');
            
            if (nav) {
                console.log(`%c🚀 نوع الدخول للصفحة: %c${nav.type.toUpperCase()}`, "color: #d97706; font-weight:bold;", "color: #000000; font-weight: bold;");
                console.log(`%c⏱️ زمن الاستجابة للهيكل (DOM Interactive): %c${nav.domInteractive.toFixed(2)} ms`, "color: #d97706; font-weight:bold;", "color: #000000; font-weight: bold;");
                console.log(`%c🎨 زمن اكتمال الموقع بالكامل (Load Complete): %c${nav.loadEventEnd.toFixed(2)} ms`, "color: #d97706; font-weight:bold;", "color: #000000; font-weight: bold;");
            }
            if (fcp) console.log(`%c👁️ أول بيكسلة ظهرت للشاشة (FCP): %c${fcp.startTime.toFixed(2)} ms`, "color: #d97706; font-weight:bold;", "color: #000000; font-weight: bold;");

            // 3. تقرير دعم تقنية الانصهار السريع (Speculation Rules)
            const specSupported = HTMLScriptElement.supports && HTMLScriptElement.supports('speculationrules');
            console.log(`%c🔮 تقنية التنبؤ والرندرة المسبقة: %c${specSupported ? 'تعمل بكفاءة 100%' : 'غير مدعومة في هذا الويبفيو!'}`, "color: #d97706; font-weight:bold;", specSupported ? "color: #059669; font-weight: bold;" : "color: #dc2626; font-weight: bold;");

            // 4. تحليل الاختناق (Bottleneck Diagnosis)
            console.log("%c🔍 --- التشخيص الآلي لسبب التأخير ---", "color: #0284c7; font-weight:bold;");
            
            if (this.longTasks.length > 0) {
                console.log(`%c⚠️ تم اكتشاف مهام ثقيلة جمدت الشاشة! (Total Blocking: ${this.metrics.total_blocking_time.toFixed(2)}ms)`, "color: #dc2626; font-weight:bold;");
                console.table(this.longTasks);
                console.log("%c💡 التشخيص: التأخير سببه سكربتات جافاسكريبت داخل الموقع الأصلي تعيق عمل المحرك.", "color: #dc2626; font-weight:bold;");
            } else if (wasmMeasure && wasmMeasure.duration > 300) {
                console.log("%c💡 التشخيص: تأخير بسبب بطء معالج الهاتف في فك تشفير ملف الـ WASM.", "color: #d97706; font-weight:bold;");
            } else if (nav && nav.domInteractive > 800) {
                console.log("%c💡 التشخيص: تأخير من الشبكة أو سيرفر الموقع الأصلي يرسل الـ HTML ببطء.", "color: #d97706; font-weight:bold;");
            } else {
                console.log("%c✅ التشخيص: لا يوجد أي بلوك! الأداء مثالي والنواة تعمل كالزبدة.", "color: #059669; font-weight:bold;");
            }
            
            console.groupEnd();
        }
    };

    // تشغيل الرادار
    window.NexusTelemetry.initObservers();
    // إتاحة أمر استخراج التقرير من الكونسول يدوياً
    window.NEXUS_REPORT = function() { window.NexusTelemetry.generateReport(); };

    // =========================================================================
    // 👑 ROYAL NUCLEUS IGNITION (كودك الأصلي مغلف بمجسات القياس)
    // =========================================================================
    const WASM_URL = 'https://royal-engine.local/public/js/royal_nucleus.wasm';
    const JS_URL = 'https://royal-engine.local/public/js/royal_nucleus.js';

    // [تعديل جراحي في Loader.js - استبدال دالة ignite فقط]
    async function ignite() {
        if (window.NexusWorkerActive) return; 

        try {
            window.NexusTelemetry.startMark('WASM_IGNITION'); // ⏱️ بدء قياس النواة

            // 1. إطلاق الخيط المنفصل (Worker) بدلاً من تجميد الخيط الرئيسي
            const worker = new Worker('public/js/nexus-worker.js');
            window.NexusWorker = worker;
            window.NexusWorkerActive = true;

            // 2. إرسال أمر الإقلاع
            worker.postMessage({ type: 'INIT' });

            // 3. الاستماع لأوامر النواة المنفصلة (The Brain commands the Body)
            worker.onmessage = function(e) {
                const msg = e.data;

                // 🎨 دالة مساعدة لإنشاء ورسم المربع الأزرق فورياً فوق الصفحة
                const drawBlueSquare = (label = "NUCLEUS SIGNAL") => {
                    let square = document.getElementById('nexus-debug-square');
                    if (!square) {
                        square = document.createElement('div');
                        square.id = 'nexus-debug-square';
                        square.style.cssText = `
                            position: fixed;
                            top: 20px;
                            right: 20px;
                            width: 60px;
                            height: 60px;
                            background-color: #0066ff;
                            border: 2px solid #ffffff;
                            border-radius: 8px;
                            box-shadow: 0 4px 15px rgba(0, 102, 255, 0.5);
                            z-index: 999999;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            color: white;
                            font-size: 10px;
                            font-weight: bold;
                            font-family: sans-serif;
                            pointer-events: none;
                            transition: transform 0.2s ease, opacity 0.2s ease;
                        `;
                        square.innerText = label;
                        document.body.appendChild(square);
                    }
                    // إضافة تأثير وميض بصري عند استقبال كل إشارة
                    square.style.transform = 'scale(1.2)';
                    setTimeout(() => { square.style.transform = 'scale(1)'; }, 200);
                };

                if (msg.type === 'NUCLEUS_READY') {
                    // النواة جاهزة، نطلب منها فتح حوض الذاكرة
                    worker.postMessage({ type: 'INIT_MEMORY' });
                    window.NexusTelemetry.endMark('WASM_IGNITION'); // ⏱️ إنهاء قياس الإقلاع
                    console.log("🚀 NUCLEUS ACTIVE: Off-Main-Thread Fusion Complete.");
                    
                    // 🟦 رسم المربع الأزرق فور جاهزية النواة للتأكد البصري
                    drawBlueSquare("READY");
                }

                // 🚀 تنفيذ أمر الرندرة المسبقة + إرسال نبضة للمربع الأزرق
                if (msg.type === 'EXECUTE_PRERENDER') {
                    drawBlueSquare("RENDER"); // وميض الأزرق عند استقبال إشارة التنبؤ
                    
                    if (HTMLScriptElement.supports && HTMLScriptElement.supports('speculationrules')) {
                        const script = document.createElement('script');
                        script.type = 'speculationrules';
                        script.textContent = JSON.stringify({
                            prerender: [{ source: "list", urls: [msg.url] }]
                        });
                        document.head.appendChild(script);
                        console.log(`⚡ [NEXUS] Prerendering Injected for: ${msg.url}`);
                    }
                }

                // 🔵 أمر مخصص عام: إذا أرسل الـ Worker أي إشارة باسم DRAW_BLUE_SQUARE
                if (msg.type === 'DRAW_BLUE_SQUARE') {
                    drawBlueSquare(msg.text || "SIGNAL");
                }
            };

            // 4. إنشاء قناة إرسال بيانات الحساسات (بدون استهلاك الذاكرة)
            window.dispatchToNucleus = (type, payload) => {
                worker.postMessage({ type, ...payload });
            };

            // 5. ربط لمسات المستخدم بالنواة المنفصلة
            window.addEventListener('touchstart', (e) => {
                const link = e.target.closest('a');
                if (link && link.href) {
                    window.dispatchToNucleus('TOUCH_START', {
                        x: e.touches[0].clientX,
                        y: e.touches[0].clientY,
                        timestamp: Date.now(),
                        url: link.href
                    });
                }
            }, { passive: true }); // passive لعدم تعطيل السكرول

            // [مراقب الخمول لفك خناق السكربتات يبقى كما هو لديك]
            const triggerMaestroStabilization = () => {
                console.log("%c🛡️ [NEXUS] SHIELD: Main Thread is now COLD.", "color:#3b82f6; font-weight:bold; background:#e0f2fe; padding:2px 5px;");
                if (window.RoyalBridge && window.RoyalBridge.log) {
                    window.RoyalBridge.log("NUCLEUS_STABILIZED");
                }
            };

            if ('requestIdleCallback' in window) {
                requestIdleCallback(() => setTimeout(triggerMaestroStabilization, 1500), { timeout: 4000 });
            } else {
                setTimeout(triggerMaestroStabilization, 4000);
            }

            // طباعة التقرير
            setTimeout(() => { window.NexusTelemetry.generateReport(); }, 2000);

        } catch (err) {
            console.warn("Nucleus Ignition partial fail, retrying...", err);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', ignite);
    } else {
        ignite();
    }

    // =========================================================================
    // 👑 NEXUS DOM PERSISTENCE & ZERO-LATENCY SHELL ENGINE
    // =========================================================================
    (function initDOMPersistence() {
        const SNAPSHOT_KEY = 'NEXUS_DOM_SHELL_SNAPSHOT';

        // 1. أخذ لقطة خفيفة من الصفحة الرئيسية فور استقرارها قبل الخروج/التنقل
        const captureHomeState = () => {
            const isHome = location.pathname === '/' || location.pathname.endsWith('/index.html') || location.href === location.origin + '/';
            if (isHome && document.body) {
                try {
                    // حفظ الهيكل مع حالة السكرول الحالية
                    sessionStorage.setItem(SNAPSHOT_KEY, JSON.stringify({
                        html: document.body.innerHTML,
                        scrollTop: window.scrollY,
                        timestamp: Date.now()
                    }));
                } catch(e) {}
            }
        };

        // التقاط الحالة عند تحول التطبيق للخلفية (Background) أو مغادرة التبويب
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'hidden') {
                captureHomeState();
            }
        });

        // 2. استرجاع موقع السكرول واللقطة عند الرجوع اللحظي
        window.addEventListener('pageshow', (event) => {
            if (event.persisted) {
                console.log("%c⚡ [NEXUS]: Instant BFCache Restore (0ms Response)", "color:#00ff00; font-weight:bold; background:#003300; padding:2px 5px;");
            }
        });
    })();

})();
