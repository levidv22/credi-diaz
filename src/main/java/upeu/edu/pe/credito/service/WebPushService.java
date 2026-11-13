package upeu.edu.pe.credito.service;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import upeu.edu.pe.credito.entity.SuscripcionPush;
import upeu.edu.pe.credito.repository.SuscripcionPushRepository;

import java.util.HashMap;
import java.util.Map;

@Service
public class WebPushService {

    private static final Logger log =
            LoggerFactory.getLogger(WebPushService.class);

    private final SuscripcionPushRepository repository;
    private final ObjectMapper objectMapper;
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
                    "Configura APP_WEBPUSH_PUBLIC_KEY, APP_WEBPUSH_PRIVATE_KEY y APP_WEBPUSH_SUBJECT."
            );
        }

        this.pushService = new PushService(
                publicKey.trim(),
                privateKey.trim(),
                subject.trim()
        );

        log.info("Servicio Web Push inicializado correctamente.");
    }

    public void enviar(
            SuscripcionPush suscripcion,
            String titulo,
            String mensaje,
            String url) {

        if (suscripcion == null) {
            log.warn("No se puede enviar Web Push: suscripción nula.");
            return;
        }

        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("title", titulo);
            payload.put("body", mensaje);
            payload.put("url", url == null || url.isBlank() ? "/" : url);

            String json = objectMapper.writeValueAsString(payload);

            Notification notification = new Notification(
                    suscripcion.getEndpoint(),
                    suscripcion.getClavePublica(),
                    suscripcion.getClaveAuth(),
                    json
            );

            log.info(
                    "Enviando Web Push. Suscripción ID: {}, Usuario ID: {}",
                    suscripcion.getId(),
                    suscripcion.getUsuario().getId()
            );

            HttpResponse response = pushService.send(notification);

            int status = response.getStatusLine().getStatusCode();

            String respuesta = response.getEntity() != null
                    ? EntityUtils.toString(response.getEntity())
                    : "";

            if (status >= 200 && status < 300) {

                log.info(
                        "Web Push enviado correctamente. Suscripción ID: {}. HTTP: {}",
                        suscripcion.getId(),
                        status
                );

            } else {

                log.error(
                        "Error Web Push. Suscripción ID: {}. HTTP: {}. Respuesta: {}",
                        suscripcion.getId(),
                        status,
                        respuesta
                );

                if (status == 404 || status == 410) {
                    repository.deleteByEndpoint(suscripcion.getEndpoint());

                    log.warn(
                            "Suscripción vencida eliminada. ID: {}",
                            suscripcion.getId()
                    );
                }
            }

        } catch (Exception e) {
            log.error(
                    "Excepción enviando Web Push. Suscripción ID: {}",
                    suscripcion.getId(),
                    e
            );
        }
    }
}
