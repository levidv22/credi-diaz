(function () {
    'use strict';

    if (!window.fetch || !window.Notification) {
        return;
    }

    const STORAGE_KEY = 'credi_diaz_notificaciones_v1';
    let solicitandoPermiso = false;

    function leerAnterior() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY);
            return raw ? JSON.parse(raw) : null;
        } catch (e) {
            return null;
        }
    }

    function guardarActual(data) {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
        } catch (e) {
            // El navegador puede bloquear localStorage; la notificación seguirá funcionando.
        }
    }

    function pedirPermiso() {
        if (Notification.permission !== 'default' || solicitandoPermiso) {
            return Promise.resolve(Notification.permission);
        }
        solicitandoPermiso = true;
        return Notification.requestPermission().finally(function () {
            solicitandoPermiso = false;
        });
    }

    function mostrar(titulo, cuerpo, ruta) {
        if (Notification.permission !== 'granted') {
            return;
        }

        const n = new Notification(titulo, {
            body: cuerpo
        });

        n.onclick = function () {
            try {
                window.focus();
                window.location.href = ruta;
            } catch (e) {
                // No hacer nada si el navegador no permite navegar desde el evento.
            }
        };
    }

    function procesar(data) {
        const anterior = leerAnterior();
        const rol = data.rol || 'CLIENTE';

        const actual = {
            rol: rol,
            solicitudes: Number(data.solicitudes || 0),
            pagos: Number(data.pagos || 0),
            amortizaciones: Number(data.amortizaciones || 0),
            garantias: Number(data.garantias || 0)
        };

        const hayNuevas = anterior && anterior.rol === rol && (
            actual.solicitudes > Number(anterior.solicitudes || 0) ||
            actual.pagos > Number(anterior.pagos || 0) ||
            actual.amortizaciones > Number(anterior.amortizaciones || 0) ||
            actual.garantias > Number(anterior.garantias || 0)
        );

        const esPrimeraLectura = !anterior || anterior.rol !== rol;

        guardarActual(actual);

        if (Notification.permission === 'default' && (hayNuevas || (esPrimeraLectura && (actual.solicitudes + actual.pagos + actual.amortizaciones + actual.garantias) > 0))) {
            pedirPermiso().then(function () {
                procesarNotificacionActual(actual, anterior);
            });
            return;
        }

        if (hayNuevas) {
            procesarNotificacionActual(actual, anterior);
        }
    }

    function procesarNotificacionActual(actual, anterior) {
        const prev = anterior || {};

        if (actual.rol === 'ADMIN') {
            if (actual.solicitudes > Number(prev.solicitudes || 0)) {
                mostrar(
                    'CREDI DIAZ S.A.',
                    'Hay nuevas solicitudes de crédito pendientes de revisión.',
                    '/creditos/pendientes'
                );
            }
            if (actual.pagos > Number(prev.pagos || 0)) {
                mostrar(
                    'CREDI DIAZ S.A.',
                    'Hay nuevos pagos de cuotas pendientes de aprobación.',
                    '/creditos/pagos/pendientes'
                );
            }
            if (actual.amortizaciones > Number(prev.amortizaciones || 0)) {
                mostrar(
                    'CREDI DIAZ S.A.',
                    'Hay nuevas solicitudes de amortización pendientes de aprobación.',
                    '/creditos/amortizaciones/pendientes'
                );
            }
            if (actual.garantias > Number(prev.garantias || 0)) {
                mostrar(
                    'CREDI DIAZ S.A.',
                    'Hay créditos con garantía de patrocinador pendientes de respuesta.',
                    '/creditos/pendientes'
                );
            }
        } else if (actual.garantias > Number(prev.garantias || 0)) {
            mostrar(
                'CREDI DIAZ S.A.',
                'Un referido tuyo solicitó un crédito y necesita tu respuesta sobre la garantía.',
                '/usuarios/referidos'
            );
        }
    }

    function consultar() {
        fetch('/notificaciones/navegador', {
            method: 'GET',
            credentials: 'same-origin',
            headers: { 'Accept': 'application/json' },
            cache: 'no-store'
        })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error('No se pudo consultar notificaciones.');
                }
                return response.json();
            })
            .then(procesar)
            .catch(function () {
                // No interrumpir la navegación si el endpoint no está disponible temporalmente.
            });
    }

    consultar();
    window.setInterval(consultar, 10000);
})();
