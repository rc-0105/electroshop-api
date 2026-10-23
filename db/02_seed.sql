-- =====================================================================
-- ElectroShop · Datos de prueba con volumen realista
-- MySQL 8.0 · Ejecutar DESPUÉS de 01_schema.sql
--
-- Uso:   mysql -u root -p electroshop < db/02_seed.sql
-- Tarda entre 1 y 3 minutos según la máquina.
--
-- Volumen generado (pensado para que los índices y el plan de consulta
-- importen de verdad; con pocas filas cualquier consulta es "rápida"):
--     clientes ..............   50.000
--     productos .............  100.000   (SKU: ELEC-1 … ELEC-100000)
--     pedidos históricos ....  500.000   (id 1 … 500000)
--     ítems de pedido ....... ~1.500.000 (1 a 5 por pedido, ~3 en promedio)
--     pagos .................  500.000   (uno por pedido)
--     órdenes de preparación   500.000   (una por pedido)
--
-- Los datos son deterministas (mismas filas en cada ejecución) para que
-- la línea base y la medición posterior se hagan sobre los mismos datos.
-- =====================================================================

USE electroshop;

SET SESSION foreign_key_checks = 0;
SET SESSION unique_checks = 0;

-- ---------------------------------------------------------------------
-- Tabla auxiliar de números 0 … 999.999
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_digito;
DROP TABLE IF EXISTS tmp_numero;
CREATE TABLE tmp_digito (d INT NOT NULL PRIMARY KEY);
INSERT INTO tmp_digito VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

CREATE TABLE tmp_numero (n INT NOT NULL PRIMARY KEY);
INSERT INTO tmp_numero (n)
SELECT a.d + 10*b.d + 100*c.d + 1000*e.d + 10000*f.d + 100000*g.d
FROM tmp_digito a, tmp_digito b, tmp_digito c, tmp_digito e, tmp_digito f, tmp_digito g;

-- ---------------------------------------------------------------------
-- Clientes (id = n + 1)
-- ---------------------------------------------------------------------
INSERT INTO cliente (id, documento, nombre, email, ciudad, fecha_registro)
SELECT n + 1,
       CAST(1000000000 + n AS CHAR),
       CONCAT(ELT(1 + n % 12, 'Ana', 'Luis', 'María', 'Carlos', 'Laura', 'Andrés',
                              'Valentina', 'Juan', 'Camila', 'Santiago', 'Daniela', 'Felipe'),
              ' ',
              ELT(1 + (n DIV 12) % 10, 'Gómez', 'Rodríguez', 'Martínez', 'López', 'García',
                                       'Hernández', 'Díaz', 'Moreno', 'Rojas', 'Vargas')),
       CONCAT('cliente', n + 1, '@correo.com'),
       ELT(1 + n % 8, 'Bogotá', 'Medellín', 'Cali', 'Barranquilla', 'Bucaramanga',
                      'Cartagena', 'Pereira', 'Bogotá'),
       TIMESTAMP('2022-01-01') + INTERVAL (n % 1300) DAY
FROM tmp_numero
WHERE n < 50000;

-- ---------------------------------------------------------------------
-- Productos (id = n + 1, sku = 'ELEC-' + id)
-- ---------------------------------------------------------------------
INSERT INTO producto (id, sku, nombre, categoria, precio, stock, activo)
SELECT n + 1,
       CONCAT('ELEC-', n + 1),
       CONCAT(ELT(1 + n % 10, 'Televisor', 'Portátil', 'Celular', 'Tableta', 'Audífonos',
                              'Monitor', 'Consola', 'Parlante', 'Reloj inteligente', 'Cámara'),
              ' ',
              ELT(1 + (n DIV 10) % 8, 'Samsung', 'LG', 'Lenovo', 'Xiaomi', 'Sony',
                                      'Apple', 'HP', 'Asus'),
              ' modelo ', n + 1),
       ELT(1 + n % 10, 'TELEVISORES', 'COMPUTADORES', 'CELULARES', 'TABLETAS', 'AUDIO',
                       'COMPUTADORES', 'VIDEOJUEGOS', 'AUDIO', 'WEARABLES', 'FOTOGRAFIA'),
       ROUND(80000 + ((n * 7919) % 7900000), -2),
       2000 + (n % 3000),                 -- stock holgado: una prueba larga no lo agota
       TRUE                               -- todos activos: el plan JMeter elige SKU al azar
