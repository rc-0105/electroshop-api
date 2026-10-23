package co.edu.unbosque.electroshop.dto;

import co.edu.unbosque.electroshop.domain.MetodoPago;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Pedido que el cliente envía para ser procesado")
public record ProcesarPedidoRequest(

        @Schema(description = "Identificador del cliente", example = "1250")
        @NotNull @Positive
        Long clienteId,

        @Schema(description = "Medio de pago", example = "TARJETA_CREDITO")
        @NotNull
        MetodoPago metodoPago,

        @Schema(description = "Dirección de entrega", example = "Calle 80 # 69-70, Bogotá")
        @NotBlank @Size(max = 200)
        String direccionEnvio,

        @Schema(description = "Productos del pedido (máximo 20 líneas)")
        @NotEmpty @Size(max = 20)
        List<@Valid Item> items
) {

    @Schema(name = "ItemPedidoRequest", description = "Línea del pedido")
    public record Item(

            @Schema(description = "Código comercial del producto", example = "ELEC-54321")
            @NotBlank @Size(max = 20)
            String sku,

            @Schema(description = "Unidades solicitadas", example = "1")
            @NotNull @Min(1) @Max(10)
            Integer cantidad
    ) {
    }
}
