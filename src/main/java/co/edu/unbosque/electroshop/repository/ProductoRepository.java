package co.edu.unbosque.electroshop.repository;

import co.edu.unbosque.electroshop.domain.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Busca el producto por SKU y bloquea la fila (SELECT ... FOR UPDATE) para que dos
     * pedidos simultáneos no reserven las mismas unidades.
     *
     * Línea base: la columna sku NO tiene índice. Revisar con EXPLAIN qué filas recorre
     * (y por tanto bloquea) InnoDB para resolver esta consulta.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.sku = :sku")
    Optional<Producto> buscarPorSkuParaReserva(@Param("sku") String sku);
}
