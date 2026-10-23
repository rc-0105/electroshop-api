package co.edu.unbosque.electroshop.service;

import co.edu.unbosque.electroshop.domain.MetodoPago;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simula la llamada a una pasarela de pagos externa.
 *
 * La latencia es configurable (electroshop.pago.latencia-ms) y representa el tiempo de red
 * y de autorización del banco. Forma parte de los 100 ms del requisito, así que conviene
 * declarar en el informe qué valor se usó y por qué.
 */
@Component
public class PasarelaPagoSimulada {

    private final long latenciaMs;
    private final double tasaRechazo;

    public PasarelaPagoSimulada(@Value("${electroshop.pago.latencia-ms:30}") long latenciaMs,
                                @Value("${electroshop.pago.tasa-rechazo:0.0}") double tasaRechazo) {
        this.latenciaMs = latenciaMs;
        this.tasaRechazo = tasaRechazo;
    }

    public ResultadoPago cobrar(BigDecimal monto, MetodoPago metodo) {
        if (latenciaMs > 0) {
            try {
                Thread.sleep(latenciaMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResultadoPago.rechazado("Operación interrumpida");
            }
        }
        if (tasaRechazo > 0 && ThreadLocalRandom.current().nextDouble() < tasaRechazo) {
            return ResultadoPago.rechazado("Fondos insuficientes");
        }
        String referencia = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return ResultadoPago.aprobado(referencia);
    }

    public record ResultadoPago(boolean aprobado, String referencia, String motivo) {

        static ResultadoPago aprobado(String referencia) {
            return new ResultadoPago(true, referencia, null);
        }

        static ResultadoPago rechazado(String motivo) {
            return new ResultadoPago(false, null, motivo);
        }
    }
}
