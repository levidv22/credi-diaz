package upeu.edu.pe.credito.service;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import upeu.edu.pe.credito.entity.SuscripcionPush;
import upeu.edu.pe.credito.repository.SuscripcionPushRepository;

import java.util.HashMap;
import java.util.Map;

@Service
public class WebPushService {

    private final SuscripcionPushRepository repository;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final PushService pushService;

    public WebPushService(
            SuscripcionPushRepository repository,
            ObjectMapper objectMapper,
            @Value("${app.webpush.public-key:}") String publicKey,
            @Value("${app.webpush.private-key:}") String privateKey,
            @Value("${app.webpush.subject:}") String subject
    ) throws Exception {

        this.repository = repository;
        this.objectMapper = objectMapper;

        if (publicKey.isBlank() || privateKey.isBlank() || subject.isBlank()) {
            throw new IllegalStateException(
                    "Configura APP_WEBPUSH_PUBLIC_KEY, APP_WEBPUSH_PRIVATE_KEY y APP_WEBPUSH_SUBJECT.");
        }

        this.pushService = new PushService(publicKey, privateKey, subject);
    }

    public void enviar(
            SuscripcionPush suscripcion,
            String titulo,
            String mensaje,
            String url) {

        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("title", titulo);
            payload.put("body", mensaje);
            payload.put("url", url == null ? "/" : url);

            String json = objectMapper.writeValueAsString(payload);

            Notification notification = new Notification(
                    suscripcion.getEndpoint(),
                    suscripcion.getClavePublica(),
                    suscripcion.getClaveAuth(),
                    json
            );

            pushService.send(notification);

        } catch (Exception e) {
            System.err.println("Error al enviar Web Push: " + e.getMessage());
        }
    }
}
