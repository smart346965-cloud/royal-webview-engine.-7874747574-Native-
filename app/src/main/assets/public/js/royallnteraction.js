/**
 * =========================================================
 * ⚡ ROYAL INTERACTION ENGINE V6
 * Native Prediction Sensor
 * =========================================================
 *
 * JS responsibilities:
 *  - Detect high-confidence touch intent.
 *  - Send predict(url) to Native.
 *
 * Native responsibilities:
 *  - Origin validation.
 *  - Preconnect.
 *  - Prerender.
 *  - Navigation.
 *
 * JS NEVER controls navigation.
 */

(function () {
    'use strict';

    const PredictionSensor = {

        init: function () {

            document.addEventListener(
                'touchstart',
                function (event) {

                    if (!event.touches ||
                        event.touches.length === 0) {
                        return;
                    }

                    const target =
                        event.target;

                    if (!target ||
                        !target.closest) {
                        return;
                    }

                    const link =
                        target.closest('a[href]');

                    if (!link ||
                        !link.href) {
                        return;
                    }

                    /*
                     * High-confidence signal:
                     * actual touch on a real hyperlink.
                     *
                     * Native performs ALL validation.
                     */

                    try {

                        if (window.RoyalJsBridge &&
                            typeof window.RoyalJsBridge.predict === 'function') {

                            window.RoyalJsBridge.predict(
                                link.href
                            );
                        }

                    } catch (error) {
                        // Prediction must never affect navigation.
                    }

                },
                {
                    passive: true,
                    capture: true
                }
            );
        }
    };


    function startRoyalInteraction() {

        PredictionSensor.init();

        console.log(
            '⚡ ROYAL INTERACTION V6: Native Prediction Sensor Active'
        );
    }


    window.RoyalInteraction = {
        init: startRoyalInteraction
    };

})();
