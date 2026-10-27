package upeu.edu.pe.credito.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import upeu.edu.pe.credito.entity.Credito;
import upeu.edu.pe.credito.entity.EstadoCredito;
import upeu.edu.pe.credito.repository.CreditoRepository;

import java.util.List;

@Component
@ConditionalOnProperty(
        name = "mlm.regularizar-bonos-historicos",
        havingValue = "true"
)
public class RegularizarBonosHistoricosRunner
        implements CommandLineRunner {

    private final CreditoRepository creditoRepository;
    private final CreditoServiceImpl creditoService;

    public RegularizarBonosHistoricosRunner(
            CreditoRepository creditoRepository,
            CreditoServiceImpl creditoService
    ) {
        this.creditoRepository = creditoRepository;
        this.creditoService = creditoService;
    }

    @Override
    public void run(String... args) {
        List<Credito> creditosPagados =
                creditoRepository.findByEstado(EstadoCredito.PAGADO);

        int procesados = 0;
        int omitidos = 0;

        for (Credito credito : creditosPagados) {
            try {
                boolean regularizado =
                        creditoService.regularizarBonosAmortizacionHistorica(
                                credito.getId()
                        );

                if (regularizado) {
                    procesados++;
                    System.out.println(
                            "[BONOS MLM] Regularizado crédito #"
                                    + credito.getId()
                    );
                } else {
                    omitidos++;
                    System.out.println(
                            "[BONOS MLM] Omitido crédito #"
                                    + credito.getId()
                                    + " (sin interés elegible, sin amortización "
                                    + "aprobada o con bonos existentes)."
                    );
                }
            } catch (Exception e) {
                System.err.println(
                        "[BONOS MLM] Error en crédito #"
                                + credito.getId()
                                + ": " + e.getMessage()
                );
            }
        }

        System.out.println(
                "[BONOS MLM] Finalizado. Procesados: "
                        + procesados + "; omitidos: " + omitidos
        );
    }
}
