
'use strict';

self.addEventListener('install', (event) => {
    self.skipWaiting();
});

self.addEventListener('activate', (event) => {
    event.waitUntil(self.clients.claim());
});

self.addEventListener('push', (event) => {
    event.waitUntil((async () => {
        try {
            let data = {};

            if (event.data) {
                try {
                    data = event.data.json();
                } catch {
                    data = {
                        title: 'CREDI DIAZ S.A.',
                        body: event.data.text(),
                        url: '/'
                    };
                }
            }

            const title = data.title || 'CREDI DIAZ S.A.';

            let targetUrl = '/';

            try {
                const parsedUrl = new URL(
                    data.url || '/',
                    self.location.origin
                );

                if (parsedUrl.origin === self.location.origin) {
                    targetUrl = parsedUrl.href;
                }
            } catch (error) {
                console.error('[CREDI DIAZ] URL inválida:', error);
            }

            const options = {
                body: data.body || 'Tienes una nueva notificación.',
                icon: '/images/icono.jpg',
                badge: '/images/icono.jpg',
                data: {
                    url: targetUrl
                }
            };

            // Solo usar renotify cuando exista un tag válido.
            if (data.id) {
                options.tag = `credi-${data.id}`;
                options.renotify = true;
            }

            await self.registration.showNotification(title, options);

            console.log(
                '[CREDI DIAZ] Notificación mostrada correctamente:',
                title
            );

        } catch (error) {
            console.error(
                '[CREDI DIAZ] Error mostrando notificación:',
                error
            );

            throw error;
        }
    })());
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();

    event.waitUntil((async () => {
        try {
            const rawUrl = event.notification.data?.url || '/';
            const target = new URL(rawUrl, self.location.origin);

            if (target.origin !== self.location.origin) {
                return;
            }

            const windows = await self.clients.matchAll({
                type: 'window',
                includeUncontrolled: true
            });

            for (const client of windows) {
                if (new URL(client.url).origin === self.location.origin) {
                    await client.navigate(target.href);
                    return client.focus();
                }
            }

            return self.clients.openWindow(target.href);

        } catch (error) {
            console.error(
                '[CREDI DIAZ] Error al abrir notificación:',
                error
            );
        }
    })());
});
