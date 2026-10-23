package co.edu.unbosque.electroshop.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Temporizadores por fase del procesamiento de un pedido.
 *
 * Sirven para responder "¿dónde se consume el tiempo?" en el análisis de la línea base:
 *   GET /actuator/metrics/electroshop.pedido.fase?tag=fase:reserva_inventario
 * (cada fase publica p50, p95 y p99).
 */
@Component
public class MetricasPedido {

    public enum Fase {
        VALIDACION, RESERVA_INVENTARIO, PAGO, ORDEN_PREPARACION
    }

    private final Map<Fase, Timer> temporizadores = new EnumMap<>(Fase.class);

    public MetricasPedido(MeterRegistry registry) {
        for (Fase fase : Fase.values()) {
            temporizadores.put(fase, Timer.builder("electroshop.pedido.fase")
                    .description("Tiempo por fase de POST /api/pedidos/procesar")
                    .tag("fase", fase.name().toLowerCase())
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(registry));
        }
    }

    public void registrar(Fase fase, long inicioNanos) {
        temporizadores.get(fase).record(System.nanoTime() - inicioNanos, TimeUnit.NANOSECONDS);
    }
}
