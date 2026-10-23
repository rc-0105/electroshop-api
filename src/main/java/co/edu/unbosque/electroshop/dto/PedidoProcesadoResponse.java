package co.edu.unbosque.electroshop.dto;

import co.edu.unbosque.electroshop.domain.EstadoPedido;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Confirmación del pedido procesado")
public record PedidoProcesadoResponse(

        @Schema(example = "500001")
        Long pedidoId,

        @Schema(example = "CONFIRMADO")
        EstadoPedido estado,

        @Schema(description = "Total cobrado en COP", example = "3720100.00")
        BigDecimal total,

        @Schema(description = "Referencia devuelta por la pasarela de pago", example = "PAY-3F2A9C1B7E4D")
        String referenciaPago,

        @Schema(description = "Orden de preparación generada para la bodega", example = "500001")
        Long ordenPreparacionId,

        @Schema(example = "BOG-NORTE")
        String bodega,

        LocalDateTime fecha
) {
}
