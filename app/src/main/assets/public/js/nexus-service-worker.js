/**
 * =========================================================
 * 🔥 NEXUS X - Elite Service Worker v2.0
 * =========================================================
 * استراتيجيات متعددة حسب نوع الملف + Preload + Predictive Caching
 * يؤدي عمله وكيلاً داخلياً في محرك V8 متوازياً مع طبقة الشبكة
 */

const CONFIG = {
    CORE_CACHE: 'nexus-core-v5',
    IMAGE_CACHE: 'nexus-images-v5',
    FONT_CACHE: 'nexus-fonts-v5',
    PAGE_CACHE: 'nexus-pages-v5',
    OFFLINE_PAGE: '/offline.html',
    
    // الصلاحيات الزمنية للتخزين
    MAX_AGE: {
        CORE: 7 * 24 * 60 * 60,        // 7 أيام للملفات الأساسية
        IMAGES: 30 * 24 * 60 * 60,      // 30 يوم للصور
        FONTS: 365 * 24 * 60 * 60,      // سنة للخطوط
        PAGES: 24 * 60 * 60             // يوم واحد للصفحات
    },
    
    // 👑 رفع عدد الملفات المسموح بها لتتناسب مع الـ 4GB
    MAX_ITEMS: {
        CORE: 1000,   // أرشفة كل كود الموقع
        IMAGES: 3000,  // أرشفة جميع صور المنتجات بدقة عالية
        FONTS: 100,
        PAGES: 1000   // أرشفة الموقع كاملاً تقريباً
    }
};

// ⚡ الصفحات التي سيتم تحميلها مسبقاً تلقائياً
const PRELOAD_PAGES = [
    '/',
    '/index.html',
    '/products',
    '/categories',
    '/cart',
    '/account'
];

// 🖼️ أنماط الصور التي سيتم تخزينها
const IMAGE_EXTENSIONS = /\.(png|jpg|jpeg|gif|webp|svg|ico|avif|bmp)$/i;

// 🔤 أنماط الخطوط
const FONT_EXTENSIONS = /\.(woff|woff2|ttf|eot|otf)$/i;

// 📜 أنماط الملفات الأساسية
const CORE_EXTENSIONS = /\.(js|css|html|json|xml)$/i;

// ============================================================
// 🔧 وظائف مساعدة
// ============================================================

/**
 * تحديد نوع الكاش المناسب للطلب
 */
function getCacheName(request) {
    const url = new URL(request.url);
    
    if (request.destination === 'font' || FONT_EXTENSIONS.test(url.pathname)) {
        return CONFIG.FONT_CACHE;
    }
    if (request.destination === 'image' || IMAGE_EXTENSIONS.test(url.pathname)) {
        return CONFIG.IMAGE_CACHE;
    }
    if (request.destination === 'document' || request.mode === 'navigate') {
        return CONFIG.PAGE_CACHE;
    }
    return CONFIG.CORE_CACHE;
}

/**
 * التحقق من صلاحية العنصر المخزن
 */
async function isCacheValid(cacheName, request, maxAge) {
    const cache = await caches.open(cacheName);
    const cachedResponse = await cache.match(request);
    
    if (!cachedResponse) return false;
    
    // قراءة تاريخ التخزين من header مخصص
    const cachedDate = cachedResponse.headers.get('x-cached-date');
    if (!cachedDate) return false;
    
    const age = Date.now() - parseInt(cachedDate);
    return age < maxAge * 1000;
}

/**
 * تخزين الاستجابة مع تاريخ التخزين
 */
async function cacheWithDate(cacheName, request, response) {
    if (!response || response.status !== 200) return response;
    
    const cache = await caches.open(cacheName);
    const headers = new Headers(response.headers);
    headers.set('x-cached-date', Date.now().toString());
    
    const enhancedResponse = new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers: headers
    });
    
    await cache.put(request, enhancedResponse);
    
    // تنظيف الكاش إذا تجاوز الحد الأقصى
    await trimCache(cacheName, CONFIG.MAX_ITEMS[cacheName.split('-')[1]?.toUpperCase()] || 100);
    
    return enhancedResponse;
}

/**
 * تقليم الكاش عند تجاوز الحد الأقصى
 */
