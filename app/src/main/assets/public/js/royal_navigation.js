/* =========================================================
 * 👑 Royal Navigation Layer
 * =========================================================
 *
 * Purpose:
 *   Professional cross-document navigation transitions.
 *
 * Architecture:
 *   - Native browser View Transition API when available.
 *   - pageswap / pagereveal lifecycle control.
 *   - Navigation Activation direction detection.
 *   - Forward / Back transition types.
 *   - Reduced-motion awareness.
 *   - Safe feature detection.
 *   - Zero DOM replacement.
 *   - Zero fetch interception.
 *   - Zero router interference.
 *   - Zero dependency.
 *
 * This file MUST remain isolated from:
 *   royal_interaction.js
 *   prediction engines
 *   network engines
 *   application business logic
 *
 * ========================================================= */

(function RoyalNavigationBootstrap(global) {

    "use strict";

    /* ---------------------------------------------------------
     * Prevent duplicate initialization
     * --------------------------------------------------------- */

    if (global.__ROYAL_NAVIGATION_ENGINE__) {
        return;
    }

    const VERSION = "1.0.0";

    const STATE = {
        initialized: false,
        transitionActive: false,
        transitionSupported: false,
        reducedMotion: false,
        direction: "forward",
        navigationType: "unknown",
        currentUrl: location.href,
        sequence: 0
    };

    /* ---------------------------------------------------------
     * Feature Detection
     *
     * Cross-document View Transitions are CSS opt-in based.
     * We intentionally do NOT use document.startViewTransition()
     * as the primary detection because that API is associated
     * with same-document transitions.
     * --------------------------------------------------------- */

    function detectViewTransitionSupport() {

        if (!global.CSS || typeof CSS.supports !== "function") {
            return false;
        }

        try {

            const cssSupported =
                CSS.supports("view-transition-name: root") ||
                CSS.supports("view-transition-name: none");

            const pseudoSupported =
                CSS.supports("selector(::view-transition-old(root))");

            return Boolean(cssSupported && pseudoSupported);

        } catch (_) {

            return false;
        }
    }

    /* ---------------------------------------------------------
     * Reduced Motion
     *
     * We never force cinematic movement on users who explicitly
     * request reduced motion.
     * --------------------------------------------------------- */

    function detectReducedMotion() {

        try {

            return Boolean(
                global.matchMedia &&
                global.matchMedia(
                    "(prefers-reduced-motion: reduce)"
                ).matches
            );

        } catch (_) {

            return false;
        }
    }

    /* ---------------------------------------------------------
     * Same-Origin Guard
     *
     * View Transitions are strictly same-origin.
     * This guard prevents our transition layer from pretending
     * that subdomains are eligible.
     * --------------------------------------------------------- */

    function isSameOrigin(urlA, urlB) {

        try {

            const a = new URL(urlA, location.href);
            const b = new URL(urlB, location.href);

            return a.origin === b.origin;

        } catch (_) {

            return false;
        }
    }

    /* ---------------------------------------------------------
     * Navigation Activation
     *
     * Modern Chromium exposes NavigationActivation through
     * navigation.activation.
     *
     * We deliberately keep this defensive because older WebView
     * builds may expose pageswap/pagereveal without all activation
     * information.
     * --------------------------------------------------------- */

    function getActivation(event) {

        try {

            if (event && event.activation) {
                return event.activation;
            }

            if (
                global.navigation &&
                global.navigation.activation
            ) {
                return global.navigation.activation;
            }

        } catch (_) {}

        return null;
    }

    /* ---------------------------------------------------------
     * Navigation Type
     * --------------------------------------------------------- */

    function getNavigationType(activation) {

        if (!activation) {
            return "unknown";
        }

        try {

            if (activation.navigationType) {
                return String(
                    activation.navigationType
                ).toLowerCase();
            }

        } catch (_) {}

        return "unknown";
    }

    /* ---------------------------------------------------------
     * Determine Direction
     *
     * Priority:
     *
     * 1. Navigation API transition type.
     * 2. History index comparison when available.
     * 3. URL fallback.
     *
     * We intentionally do NOT guess aggressively.
     *
     * Unknown => neutral transition.
     * --------------------------------------------------------- */

    function determineDirection(event) {

        const activation = getActivation(event);

        if (!activation) {
            return "forward";
        }

        const type = getNavigationType(activation);

        STATE.navigationType = type;

        if (
            type === "traverse" ||
            type === "back_forward"
        ) {

            try {

                const from =
                    activation.from;

                const entry =
                    activation.entry;

                if (
                    from &&
                    entry &&
                    typeof from.index === "number" &&
                    typeof entry.index === "number"
                ) {

                    if (entry.index < from.index) {
                        return "back";
                    }

                    if (entry.index > from.index) {
                        return "forward";
                    }
                }

            } catch (_) {}

            /*
             * If this is a traversal but indexes are unavailable,
             * don't invent a direction.
             */
            return "neutral";
        }

        if (
            type === "push" ||
            type === "replace"
        ) {
            return "forward";
        }

        return "forward";
    }

    /* ---------------------------------------------------------
     * CSS Injection
     *
     * One stylesheet only.
     *
     * No global animation of the actual DOM.
     * The browser animates the transition pseudo-elements.
     * --------------------------------------------------------- */

    function injectStyles() {

        if (
            document.getElementById(
                "royal-navigation-style"
            )
        ) {
            return;
        }

        const style =
            document.createElement("style");

        style.id =
            "royal-navigation-style";

        style.textContent = `

/* =========================================================
 * 👑 ROYAL CROSS-DOCUMENT VIEW TRANSITION
 * ========================================================= */

@view-transition {
    navigation: auto;
}

/*
 * Root transition.
 *
 * We keep the default transition intentionally short.
 * The goal is continuity, not a theatrical page animation.
 */

::view-transition-group(root) {
    animation-duration: 180ms;
    animation-timing-function:
        cubic-bezier(0.22, 1, 0.36, 1);
}

/* ---------------------------------------------------------
 * DEFAULT / FORWARD
 * --------------------------------------------------------- */

::view-transition-old(root) {
    animation:
        royal-old-forward
        130ms
        cubic-bezier(0.4, 0, 1, 1)
        both;
}

::view-transition-new(root) {
    animation:
        royal-new-forward
        180ms
        cubic-bezier(0.22, 1, 0.36, 1)
        both;
}

/* ---------------------------------------------------------
 * BACK NAVIGATION
 * --------------------------------------------------------- */

:root:active-view-transition-type(royal-back)
::view-transition-old(root) {
    animation:
        royal-old-back
        150ms
        cubic-bezier(0.4, 0, 1, 1)
        both;
}

:root:active-view-transition-type(royal-back)
::view-transition-new(root) {
    animation:
        royal-new-back
        180ms
        cubic-bezier(0.22, 1, 0.36, 1)
        both;
}

/* ---------------------------------------------------------
 * FORWARD NAVIGATION
 * --------------------------------------------------------- */

:root:active-view-transition-type(royal-forward)
::view-transition-old(root) {
    animation:
        royal-old-forward
        130ms
        cubic-bezier(0.4, 0, 1, 1)
        both;
}

:root:active-view-transition-type(royal-forward)
::view-transition-new(root) {
    animation:
        royal-new-forward
        180ms
        cubic-bezier(0.22, 1, 0.36, 1)
        both;
}

/* ---------------------------------------------------------
 * NEUTRAL NAVIGATION
 *
 * Used when the browser does not provide enough information
 * to safely determine direction.
 * --------------------------------------------------------- */

:root:active-view-transition-type(royal-neutral)
::view-transition-old(root) {
    animation:
        royal-old-neutral
        120ms
        ease-out
        both;
}

:root:active-view-transition-type(royal-neutral)
::view-transition-new(root) {
    animation:
        royal-new-neutral
        160ms
        ease-in
        both;
}

/* ---------------------------------------------------------
 * FORWARD KEYFRAMES
 *
 * Extremely subtle movement.
 * The user should feel continuity rather than a slide.
 * --------------------------------------------------------- */

@keyframes royal-old-forward {

    from {
        opacity: 1;
        transform: translate3d(0, 0, 0);
    }

    to {
        opacity: 0.94;
        transform: translate3d(-1.2%, 0, 0);
    }
}

@keyframes royal-new-forward {

    from {
        opacity: 0.94;
        transform: translate3d(1.2%, 0, 0);
    }

    to {
        opacity: 1;
        transform: translate3d(0, 0, 0);
    }
}

/* ---------------------------------------------------------
 * BACK KEYFRAMES
 * --------------------------------------------------------- */

@keyframes royal-old-back {

    from {
        opacity: 1;
        transform: translate3d(0, 0, 0);
    }

    to {
        opacity: 0.94;
        transform: translate3d(1.2%, 0, 0);
    }
}

@keyframes royal-new-back {

    from {
        opacity: 0.94;
        transform: translate3d(-1.2%, 0, 0);
    }

    to {
        opacity: 1;
        transform: translate3d(0, 0, 0);
    }
}

/* ---------------------------------------------------------
 * NEUTRAL
 * --------------------------------------------------------- */

@keyframes royal-old-neutral {

    from {
        opacity: 1;
    }

    to {
        opacity: 0.92;
    }
}

@keyframes royal-new-neutral {

    from {
        opacity: 0.92;
    }

    to {
        opacity: 1;
    }
}

/* ---------------------------------------------------------
 * REDUCED MOTION
 * --------------------------------------------------------- */

@media (prefers-reduced-motion: reduce) {

    ::view-transition-group(root) {
        animation-duration: 1ms;
    }

    ::view-transition-old(root),
    ::view-transition-new(root) {
        animation-duration: 1ms !important;
    }
}

/* ---------------------------------------------------------
 * SAFETY
 *
 * Keep transition pseudo-elements above normal page content
 * but below browser-level UI.
 * --------------------------------------------------------- */

::view-transition {
    pointer-events: none;
}

`;

        /*
         * Prefer <head>.
         * Fallback to documentElement for unusual documents.
         */
        const target =
            document.head ||
            document.documentElement;

        if (target) {
            target.appendChild(style);
        }
    }

    /* ---------------------------------------------------------
     * Add Transition Type
     *
     * ViewTransition.types is the official mechanism for
     * dynamically selecting transition animations.
     * --------------------------------------------------------- */

    function applyTransitionType(
        viewTransition,
        direction
    ) {

        if (
            !viewTransition ||
            !viewTransition.types ||
            typeof viewTransition.types.add !== "function"
        ) {
            return;
        }

        try {

            /*
             * Only one Royal transition type should be active.
             */
            viewTransition.types.delete(
                "royal-forward"
            );

            viewTransition.types.delete(
                "royal-back"
            );

            viewTransition.types.delete(
                "royal-neutral"
            );

            if (direction === "back") {

                viewTransition.types.add(
                    "royal-back"
                );

            } else if (direction === "forward") {

                viewTransition.types.add(
                    "royal-forward"
                );

            } else {

                viewTransition.types.add(
                    "royal-neutral"
                );
            }

        } catch (_) {}
    }

    /* ---------------------------------------------------------
     * pageswap
     *
     * Runs on the outgoing document.
     *
     * This is the last safe opportunity to modify the outgoing
     * page before its transition snapshot is taken.
     * --------------------------------------------------------- */

    function handlePageSwap(event) {

        const viewTransition =
            event &&
            event.viewTransition;

        const activation =
            getActivation(event);

        /*
         * If there is no ViewTransition, this is still a useful
         * navigation lifecycle event, but there is nothing to
         * customize.
         */
        if (!viewTransition) {
            return;
        }

        const direction =
            determineDirection(event);

        STATE.direction =
            direction;

        STATE.transitionActive =
            true;

        STATE.sequence++;

        /*
         * Verify destination is actually same-origin when
         * activation exposes its destination.
         */
        try {

            if (
                activation &&
                activation.entry &&
                activation.entry.url
            ) {

                if (
                    !isSameOrigin(
                        location.href,
                        activation.entry.url
                    )
                ) {

                    viewTransition.skipTransition();

                    STATE.transitionActive =
                        false;

                    return;
                }
            }

        } catch (_) {}

        applyTransitionType(
            viewTransition,
            direction
        );

        /*
         * Expose diagnostic state without touching application
         * logic.
         */
        document.documentElement.dataset.royalNavigation =
            direction;

        /*
         * Do NOT wait for the outgoing transition.
         *
         * The document is about to be hidden and the old
         * ViewTransition object has special lifecycle semantics.
         */
        try {

            if (viewTransition.finished) {

                viewTransition.finished
                    .catch(() => {})
                    .finally(() => {

                        if (
                            document.documentElement
                        ) {

                            delete document
                                .documentElement
                                .dataset
                                .royalNavigation;
                        }
                    });
            }

        } catch (_) {}
    }

    /* ---------------------------------------------------------
     * pagereveal
     *
     * Runs on the destination document before its first
     * rendering opportunity.
     *
     * This is where the inbound transition receives its type.
     * --------------------------------------------------------- */

    function handlePageReveal(event) {

        const viewTransition =
            event &&
            event.viewTransition;

        if (!viewTransition) {
            STATE.transitionActive =
                false;

            return;
        }

        const direction =
            determineDirection(event);

        STATE.direction =
            direction;

        STATE.transitionActive =
            true;

        STATE.sequence++;

        applyTransitionType(
            viewTransition,
            direction
        );

        document.documentElement.dataset.royalNavigation =
            direction;

        /*
         * The transition is now controlled by the browser.
         *
         * We wait only for the visual lifecycle and then clean
         * our state.
         */
        try {

            const sequence =
                STATE.sequence;

            viewTransition.finished
                .catch(() => {})
                .finally(() => {

                    /*
                     * Ignore stale transition completions.
                     */
                    if (
                        sequence !== STATE.sequence
                    ) {
                        return;
                    }

                    STATE.transitionActive =
                        false;

                    if (
                        document.documentElement
                    ) {

                        delete document
                            .documentElement
                            .dataset
                            .royalNavigation;
                    }
                });

        } catch (_) {

            STATE.transitionActive =
                false;
        }
    }

    /* ---------------------------------------------------------
     * Navigation diagnostics
     * --------------------------------------------------------- */

    function exposeState() {

        global.RoyalNavigation =
            Object.freeze({

                version: VERSION,

                get supported() {
                    return STATE.transitionSupported;
                },

                get active() {
                    return STATE.transitionActive;
                },

                get direction() {
                    return STATE.direction;
                },

                get navigationType() {
                    return STATE.navigationType;
                },

                get sequence() {
                    return STATE.sequence;
                },

                get url() {
                    return location.href;
                }

            });
    }

    /* ---------------------------------------------------------
     * Runtime protection
     * --------------------------------------------------------- */

    function installEventListeners() {

        /*
         * These listeners are deliberately attached immediately.
         *
         * pagereveal must exist before the first rendering
         * opportunity of the destination document.
         */
        global.addEventListener(
            "pageswap",
            handlePageSwap
        );

        global.addEventListener(
            "pagereveal",
            handlePageReveal
        );

    }

    /* ---------------------------------------------------------
     * Bootstrap
     * --------------------------------------------------------- */

    function bootstrap() {

        if (STATE.initialized) {
            return;
        }

        STATE.reducedMotion =
            detectReducedMotion();

        STATE.transitionSupported =
            detectViewTransitionSupport();

        /*
         * CSS opt-in is harmless on unsupported engines because
         * unsupported at-rules/selectors are ignored.
         */
        injectStyles();

        /*
         * Install lifecycle listeners regardless of support.
         * This makes the engine resilient to WebView feature
         * differences.
         */
        installEventListeners();

        exposeState();

        STATE.initialized =
            true;

        /*
         * Internal diagnostic only.
         */
        try {

            console.info(
                "[RoyalNavigation] initialized",
                {
                    version: VERSION,
                    viewTransitions:
                        STATE.transitionSupported,
                    reducedMotion:
                        STATE.reducedMotion
                }
            );

        } catch (_) {}
    }

    /* ---------------------------------------------------------
     * Public singleton marker
     * --------------------------------------------------------- */

    global.__ROYAL_NAVIGATION_ENGINE__ = {
        version: VERSION
    };

    bootstrap();

})(window);
