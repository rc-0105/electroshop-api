package co.edu.unbosque.electroshop.web;

import co.edu.unbosque.electroshop.dto.DetallePedidoResponse;
import co.edu.unbosque.electroshop.dto.PedidoProcesadoResponse;
import co.edu.unbosque.electroshop.dto.ProcesarPedidoRequest;
import co.edu.unbosque.electroshop.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/pedidos")
@Tag(name = "Pedidos", description = "Procesamiento y consulta de pedidos de ElectroShop")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping("/procesar")
    @Operation(
            summary = "Procesa un pedido",
            description = """
                    Valida el pedido, reserva el inventario, procesa el pago y genera la orden de \
                    preparación en una sola transacción.

                    **Requisito de rendimiento:** p95 ≤ 100 ms con una carga sostenida de \
                    1.500 pedidos por minuto.""")
    @ApiResponse(responseCode = "201", description = "Pedido confirmado",
            content = @Content(schema = @Schema(implementation = PedidoProcesadoResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solicitud mal formada",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "402", description = "Pago rechazado por la pasarela",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Stock insuficiente",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "422", description = "Cliente o producto inexistente",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "No se pudo reservar el inventario a tiempo",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<PedidoProcesadoResponse> procesar(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
                    name = "Pedido de dos productos",
                    value = """
                            {
                              "clienteId": 1250,
                              "metodoPago": "TARJETA_CREDITO",
                              "direccionEnvio": "Calle 80 # 69-70, Bogotá",
                              "items": [
                                { "sku": "ELEC-54321", "cantidad": 1 },
                                { "sku": "ELEC-1203",  "cantidad": 2 }
                              ]
                            }""")))
            @Valid @RequestBody ProcesarPedidoRequest solicitud) {

        PedidoProcesadoResponse respuesta = pedidoService.procesar(solicitud);
        return ResponseEntity
                .created(URI.create("/api/pedidos/detalle/" + respuesta.pedidoId()))
                .body(respuesta);
    }

    @GetMapping("/detalle/{id}")
    @Operation(
            summary = "Consulta el detalle de un pedido",
            description = """
                    Devuelve el pedido con su cliente, ítems, pago y orden de preparación.

                    **Requisito de rendimiento:** p95 ≤ 50 ms.""")
    @ApiResponse(responseCode = "200", description = "Pedido encontrado")
    @ApiResponse(responseCode = "404", description = "El pedido no existe",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public DetallePedidoResponse detalle(
            @Parameter(description = "Identificador del pedido", example = "250000") @PathVariable Long id) {
        return pedidoService.obtenerDetalle(id);
    }
}
