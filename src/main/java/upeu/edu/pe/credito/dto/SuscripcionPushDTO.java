package upeu.edu.pe.credito.dto;

public record SuscripcionPushDTO(
        String endpoint,
        Keys keys
) {
    public record Keys(String p256dh, String auth) {}
}
