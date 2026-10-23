-- =====================================================================
-- ElectroShop · Táctica 1: índice sobre producto.sku
-- MySQL 8.0 · Ejecutar DESPUÉS de 01_schema.sql y 02_seed.sql
--
-- Uso:   mysql -u root -p electroshop < db/03_indices.sql
--
-- Hallazgo de la línea base (mediana de 3 corridas):
--   - Fase reserva_inventario: ~1.213 ms promedio por pedido.
--   - EXPLAIN de "SELECT ... WHERE sku = ? FOR UPDATE": type ALL,
--     ~99.613 filas recorridas. Sin índice, InnoDB bloquea cada fila que
--     recorre, así que los pedidos se serializan sobre todo el catálogo.
--   - Consecuencia: cada pedido retiene una conexión >1,2 s, el pool (10)
--     se agota (espera promedio 3.365 ms) y el detalle hace fila detrás.
--
-- Por qué UNIQUE y no un índice simple: el SKU identifica al producto
-- (100.000 filas, 100.000 SKU distintos). Con un índice único, una
-- búsqueda por igualdad con FOR UPDATE bloquea solo la fila encontrada;
-- con uno no único InnoDB también bloquea el hueco siguiente (next-key
-- lock). Además, la base pasa a garantizar que no haya SKU repetidos.
-- =====================================================================

USE electroshop;

ALTER TABLE producto ADD UNIQUE INDEX uk_producto_sku (sku);

ANALYZE TABLE producto;
