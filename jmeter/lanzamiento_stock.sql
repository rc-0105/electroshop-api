-- =====================================================================
-- ElectroShop · Condición del experimento de lanzamiento extremo
-- MySQL 8.0 · Ejecutar DESPUÉS de 01_schema.sql, 02_seed.sql y 03_indices.sql
--
-- Uso:   mysql -u root -p electroshop < jmeter/lanzamiento_stock.sql
--
-- NO es una táctica de rendimiento. Con 1 o 5 SKU de lanzamiento, los
-- ~7.500 pedidos de una corrida agotan las ~2.000 unidades sembradas y la
-- prueba terminaría midiendo rechazos por falta de stock (409) en lugar de
-- contención por bloqueos. Con 20 SKU (escenario 3 estándar) no hace falta.
-- =====================================================================

USE electroshop;

UPDATE producto SET stock = 100000 WHERE id <= 20;
