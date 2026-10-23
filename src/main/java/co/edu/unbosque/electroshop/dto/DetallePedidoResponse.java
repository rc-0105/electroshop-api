package co.edu.unbosque.electroshop.dto;

import co.edu.unbosque.electroshop.domain.EstadoPedido;
import co.edu.unbosque.electroshop.domain.MetodoPago;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Detalle completo de un pedido")
public record DetallePedidoResponse(
        Long id,
        LocalDateTime fecha,
        EstadoPedido estado,
        BigDecimal total,
        String direccionEnvio,
        ClienteResumen cliente,
        List<Item> items,
        PagoResumen pago,
        OrdenResumen ordenPreparacion
) {

    @Schema(name = "ClienteResumen")
    public record ClienteResumen(Long id, String nombre, String email, String ciudad) {
    }

    @Schema(name = "ItemPedido")
    public record Item(String sku, String nombre, int cantidad, BigDecimal precioUnitario, BigDecimal subtotal) {
    }

    @Schema(name = "PagoResumen")
    public record PagoResumen(String referencia, MetodoPago metodo, String estado, BigDecimal monto,
                              LocalDateTime fecha) {
    }

    @Schema(name = "OrdenPreparacionResumen")
    public record OrdenResumen(Long id, String bodega, String estado, String prioridad, LocalDateTime fechaCreacion) {
    }
}
