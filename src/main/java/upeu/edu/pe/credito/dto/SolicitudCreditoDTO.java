package upeu.edu.pe.credito.dto;

import lombok.*;
import upeu.edu.pe.credito.entity.ModalidadPago;
import upeu.edu.pe.credito.entity.MedioDesembolso;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SolicitudCreditoDTO {
    private Long usuarioId;
    private BigDecimal monto;
    private ModalidadPago modalidad;
    private int plazoMeses;
    private int plazoSemanas;
    private MedioDesembolso medioDesembolso;
    private String nombreBanco;
    private String numeroCuenta;
    private String cci;
    private String nombreBilletera;
    private String numeroBilletera;
}
