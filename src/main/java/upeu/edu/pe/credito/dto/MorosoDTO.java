package upeu.edu.pe.credito.dto;

import lombok.*;
import upeu.edu.pe.credito.entity.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MorosoDTO {
    private Credito credito;
    private GarantiaReferido garantia;
    private BigDecimal saldoPendiente;
}