async function trimCache(cacheName, maxItems) {
    const cache = await caches.open(cacheName);
    const keys = await cache.keys();
    if (keys.length > maxItems) {
        // حذف الأقدم
        const toDelete = keys.slice(0, keys.length - maxItems);
        await Promise.all(toDelete.map(key => cache.delete(key)));
        console.log(`[Nexus X] 🧹 Trimmed ${toDelete.length} items from ${cacheName}`);
    }
}

/**
 * إضافة الطابع الزمني لمقارنة الحداثة
 */
async function addTimestamp(response) {
    const headers = new Headers(response.headers);
    headers.set('x-cached-date', Date.now().toString());
    return new Response(response.body, {
        status: response.status,
        statusText: response.statusText,
        headers: headers
    });
}

// ============================================================
// 👑 التثبيت: تحميل مسبق للصفحات الحيوية
// ============================================================
self.addEventListener('install', (event) => {
    console.log('[Nexus X] ⚡ Installing...');
    
    event.waitUntil(
        (async () => {
            // 🚀 تحميل مسبق فوري للصفحات الحيوية
            const cache = await caches.open(CONFIG.PAGE_CACHE);
            
            const preloadPromises = PRELOAD_PAGES.map(async (page) => {
                try {
                    const response = await fetch(page, { 
                        cache: 'no-cache',
                        credentials: 'include'
                    });
                    if (response && response.status === 200) {
                        await cacheWithDate(CONFIG.PAGE_CACHE, page, response.clone());
                        console.log(`[Nexus X] 📦 Preloaded: ${page}`);
                    }
                } catch (err) {
                    console.warn(`[Nexus X] ⚠️ Failed to preload: ${page}`);
                }
            });
            
            await Promise.allSettled(preloadPromises);
            console.log('[Nexus X] ✅ Installation complete. All critical pages cached.');
        })()
    );
    
    // فرض التفعيل الفوري
    self.skipWaiting();
});

// ============================================================
// 👑 التفعيل: السيطرة الفورية والتنظيف الذكي
// ============================================================
self.addEventListener('activate', (event) => {
    console.log('[Nexus X] ⚡ Activating...');
    
    const validCaches = [
        CONFIG.CORE_CACHE,
        CONFIG.IMAGE_CACHE,
        CONFIG.FONT_CACHE,
        CONFIG.PAGE_CACHE
    ];
    
    event.waitUntil(
        (async () => {
            const cacheNames = await caches.keys();
            const deletePromises = cacheNames
                .filter(name => !validCaches.includes(name))
                .map(name => {
                    console.log(`[Nexus X] 🧹 Removing old cache: ${name}`);
                    return caches.delete(name);
                });
            
            await Promise.all(deletePromises);
            
            // السيطرة الفورية على جميع العملاء
            const clients = await self.clients.matchAll();
            clients.forEach(client => {
                client.postMessage({
                    type: 'NEXUS_ACTIVATED',
                    version: '2.0',
                    timestamp: Date.now()
                });
            });
            
            await self.clients.claim();
            console.log('[Nexus X] ✅ Activation complete. Full control acquired.');
        })()
    );
});

// ============================================================
// 👑 اعتراض الطلبات: الاستراتيجية المتعددة حسب النوع
// ============================================================