FROM tmp_numero
WHERE n < 100000;

-- ---------------------------------------------------------------------
-- Pedidos históricos (id = n + 1). El total se calcula después.
-- ---------------------------------------------------------------------
INSERT INTO pedido (id, cliente_id, fecha, estado, total, direccion_envio)
SELECT n + 1,
       1 + ((n * 7) % 50000),
       TIMESTAMP('2025-01-01') + INTERVAL ((n * 37) % 900000) MINUTE,
       ELT(1 + n % 10, 'ENTREGADO', 'ENTREGADO', 'ENTREGADO', 'ENTREGADO', 'ENTREGADO',
                       'ENTREGADO', 'ENVIADO', 'ENVIADO', 'CONFIRMADO', 'CANCELADO'),
       0,
       CONCAT('Calle ', 1 + n % 200, ' # ', 1 + n % 99, '-', 1 + (n * 3) % 99)
FROM tmp_numero
WHERE n < 500000;

-- ---------------------------------------------------------------------
-- Ítems: entre 1 y 5 por pedido
-- ---------------------------------------------------------------------
INSERT INTO detalle_pedido (pedido_id, producto_id, cantidad, precio_unitario, subtotal)
SELECT p.id,
       pr.id,
       1 + ((p.id + k.d) % 3),
       pr.precio,
       pr.precio * (1 + ((p.id + k.d) % 3))
FROM pedido p
JOIN tmp_digito k ON k.d < 1 + ((p.id * 31) % 5)
JOIN producto pr  ON pr.id = 1 + ((p.id * 7919 + k.d * 104729) % 100000)
ORDER BY p.id, k.d;

UPDATE pedido p
JOIN (SELECT pedido_id, SUM(subtotal) AS total
      FROM detalle_pedido
      GROUP BY pedido_id) t ON t.pedido_id = p.id
SET p.total = t.total;

-- ---------------------------------------------------------------------
-- Pagos y órdenes de preparación (uno por pedido)
-- ---------------------------------------------------------------------
INSERT INTO pago (pedido_id, monto, metodo, estado, referencia, fecha)
SELECT id,
       total,
       ELT(1 + id % 5, 'TARJETA_CREDITO', 'TARJETA_DEBITO', 'PSE', 'NEQUI', 'TARJETA_CREDITO'),
       IF(estado = 'CANCELADO', 'REEMBOLSADO', 'APROBADO'),
       CONCAT('PAY-', LPAD(id, 12, '0')),
       fecha + INTERVAL 1 MINUTE
FROM pedido
ORDER BY id;

INSERT INTO orden_preparacion (pedido_id, bodega, estado, prioridad, fecha_creacion)
SELECT p.id,
       CASE c.ciudad
           WHEN 'Bogotá'       THEN 'BOG-NORTE'
           WHEN 'Medellín'     THEN 'MED-CENTRO'
           WHEN 'Cali'         THEN 'CAL-SUR'
           WHEN 'Barranquilla' THEN 'BAQ-PUERTO'
           ELSE 'BOG-CENTRAL'
       END,
       IF(p.estado IN ('ENTREGADO', 'ENVIADO'), 'DESPACHADA',
          IF(p.estado = 'CANCELADO', 'ANULADA', 'PENDIENTE')),
       IF(p.total >= 10000000, 'ALTA', 'NORMAL'),
       p.fecha + INTERVAL 2 MINUTE
FROM pedido p
JOIN cliente c ON c.id = p.cliente_id
ORDER BY p.id;

-- ---------------------------------------------------------------------
-- Limpieza y estadísticas para el optimizador
-- ---------------------------------------------------------------------
DROP TABLE tmp_numero;
DROP TABLE tmp_digito;

SET SESSION foreign_key_checks = 1;
SET SESSION unique_checks = 1;

ANALYZE TABLE cliente, producto, pedido, detalle_pedido, pago, orden_preparacion;

SELECT 'clientes' AS tabla, COUNT(*) AS filas FROM cliente
UNION ALL SELECT 'productos', COUNT(*) FROM producto
UNION ALL SELECT 'pedidos', COUNT(*) FROM pedido
UNION ALL SELECT 'detalle_pedido', COUNT(*) FROM detalle_pedido
UNION ALL SELECT 'pagos', COUNT(*) FROM pago
UNION ALL SELECT 'ordenes_preparacion', COUNT(*) FROM orden_preparacion;
