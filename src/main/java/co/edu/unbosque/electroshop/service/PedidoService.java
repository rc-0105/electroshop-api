package co.edu.unbosque.electroshop.service;

import co.edu.unbosque.electroshop.domain.Cliente;
import co.edu.unbosque.electroshop.domain.OrdenPreparacion;
import co.edu.unbosque.electroshop.domain.Pago;
import co.edu.unbosque.electroshop.domain.Pedido;
import co.edu.unbosque.electroshop.domain.Producto;
import co.edu.unbosque.electroshop.dto.DetallePedidoResponse;
import co.edu.unbosque.electroshop.dto.PedidoProcesadoResponse;
import co.edu.unbosque.electroshop.dto.ProcesarPedidoRequest;
import co.edu.unbosque.electroshop.exception.PagoRechazadoException;
import co.edu.unbosque.electroshop.exception.PedidoInvalidoException;
import co.edu.unbosque.electroshop.exception.RecursoNoEncontradoException;
import co.edu.unbosque.electroshop.exception.StockInsuficienteException;
import co.edu.unbosque.electroshop.repository.ClienteRepository;
import co.edu.unbosque.electroshop.repository.OrdenPreparacionRepository;
import co.edu.unbosque.electroshop.repository.PagoRepository;
import co.edu.unbosque.electroshop.repository.PedidoRepository;
import co.edu.unbosque.electroshop.repository.ProductoRepository;
import co.edu.unbosque.electroshop.service.MetricasPedido.Fase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Versión 1 (línea base) del procesamiento y la consulta de pedidos.
 *
 * Está escrita de la forma más directa posible con JPA, sin optimizaciones. Antes de
 * cambiar algo aquí, medir: cada táctica de la actividad 3 debe responder a un hallazgo.
 */
@Service
public class PedidoService {

    private static final BigDecimal UMBRAL_PRIORIDAD_ALTA = new BigDecimal("10000000");

    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final PedidoRepository pedidoRepository;
    private final PagoRepository pagoRepository;
    private final OrdenPreparacionRepository ordenRepository;
    private final PasarelaPagoSimulada pasarela;
    private final MetricasPedido metricas;

    public PedidoService(ClienteRepository clienteRepository,
                         ProductoRepository productoRepository,
                         PedidoRepository pedidoRepository,
                         PagoRepository pagoRepository,
                         OrdenPreparacionRepository ordenRepository,
                         PasarelaPagoSimulada pasarela,
                         MetricasPedido metricas) {
        this.clienteRepository = clienteRepository;
        this.productoRepository = productoRepository;
        this.pedidoRepository = pedidoRepository;
        this.pagoRepository = pagoRepository;
        this.ordenRepository = ordenRepository;
        this.pasarela = pasarela;
        this.metricas = metricas;
    }

