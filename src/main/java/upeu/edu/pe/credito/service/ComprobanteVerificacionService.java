package upeu.edu.pe.credito.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import upeu.edu.pe.credito.entity.MetodoPago;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

@Service
public class ComprobanteVerificacionService {

    private static final String VISION_ENDPOINT =
            "https://vision.googleapis.com/v1/images:annotate?key=";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;

    public ComprobanteVerificacionService(
            ObjectMapper objectMapper,
            @Value("${app.ocr.google-vision.api-key:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public Resultado verificar(byte[] imagen, String contentType, BigDecimal montoEsperado, MetodoPago metodo) {
        if (imagen == null || imagen.length == 0) {
            return new Resultado(false, "No se recibió el comprobante.", "");
        }
        if (montoEsperado == null || metodo == null) {
            return new Resultado(false, "Faltan datos para realizar la prevalidación. El comprobante requiere revisión manual.", "");
        }
        if (apiKey.isBlank()) {
            return new Resultado(false,
                    "La API de OCR no está configurada (GOOGLE_CLOUD_VISION_API_KEY). Se requiere revisión manual.",
                    "");
        }
        if (contentType == null || !contentType.matches("image/(jpeg|png|webp)")) {
            return new Resultado(false, "Formato de imagen no compatible con la prevalidación. Se requiere revisión manual.", "");
        }

        try {
            String texto = ejecutarOcrEnLaNube(imagen);
            if (texto.isBlank()) {
                return new Resultado(false, "La API no pudo leer texto suficiente del comprobante. Se requiere revisión manual.", "");
            }

            String normalizado = normalizar(texto);
            boolean montoOk = contieneMonto(normalizado, montoEsperado);
            boolean beneficiarioOk = comprobarBeneficiario(normalizado, metodo);

            boolean prevalidado = montoOk && beneficiarioOk;
            String detalle;
            if (!montoOk) {
                detalle = "OCR en la nube: no se encontró el monto esperado de S/ "
                        + montoEsperado.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                        + ". Revisión manual requerida.";
            } else if (!beneficiarioOk) {
                detalle = "OCR en la nube: se encontró el monto, pero no se identificó el beneficiario o destino esperado. Revisión manual requerida.";
            } else {
                detalle = "Prevalidación OCR: se encontraron el monto y datos de destino esperados. Esto no confirma autenticidad ni recepción del dinero; requiere aprobación manual del administrador.";
            }
            return new Resultado(prevalidado, detalle, texto);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resultado(false, "La consulta de OCR fue interrumpida. El comprobante requiere revisión manual.", "");
        } catch (Exception e) {
            // No mostrar la clave API ni respuestas sensibles al usuario.
            return new Resultado(false,
                    "No se pudo completar la consulta a la API de OCR en la nube. Verifique configuración/conectividad; el comprobante queda para revisión manual.",
                    "");
        }
    }

    private String ejecutarOcrEnLaNube(byte[] imagen) throws Exception {
        String encodedImage = Base64.getEncoder().encodeToString(imagen);
        var requestBody = objectMapper.createObjectNode();
        var requests = requestBody.putArray("requests");
        var request = requests.addObject();
        request.putObject("image").put("content", encodedImage);
        var feature = request.putArray("features").addObject();
        feature.put("type", "DOCUMENT_TEXT_DETECTION");
        feature.put("maxResults", 1);
        request.putObject("imageContext").putArray("languageHints").add("es").add("en");

        String json = objectMapper.writeValueAsString(requestBody);
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(VISION_ENDPOINT + encodedKey))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(
                httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Google Cloud Vision respondió HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode responses = root.path("responses");
        if (!responses.isArray() || responses.isEmpty()) return "";
        JsonNode first = responses.get(0);
        if (first.has("error")) throw new IllegalStateException("La API de OCR devolvió un error.");
        String fullText = first.path("fullTextAnnotation").path("text").asText("");
        if (!fullText.isBlank()) return fullText;
        JsonNode textAnnotations = first.path("textAnnotations");
        if (textAnnotations.isArray() && !textAnnotations.isEmpty()) {
            return textAnnotations.get(0).path("description").asText("");
        }
        return "";
    }

    private boolean comprobarBeneficiario(String texto, MetodoPago metodo) {
        if (metodo == MetodoPago.TRANSFERENCIA) {
            return contieneNombre(texto, "DIAZ CULQUI EDER OMAR")
                    || texto.contains("43502664174002")
                    || texto.contains("00243510266417400266");
        }
        if (metodo == MetodoPago.YAPE) {
            return contieneNombre(texto, "EDER DIA")
                    || contieneNombre(texto, "DIAZ CULQUI EDER OMAR")
                    || texto.contains("915092797");
        }
        return false;
    }

    private boolean contieneMonto(String texto, BigDecimal montoEsperado) {
        BigDecimal monto = montoEsperado.setScale(2, java.math.RoundingMode.HALF_UP);
        String decimal = monto.toPlainString();
        String coma = decimal.replace('.', ',');
        // Buscar formatos habituales sin aceptar coincidencias dentro de importes mayores.
        String[] candidatos = {
                decimal,
                coma,
                decimal.replace(".", ""),
                coma.replace(",", ""),
                "S/ " + decimal,
                "S/ " + coma,
                "PEN " + decimal
        };
        for (String candidato : candidatos) {
            String c = normalizar(candidato);
            if (c.isBlank()) continue;
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "(?<![0-9])" + java.util.regex.Pattern.quote(c) + "(?![0-9])");
            if (p.matcher(texto).find()) return true;
        }
        return false;
    }

    private boolean contieneNombre(String texto, String esperado) {
        String[] tokens = normalizar(esperado).split(" ");
        int encontrados = 0;
        for (String token : tokens) {
            if (token.length() > 1 && texto.contains(token)) encontrados++;
        }
        return encontrados >= Math.max(2, tokens.length - 1);
    }

    private String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9.,/ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record Resultado(boolean valido, String detalle, String textoDetectado) {}
}