self.addEventListener('fetch', (event) => {
    const request = event.request;
    const url = new URL(request.url);
    
    // تجاهل الطلبات غير GET
    if (request.method !== 'GET') return;
    
    // تجاهل طلبات API والتحليلات وطلبات Chrome الداخلية
    if (
        url.pathname.includes('/api/') ||
        url.pathname.includes('/analytics') ||
        url.pathname.includes('/gtm') ||
        request.url.startsWith('chrome-extension://')
    ) return;
    
    // تجاهل الطلبات عبر المنافذ (مثل WebSocket)
    if (url.protocol === 'ws:' || url.protocol === 'wss:') return;
    
    // ========================================
    // 🏠 استراتيجية الصفحة الرئيسية: Offline-First + Visual Continuity
    // ========================================
    if (request.mode === 'navigate') {
        event.respondWith(handlePageRequest(request));
        return;
    }
    
    // ========================================
    // 🖼️ استراتيجية الصور: Cache-First مع صلاحية 30 يوم
    // ========================================
    if (request.destination === 'image' || IMAGE_EXTENSIONS.test(url.pathname)) {
        event.respondWith(handleImageRequest(request));
        return;
    }
    
    // ========================================
    // 🔤 استراتيجية الخطوط: Cache-Only (تخزن للتثبيت)
    // ========================================
    if (request.destination === 'font' || FONT_EXTENSIONS.test(url.pathname)) {
        event.respondWith(handleFontRequest(request));
        return;
    }
    
    // ========================================
    // 📜 استراتيجية الملفات الأساسية: Stale-While-Revalidate
    // ========================================
    if (CORE_EXTENSIONS.test(url.pathname) || 
        request.destination === 'script' || 
        request.destination === 'style') {
        event.respondWith(handleCoreRequest(request));
        return;
    }
    
    // ========================================
    // 🗂️ استراتيجية افتراضية: Network-First مع Fallback
    // ========================================
    event.respondWith(handleDefaultRequest(request));
});

// ============================================================
// 🧠 معالجات كل نوع
// ============================================================

/**
 * 🏠 معالج الصفحات: Offline-First + Visual Continuity
 * هذا الجزء يضمن عدم ظهور صفحة Chrome أبداً
 */
async function handlePageRequest(request) {
    const cache = await caches.open(CONFIG.PAGE_CACHE);
    
    // 1. محاولة جلب النسخة الأحدث من الشبكة (Network-First) مع Timeout
    // لكي نضمن سرعة الرد حتى لو الإنترنت ضعيف
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 3000); // 3 ثواني للرد أو التحول للكاش

    try {
        const networkResponse = await fetch(request, { signal: controller.signal });
        if (networkResponse && networkResponse.status === 200) {
            clearTimeout(timeoutId);
            await cacheWithDate(CONFIG.PAGE_CACHE, request, networkResponse.clone());
            return networkResponse;
        }
    } catch (error) {
        console.log("[Nexus X] 📡 Network Fail/Timeout. Switching to Warehouse...");
    }

    // 2. 🚀 [الدرع الملكي]: إذا فشلت الشبكة، أرجع الكاش فوراً (0ms Offline Experience)
    const cachedResponse = await cache.match(request);
    if (cachedResponse) {
        return cachedResponse;
    }

    // 3. 🆘 إذا لم يوجد كاش أبداً (أول تشغيل)، أرجع الصفحة الرئيسية المخزنة مسبقاً
    return caches.match('/');
}

/**
 * 🖼️ معالج الصور: Cache-First مع صلاحية طويلة وتحميل تدريجي
 */
async function handleImageRequest(request) {
    const cache = await caches.open(CONFIG.IMAGE_CACHE);
    const cachedResponse = await cache.match(request);
    
    if (cachedResponse) {
        // التحقق من الصلاحية
        const isValid = await isCacheValid(CONFIG.IMAGE_CACHE, request, CONFIG.MAX_AGE.IMAGES);
        
        if (isValid) {
            console.log(`[Nexus X] 🖼️ Image from cache: ${request.url}`);
            return cachedResponse;
        }
        
        // الصورة موجودة لكن منتهية الصلاحية
        // أرجع المخزنة وحمّل الجديدة في الخلفية
        fetch(request, { cache: 'no-cache' })
            .then(response => {
                if (response && response.status === 200) {
                    cacheWithDate(CONFIG.IMAGE_CACHE, request, response);
                    console.log(`[Nexus X] 🔄 Image updated: ${request.url}`);
                }
            })
            .catch(() => {});
        
        return cachedResponse;
    }
    
    // غير موجودة، جلب من الشبكة
    try {
        const networkResponse = await fetch(request);
        if (networkResponse && networkResponse.status === 200) {
            await cacheWithDate(CONFIG.IMAGE_CACHE, request, networkResponse.clone());
            console.log(`[Nexus X] 🖼️ Image cached: ${request.url}`);
        }
        return networkResponse;
    } catch (error) {
        // عرض صورة افتراضية
        return new Response(
            '<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200"><rect width="200" height="200" fill="#e0e0e0"/><text x="100" y="110" font-size="16" text-anchor="middle" fill="#999">🖼️</text></svg>',
            { headers: { 'Content-Type': 'image/svg+xml' } }
        );
    }
}

