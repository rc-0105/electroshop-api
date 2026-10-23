package co.edu.unbosque.electroshop.repository;

import co.edu.unbosque.electroshop.domain.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
}
