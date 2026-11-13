
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
            throw new Error('Las notificaciones requieren HTTPS.');
        }

        if (!('Notification' in window)) {
            throw new Error('Este navegador no admite notificaciones.');
        }

        if (!('serviceWorker' in navigator)) {
            throw new Error('Este navegador no admite Service Worker.');
        }

        if (!('PushManager' in window)) {
            throw new Error('Este navegador no admite Push.');
        }

        if (!obtenerClavePublica()) {
            throw new Error('Falta la clave pública VAPID.');
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
            throw new Error(
                `No se pudo guardar la suscripción. HTTP ${respuesta.status}`
            );
        }
    }

    async function eliminarSuscripcionServidor(endpoint) {
        const respuesta = await fetch(API_SUSCRIPCION, {
            method: 'DELETE',
            credentials: 'same-origin',
            headers: obtenerCabeceras(),
            body: JSON.stringify({ endpoint })
        });

        if (!respuesta.ok) {
            throw new Error(
                `No se pudo eliminar la suscripción anterior. HTTP ${respuesta.status}`
            );
        }
    }

    function compararClaves(claveAnterior, claveNueva) {
        if (!claveAnterior) return false;

        const anterior = new Uint8Array(claveAnterior);

        if (anterior.length !== claveNueva.length) {
            return false;
        }

        return anterior.every(
            (byte, indice) => byte === claveNueva[indice]
        );
    }

    async function registrarSuscripcion() {
        comprobarCompatibilidad();

        const claveNueva = convertirClaveVapid(obtenerClavePublica());

        const registro = await navigator.serviceWorker.register(
            '/service-worker.js',
            { scope: '/' }
        );

        await navigator.serviceWorker.ready;

        let suscripcion = await registro.pushManager.getSubscription();

        // Comprobar si la suscripción fue creada con otra clave VAPID.
        if (suscripcion) {
            const claveAnterior =
                suscripcion.options.applicationServerKey;

            const coincide = compararClaves(
                claveAnterior,
                claveNueva
            );

            if (!coincide) {
                mostrarEstado(
                    'Actualizando la suscripción de este dispositivo...',
                    'primary'
                );

                // Primero eliminar el registro anterior de MySQL.
                await eliminarSuscripcionServidor(
                    suscripcion.endpoint
                );

                // Después eliminar la suscripción antigua del navegador.
                const eliminada = await suscripcion.unsubscribe();

                if (!eliminada) {
                    throw new Error(
                        'No se pudo eliminar la suscripción Push anterior. Intenta nuevamente.'
                    );
                }

                suscripcion = null;

                console.info(
                    '[CREDI DIAZ] Suscripción antigua eliminada por cambio de VAPID.'
                );
            }
        }

        // Crear una suscripción nueva con la clave pública actual.
        if (!suscripcion) {
            suscripcion = await registro.pushManager.subscribe({
                userVisibleOnly: true,
                applicationServerKey: claveNueva
            });
        }

        // Registrar la suscripción nueva en el servidor.
        await guardarSuscripcion(suscripcion);

        console.info(
            '[CREDI DIAZ] Nueva suscripción Push registrada correctamente.'
        );

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

                mostrarEstado(
                    permiso === 'denied'
                        ? 'Las notificaciones están bloqueadas en el navegador.'
                        : 'No se concedió el permiso de notificaciones.',
                    'warning'
                );

                return;
            }

            mostrarEstado(
                'Configurando las notificaciones de este dispositivo...',
                'primary'
            );

            await registrarSuscripcion();

            mostrarEstado(
                '¡Notificaciones activadas correctamente!',
                'success'
            );

            ocultarPanel();

        } catch (error) {
            console.error(
                '[CREDI DIAZ] Error al activar notificaciones:',
                error
            );

            mostrarPanel();
            mostrarEstado(error.message, 'danger');
        }
    }

    async function inicializar() {
        const panel = obtener('panel-permiso-notificaciones');
        const botonActivar = obtener('activar-notificaciones');
        const botonPosponer = obtener('posponer-notificaciones');

        if (!panel || !botonActivar) return;

        document.body.appendChild(panel);

        botonActivar.addEventListener(
            'click',
            activarNotificaciones
        );

        botonPosponer?.addEventListener('click', ocultarPanel);

        if (
            !('Notification' in window) ||
            !('serviceWorker' in navigator) ||
            !('PushManager' in window)
        ) {
            mostrarEstado(
                'Este navegador no admite notificaciones Push.',
                'warning'
            );

            botonActivar.disabled = true;
            mostrarPanel();
            return;
        }

        if (Notification.permission === 'granted') {
            try {
                mostrarEstado(
                    'Verificando las notificaciones de este dispositivo...',
                    'primary'
                );

                await registrarSuscripcion();

                ocultarPanel();

            } catch (error) {
                console.error(
                    '[CREDI DIAZ] Error actualizando suscripción:',
                    error
                );

                mostrarPanel();
                mostrarEstado(
                    error.message,
                    'danger'
                );
            }

            return;
        }

        mostrarPanel();

        if (Notification.permission === 'denied') {
            mostrarEstado(
                'Las notificaciones están bloqueadas en el navegador.',
                'warning'
            );
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener(
            'DOMContentLoaded',
            inicializar
        );
    } else {
        inicializar();
    }

    window.activarNotificacionesCredi = activarNotificaciones;

})();
