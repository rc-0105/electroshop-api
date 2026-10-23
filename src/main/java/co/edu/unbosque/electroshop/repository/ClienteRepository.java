package co.edu.unbosque.electroshop.repository;

import co.edu.unbosque.electroshop.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
}
