'use strict';

self.addEventListener('push', (event) => {
    if (!event.data) return;

    let data;

    try {
        data = event.data.json();
    } catch {
        data = {
            title: 'CREDI DIAZ S.A.',
            body: event.data.text(),
            url: '/'
        };
    }

    const title = data.title || 'CREDI DIAZ S.A.';
    const options = {
        body: data.body || 'Tienes una nueva notificación.',
        icon: '/images/icono.jpg',
        badge: '/images/icono.jpg',
        data: { url: data.url || '/' },
        tag: data.id ? `credi-${data.id}` : undefined
    };

    event.waitUntil(
        self.registration.showNotification(title, options)
    );
});

self.addEventListener('notificationclick', (event) => {
    event.notification.close();

    event.waitUntil((async () => {
        const rawUrl = event.notification.data?.url || '/';
        const target = new URL(rawUrl, self.location.origin);

        if (target.origin !== self.location.origin) return;

        const windows = await clients.matchAll({
            type: 'window',
            includeUncontrolled: true
        });

        for (const client of windows) {
            if (new URL(client.url).origin === self.location.origin) {
                await client.navigate(target.href);
                return client.focus();
            }
        }

        return clients.openWindow(target.href);
    })());
});
