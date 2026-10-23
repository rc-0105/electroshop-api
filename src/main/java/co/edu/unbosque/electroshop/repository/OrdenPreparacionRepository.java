package co.edu.unbosque.electroshop.repository;

import co.edu.unbosque.electroshop.domain.OrdenPreparacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrdenPreparacionRepository extends JpaRepository<OrdenPreparacion, Long> {

    Optional<OrdenPreparacion> findFirstByPedidoIdOrderByFechaCreacionDesc(Long pedidoId);
}