    /**
     * Valida el pedido, reserva el inventario, cobra y genera la orden de preparación,
     * todo dentro de una única transacción: si el pago se rechaza, el stock reservado
     * se devuelve con el rollback.
     */
    @Transactional
    public PedidoProcesadoResponse procesar(ProcesarPedidoRequest solicitud) {
        // 1. Validación
        long inicio = System.nanoTime();
        Cliente cliente = clienteRepository.findById(solicitud.clienteId())
                .orElseThrow(() -> new PedidoInvalidoException(
                        "El cliente " + solicitud.clienteId() + " no existe"));

        // Une líneas repetidas del mismo SKU y las ordena: bloquear siempre en el mismo
        // orden evita interbloqueos entre pedidos que comparten productos.
        Map<String, Integer> unidadesPorSku = solicitud.items().stream()
                .collect(Collectors.toMap(item -> item.sku().trim(),
                        ProcesarPedidoRequest.Item::cantidad, Integer::sum, TreeMap::new));
        metricas.registrar(Fase.VALIDACION, inicio);

        // 2. Reserva de inventario
        inicio = System.nanoTime();
        Pedido pedido = new Pedido(cliente, solicitud.direccionEnvio());
        for (Map.Entry<String, Integer> linea : unidadesPorSku.entrySet()) {
            String sku = linea.getKey();
            int unidades = linea.getValue();

            Producto producto = productoRepository.buscarPorSkuParaReserva(sku)
                    .orElseThrow(() -> new PedidoInvalidoException("El producto " + sku + " no existe"));
            if (!producto.isActivo()) {
                throw new PedidoInvalidoException("El producto " + sku + " no está disponible");
            }
            if (producto.getStock() < unidades) {
                throw new StockInsuficienteException(sku, producto.getStock(), unidades);
            }
            producto.reservar(unidades);
            pedido.agregarItem(producto, unidades);
        }
        pedidoRepository.save(pedido);
        metricas.registrar(Fase.RESERVA_INVENTARIO, inicio);

        // 3. Pago
        inicio = System.nanoTime();
        PasarelaPagoSimulada.ResultadoPago resultado = pasarela.cobrar(pedido.getTotal(), solicitud.metodoPago());
        if (!resultado.aprobado()) {
            throw new PagoRechazadoException(resultado.motivo());   // rollback: libera el stock
        }
        Pago pago = pagoRepository.save(new Pago(pedido, pedido.getTotal(), solicitud.metodoPago(),
                "APROBADO", resultado.referencia()));
        pedido.confirmar();
        metricas.registrar(Fase.PAGO, inicio);

        // 4. Orden de preparación
        inicio = System.nanoTime();
        String bodega = asignarBodega(cliente.getCiudad());
        String prioridad = pedido.getTotal().compareTo(UMBRAL_PRIORIDAD_ALTA) >= 0 ? "ALTA" : "NORMAL";
        OrdenPreparacion orden = ordenRepository.save(new OrdenPreparacion(pedido, bodega, prioridad));
        metricas.registrar(Fase.ORDEN_PREPARACION, inicio);

        return new PedidoProcesadoResponse(pedido.getId(), pedido.getEstado(), pedido.getTotal(),
                pago.getReferencia(), orden.getId(), bodega, pedido.getFecha());
    }

    /**
     * Consulta el pedido completo navegando las relaciones de JPA (todas son LAZY).
     */
    @Transactional(readOnly = true)
    public DetallePedidoResponse obtenerDetalle(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El pedido " + id + " no existe"));

        Cliente cliente = pedido.getCliente();
        DetallePedidoResponse.ClienteResumen clienteResumen = new DetallePedidoResponse.ClienteResumen(
                cliente.getId(), cliente.getNombre(), cliente.getEmail(), cliente.getCiudad());

        List<DetallePedidoResponse.Item> items = pedido.getDetalles().stream()
                .map(d -> new DetallePedidoResponse.Item(
                        d.getProducto().getSku(),
                        d.getProducto().getNombre(),
                        d.getCantidad(),
                        d.getPrecioUnitario(),
                        d.getSubtotal()))
                .toList();

        DetallePedidoResponse.PagoResumen pago = pagoRepository.findFirstByPedidoIdOrderByFechaDesc(id)
                .map(p -> new DetallePedidoResponse.PagoResumen(
                        p.getReferencia(), p.getMetodo(), p.getEstado(), p.getMonto(), p.getFecha()))
                .orElse(null);

        DetallePedidoResponse.OrdenResumen orden = ordenRepository.findFirstByPedidoIdOrderByFechaCreacionDesc(id)
                .map(o -> new DetallePedidoResponse.OrdenResumen(
                        o.getId(), o.getBodega(), o.getEstado(), o.getPrioridad(), o.getFechaCreacion()))
                .orElse(null);

        return new DetallePedidoResponse(pedido.getId(), pedido.getFecha(), pedido.getEstado(), pedido.getTotal(),
                pedido.getDireccionEnvio(), clienteResumen, items, pago, orden);
    }

    private static String asignarBodega(String ciudad) {
        return switch (ciudad) {
            case "Bogotá" -> "BOG-NORTE";
            case "Medellín" -> "MED-CENTRO";
            case "Cali" -> "CAL-SUR";
            case "Barranquilla" -> "BAQ-PUERTO";
            default -> "BOG-CENTRAL";
        };
    }
}
