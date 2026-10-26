(() => {
    'use strict';

    const API_SUSCRIPCION = '/api/notificaciones/suscripcion';

    const obtener = (id) => document.getElementById(id);

    function mostrarEstado(mensaje, tipo = 'secondary') {
        const estado = obtener('push-estado');
        if (!estado) return;

        estado.className = `small mb-2 text-${tipo}`;
        estado.textContent = mensaje;
    }

    function mostrarPanel() {
        const panel = obtener('panel-permiso-notificaciones');
        if (panel) panel.style.display = 'block';
    }

    function ocultarPanel() {
        const panel = obtener('panel-permiso-notificaciones');
        if (panel) panel.style.display = 'none';
    }

    function obtenerClavePublica() {
        return obtener('push-public-key')?.value?.trim() || '';
    }

    function convertirClaveVapid(clave) {
        const base64 = clave
            .replace(/-/g, '+')
            .replace(/_/g, '/');

        const relleno = '='.repeat((4 - base64.length % 4) % 4);
        const binario = atob(base64 + relleno);
        const salida = new Uint8Array(binario.length);

        for (let i = 0; i < binario.length; i++) {
            salida[i] = binario.charCodeAt(i);
        }

        return salida;
    }

    function obtenerCabeceras() {
        const headers = {
            'Content-Type': 'application/json'
        };

        const token = obtener('push-csrf-token')?.value;
        const nombreCabecera = obtener('push-csrf-header')?.value;

        if (token && nombreCabecera) {
            headers[nombreCabecera] = token;
        }

        return headers;
    }

    function comprobarCompatibilidad() {
        if (!window.isSecureContext) {
            throw new Error(
                'Las notificaciones requieren HTTPS. En local solo se admite localhost.'
            );
        }

        if (!('Notification' in window)) {
            throw new Error(
                'Este navegador no admite notificaciones web.'
            );
        }

        if (!('serviceWorker' in navigator)) {
            throw new Error(
                'Este navegador no admite Service Worker.'
            );
        }

        if (!('PushManager' in window)) {
            throw new Error(
                'Este navegador no admite notificaciones Push.'
            );
        }

        if (!obtenerClavePublica()) {
            throw new Error(
                'Falta la clave pública VAPID. Revisa APP_WEBPUSH_PUBLIC_KEY.'
            );
        }
    }

    async function guardarSuscripcion(suscripcion) {
        const respuesta = await fetch(API_SUSCRIPCION, {
            method: 'POST',
            credentials: 'same-origin',
            headers: obtenerCabeceras(),
            body: JSON.stringify(suscripcion)
        });

        if (!respuesta.ok) {
            if (respuesta.status === 401 || respuesta.status === 403) {
                throw new Error(
                    'La sesión expiró o el servidor rechazó la solicitud. Recarga la página e inicia sesión nuevamente.'
                );
            }

            throw new Error(
                `No se pudo guardar la suscripción (HTTP ${respuesta.status}).`
            );
        }
    }

    async function registrarSuscripcion() {
        comprobarCompatibilidad();

        const registro = await navigator.serviceWorker.register(
            '/service-worker.js',
            { scope: '/' }
        );

        await navigator.serviceWorker.ready;

        let suscripcion = await registro.pushManager.getSubscription();

        if (!suscripcion) {
            suscripcion = await registro.pushManager.subscribe({
                userVisibleOnly: true,
                applicationServerKey: convertirClaveVapid(
                    obtenerClavePublica()
                )
            });
        }

        await guardarSuscripcion(suscripcion);

        return suscripcion;
    }

    async function activarNotificaciones() {
        try {
            comprobarCompatibilidad();

            let permiso = Notification.permission;

            if (permiso === 'default') {
                permiso = await Notification.requestPermission();
            }

            if (permiso !== 'granted') {
                mostrarPanel();

                if (permiso === 'denied') {
                    mostrarEstado(
                        'El permiso está bloqueado en el navegador. Habilita las notificaciones en la configuración del sitio y vuelve a intentarlo.',
                        'danger'
                    );
                } else {
                    mostrarEstado(
                        'No se concedió el permiso. Puedes volver a intentarlo.',
                        'warning'
                    );
                }

                return;
            }

            mostrarEstado(
                'Permiso concedido. Registrando este dispositivo...',
                'primary'
            );

            await registrarSuscripcion();

            mostrarEstado(
                '¡Notificaciones activadas correctamente en este dispositivo!',
                'success'
            );

            ocultarPanel();

            console.info(
                '[CREDI DIAZ] Suscripción Push registrada correctamente.'
            );

        } catch (error) {
            console.error(
                '[CREDI DIAZ] Error al activar notificaciones:',
                error
            );

            mostrarPanel();
            mostrarEstado(
                error.message || 'No se pudieron activar las notificaciones.',
                'danger'
            );
        }
    }

    async function inicializar() {
        const panel = obtener('panel-permiso-notificaciones');
        const botonActivar = obtener('activar-notificaciones');
        const botonPosponer = obtener('posponer-notificaciones');

        // Si la página no usa este fragmento, no hay nada que inicializar.
        if (!panel || !botonActivar) return;
        document.body.appendChild(panel);

        botonActivar.addEventListener('click', activarNotificaciones);

        botonPosponer?.addEventListener('click', () => {
            ocultarPanel();
        });

        // No mostrar el panel en navegadores que no admiten Push.
        if (!('Notification' in window)
                || !('serviceWorker' in navigator)
                || !('PushManager' in window)) {
            mostrarEstado(
                'Este navegador o modo de navegación no admite notificaciones Push.',
                'warning'
            );
            botonActivar.disabled = true;
            mostrarPanel();
            return;
        }

        // Si ya se dio permiso, registrar o recuperar la suscripción.
        if (Notification.permission === 'granted') {
            mostrarEstado(
                'Verificando la activación de este dispositivo...',
                'primary'
            );

            try {
                await registrarSuscripcion();
                ocultarPanel();
            } catch (error) {
                console.error(
                    '[CREDI DIAZ] Error recuperando suscripción:',
                    error
                );

                mostrarPanel();
                mostrarEstado(
                    'El permiso está concedido, pero no se pudo registrar el dispositivo: '
                    + error.message,
                    'danger'
                );
            }

            return;
        }

        // Si aún no se pidió permiso, mostrar el panel.
        mostrarPanel();

        if (Notification.permission === 'denied') {
            mostrarEstado(
                'Las notificaciones están bloqueadas en la configuración del navegador.',
                'warning'
            );
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', inicializar);
    } else {
        inicializar();
    }

    // Permite probarlo manualmente desde la consola del navegador.
    window.activarNotificacionesCredi = activarNotificaciones;
})();
