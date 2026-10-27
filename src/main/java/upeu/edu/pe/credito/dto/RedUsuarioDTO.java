package upeu.edu.pe.credito.dto;

import java.math.BigDecimal;
import java.util.List;

public record RedUsuarioDTO(
        Long id,
        String nombre,
        String rango,
        BigDecimal puntos,
        BigDecimal totalBonos,
        long cantidadDirectos,
        boolean tieneHijos,
        List<RedUsuarioDTO> hijos
) {
}
