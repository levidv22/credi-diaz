(function () {
    'use strict';

    const STORAGE_KEY = 'credi_diaz_push_prompt_v1';

    function getMeta(name) {
        return document.querySelector(`meta[name="${name}"]`)?.content || '';
    }

    function base64UrlToUint8Array(value) {
        const padding = '='.repeat((4 - value.length % 4) % 4);
        const base64 = (value + padding)
            .replace(/-/g, '+')
            .replace(/_/g, '/');

        const raw = atob(base64);
        const result = new Uint8Array(raw.length);

        for (let i = 0; i < raw.length; i++) {
            result[i] = raw.charCodeAt(i);
        }

        return result;
    }

    function csrfHeaders() {
        const headers = { 'Content-Type': 'application/json' };
        const token = getMeta('_csrf');
        const headerName = getMeta('_csrf_header');

        if (token && headerName) {
            headers[headerName] = token;
        }

        return headers;
    }

    async function guardarSuscripcion(subscription) {
        const response = await fetch('/api/notificaciones/suscripcion', {
            method: 'POST',
            credentials: 'same-origin',
            headers: csrfHeaders(),
            body: JSON.stringify(subscription)
        });

        if (!response.ok) {
            throw new Error(
                `Error guardando suscripción Push: HTTP ${response.status}`
            );
        }
    }

    async function registrarPush() {
        const publicKey = getMeta('vapid-public-key');

        if (!('serviceWorker' in navigator)
            || !('PushManager' in window)
            || !('Notification' in window)
            || !publicKey) {
            console.warn('Push no disponible o falta la clave pública VAPID.');
            return;
        }

        if (Notification.permission !== 'granted') return;

        const registration = await navigator.serviceWorker.register(
            '/service-worker.js',
            { scope: '/' }
        );

        await navigator.serviceWorker.ready;

        let subscription = await registration.pushManager.getSubscription();

        if (!subscription) {
            subscription = await registration.pushManager.subscribe({
                userVisibleOnly: true,
                applicationServerKey: base64UrlToUint8Array(publicKey)
            });
        }

        await guardarSuscripcion(subscription);
        console.info('Suscripción Push registrada correctamente.');
    }

    async function solicitarPermiso() {
        if (!('Notification' in window)) {
            throw new Error('Este navegador no admite notificaciones.');
        }

        if (Notification.permission === 'default') {
            const permission = await Notification.requestPermission();

            try {
                localStorage.setItem(STORAGE_KEY, permission);
            } catch (_) {}
        }

        if (Notification.permission === 'granted') {
            await registrarPush();
        } else {
            console.info('El permiso de notificaciones no fue concedido.');
        }
    }

    window.activarNotificacionesCredi = solicitarPermiso;

    document.addEventListener('DOMContentLoaded', () => {
        const button = document.getElementById('activar-notificaciones');

        if (button) {
            button.addEventListener('click', () => {
                solicitarPermiso().catch(error => {
                    console.error('No se pudo activar Push:', error);
                    alert('No se pudieron activar las notificaciones. Inténtalo nuevamente.');
                });
            });
        }

        if ('Notification' in window
            && Notification.permission === 'granted') {
            registrarPush().catch(error => {
                console.error('Error al recuperar la suscripción Push:', error);
            });
        }
    });
})();