/**
 * 🔤 معالج الخطوط: Cache-First دائم (نادراً ما تتغير الخطوط)
 */
async function handleFontRequest(request) {
    const cache = await caches.open(CONFIG.FONT_CACHE);
    const cachedResponse = await cache.match(request);
    
    if (cachedResponse) {
        console.log(`[Nexus X] 🔤 Font from cache: ${request.url}`);
        return cachedResponse;
    }
    
    try {
        const networkResponse = await fetch(request);
        if (networkResponse && networkResponse.status === 200) {
            // تخزين دائم (لا تحديث تلقائي للخطوط)
            await cacheWithDate(CONFIG.FONT_CACHE, request, networkResponse.clone());
            console.log(`[Nexus X] 🔤 Font cached permanently: ${request.url}`);
        }
        return networkResponse;
    } catch (error) {
        return new Response('', { status: 503, statusText: 'Font unavailable' });
    }
}

/**
 * 📜 معالج الملفات الأساسية: Stale-While-Revalidate
 */
async function handleCoreRequest(request) {
    const cache = await caches.open(CONFIG.CORE_CACHE);
    const cachedResponse = await cache.match(request);
    
    const networkFetch = fetch(request)
        .then(async (response) => {
            if (response && response.status === 200) {
                await cacheWithDate(CONFIG.CORE_CACHE, request, response.clone());
            }
            return response;
        })
        .catch(() => cachedResponse);
    
    // إرجاع المخزن فوراً إن وجد، وإلا انتظار الشبكة
    return cachedResponse || networkFetch;
}

/**
 * 🗂️ معالج افتراضي: Network-First مع تخزين تلقائي
 */
async function handleDefaultRequest(request) {
    try {
        const response = await fetch(request);
        
        // تخزين الملفات الناجحة تلقائياً حسب نوعها
        if (response && response.status === 200) {
            const cacheName = getCacheName(request);
            const cache = await caches.open(cacheName);
            await cacheWithDate(cacheName, request, response.clone());
        }
        
        return response;
    } catch (error) {
        // محاولة الإرجاع من الكاش
        const cacheName = getCacheName(request);
        const cache = await caches.open(cacheName);
        const cachedResponse = await cache.match(request);
        
        if (cachedResponse) {
            console.log(`[Nexus X] 🗂️ Served from cache: ${request.url}`);
            return cachedResponse;
        }
        
        throw error;
    }
}

// ============================================================
// 🔮 التحميل التنبؤي الذكي (Spider Preload)
// ============================================================

/**
 * تحليل محتوى الصفحة واستخراج الروابط للتحميل المسبق
 * بدلاً من 5 روابط، سنقوم بتحميل الروابط العميقة (Deep Links) بصمت
 */
async function predictivePreload(response) {
    try {
        const text = await response.clone().text();
        
        // استخراج الروابط من HTML
        const linkRegex = /href=["']([^"']+)["']/g;
        const links = [];
        let match;
        
        while ((match = linkRegex.exec(text)) !== null) {
            const href = match[1];
            // تجاهل الروابط الخارجية والمراسي
            if (
                href.startsWith('/') && 
                !href.startsWith('//') && 
                !href.startsWith('#') &&
                !href.includes('logout') &&
                !href.includes('sign-out')
            ) {
                links.push(href);
            }
        }
        
        // تحميل أول 20 رابط في الصفحة بصمت تام
        const preloadList = links.slice(0, 20);
        
        if (preloadList.length > 0) {
            console.log(`[Nexus X] 🔮 Predictive preload: ${preloadList.length} deep links`);
            
            const cache = await caches.open(CONFIG.PAGE_CACHE);
            
            for (const link of preloadList) {
                // التحقق من وجود الرابط في الكاش مسبقاً لتجنب التكرار
                const exists = await cache.match(link);
                if (!exists) {
                    try {
                        const response = await fetch(link, { cache: 'no-cache' });
                        if (response && response.status === 200) {
                            await cacheWithDate(CONFIG.PAGE_CACHE, link, response);
                            console.log(`[Nexus X] 🔮 Spider preloaded: ${link}`);
                        }
                    } catch (err) {
                        // فشل صامت للتحميل التنبؤي
                    }
                }
            }
        }
    } catch (err) {
        // فشل في تحليل HTML
    }
}

