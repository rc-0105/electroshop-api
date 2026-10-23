package co.edu.unbosque.electroshop.repository;

import co.edu.unbosque.electroshop.domain.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    /** Último pago registrado para el pedido (un pedido podría tener reintentos). */
    Optional<Pago> findFirstByPedidoIdOrderByFechaDesc(Long pedidoId);
}