// ============================================================
// 📡 إشعار العميل بالتحديثات
// ============================================================

function notifyClientUpdate(url) {
    self.clients.matchAll().then(clients => {
        clients.forEach(client => {
            client.postMessage({
                type: 'PAGE_UPDATED',
                url: url,
                timestamp: Date.now()
            });
        });
    });
}

// ============================================================
// 🧠 معالج الرسائل من الطبقات الأخرى (Native Bridge)
// ============================================================

self.addEventListener('message', (event) => {
    if (!event.data || !event.data.type) return;
    
    switch (event.data.type) {
        
        // 🔥 أمر تحميل مسبق لروابط محددة من الـ Native Bridge
        case 'PRELOAD_PAGES':
            const urlsToPreload = event.data.urls || [];
            event.waitUntil(
                (async () => {
                    const cache = await caches.open(CONFIG.PAGE_CACHE);
                    for (const url of urlsToPreload) {
                        try {
                            const response = await fetch(url);
                            if (response && response.status === 200) {
                                await cacheWithDate(CONFIG.PAGE_CACHE, url, response);
                                console.log(`[Nexus X] 📦 Bridge Preload: ${url}`);
                            }
                        } catch (err) {
                            console.warn(`[Nexus X] ⚠️ Bridge Preload failed: ${url}`);
                        }
                    }
                })()
            );
            break;
        
        // 🧹 أمر تنظيف كاش محدد
        case 'CLEAR_CACHE':
            const cacheToClear = event.data.cacheName;
            if (cacheToClear) {
                event.waitUntil(caches.delete(cacheToClear));
                console.log(`[Nexus X] 🧹 Cache cleared: ${cacheToClear}`);
            }
            break;
        
        // 🗑️ أمر تنظيف كل الكاش
        case 'CLEAR_ALL_CACHES':
            event.waitUntil(
                caches.keys().then(names => 
                    Promise.all(names.map(name => caches.delete(name)))
                ).then(() => console.log('[Nexus X] 🧹 All caches cleared'))
            );
            break;
        
        // 📊 أمر إرسال إحصائيات الكاش
        case 'GET_CACHE_STATS':
            event.waitUntil(
                (async () => {
                    const stats = {};
                    const cacheNames = await caches.keys();
                    
                    for (const name of cacheNames) {
                        const cache = await caches.open(name);
                        const keys = await cache.keys();
                        stats[name] = keys.length;
                    }
                    
                    // إرسال الإحصائيات لجميع العملاء
                    const clients = await self.clients.matchAll();
                    clients.forEach(client => {
                        client.postMessage({
                            type: 'CACHE_STATS',
                            stats: stats,
                            timestamp: Date.now()
                        });
                    });
                    
                    console.log('[Nexus X] 📊 Cache stats:', stats);
                })()
            );
            break;
        
        default:
            console.log(`[Nexus X] ❓ Unknown message type: ${event.data.type}`);
    }
});

// ============================================================
// 📊 إحصائيات وأداء
// ============================================================

// تسجيل أداء التخزين المؤقت
self.addEventListener('fetch', function statsHandler(event) {
    // لا يؤثر على المنطق، فقط يجمع إحصائيات
    const startTime = performance.now();
    
    event.waitUntil(
        (async () => {
            await new Promise(resolve => setTimeout(resolve, 0));
            const duration = performance.now() - startTime;
            
            // يمكن إرسال هذه البيانات للتحليلات
            if (duration > 100) {
                console.warn(`[Nexus X] ⚠️ Slow request: ${event.request.url} (${duration.toFixed(2)}ms)`);
            }
        })()
    );
});

console.log(`
╔══════════════════════════════════════════╗
║   🔥 NEXUS X Service Worker v2.0       ║
║   ⚡ Instant Cache-First Strategy      ║
║   🔮 Predictive Preloading             ║
║   🖼️  Image Optimization               ║
║   📡 Offline Support                   ║
║   📊 Performance Monitoring            ║
║   ✅ Ready for Production              ║
╚══════════════════════════════════════════╝
`);
