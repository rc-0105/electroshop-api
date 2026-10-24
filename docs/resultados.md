# Resultados de las pruebas de rendimiento

Mediciones de la línea base y de las dos tácticas aplicadas, contra los escenarios de
`escenarios-de-calidad.md`. El entorno (hardware, versiones y limitaciones) está en
`entorno-de-pruebas.md`.

**Cómo leer las tablas.** Cada valor es la **mediana de 3 corridas** del p95 (o del dato
indicado), calculada sobre la ventana estable (se descartan los primeros 60 s). Entre
paréntesis, los p95 de las tres corridas. La evidencia completa de cada corrida (JTL,
reporte HTML de JMeter, `resumen.txt` y, en el escenario 3, `stock.txt`) está en
`jmeter/resultados/<corrida>/`. Ninguna corrida registró errores HTTP (0,00 %).

## Resumen

| Escenario | Requisito | Línea base | T1 · índice en `sku` | T2 · pago fuera de la transacción |
|---|---|---|---|---|
| E1 · procesar pedido | p95 ≤ 100 ms a 1.500/min | 5.322 ms, 458/min ❌ | **66 ms** ✅ | 83 ms ✅ |
| E2 · detalle del pedido | p95 ≤ 50 ms | 4.128 ms ❌ | **7 ms** ✅ | 7 ms ✅ |
| E3 · lanzamiento, 20 SKU | p95 ≤ 100 ms, stock ≥ 0 | 5.103 ms, 481/min ❌ | **84 ms** ✅ | 86 ms ✅ |
| E3 · variación, 5 SKU | ídem | — | 104 ms ❌ (en el límite) | **78 ms** ✅ |
| E3 · variación, 1 SKU | ídem | — | 1.826 ms, 1.344/min ❌ | **95 ms** ✅ |

T1 resuelve los tres escenarios planteados. T2 no mejora la carga normal (la empeora
17 ms), pero es la única configuración que soporta la demanda concentrada en 5 o en 1
producto.

## Línea base: dónde se iba el tiempo

| Endpoint | p95 | p99 | Promedio | Carga real |
|---|---|---|---|---|
| `POST /procesar` | 5.322 ms (5.212 · 5.322 · 5.440) | 5.393 ms | 5.240 ms | 458/min de 1.500 |
| `GET /detalle/{id}` | 4.128 ms (4.044 · 4.128 · 4.230) | 4.185 ms | 4.061 ms | 443/min de 3.000 |

El sistema no llega ni a un tercio de la carga pedida: JMeter no consigue enviar más
pedidos porque cada hilo espera más de 5 s la respuesta.

Diagnóstico (métricas de la API y `EXPLAIN`, detallado en `db/03_indices.sql`):

1. La reserva de inventario busca el producto con `SELECT … WHERE sku = ? FOR UPDATE`, y
   `sku` no tenía índice. `EXPLAIN` muestra `type: ALL` sobre ~99.600 filas.
2. Sin índice, InnoDB bloquea **cada fila que recorre**, no solo la que encuentra. En la
   práctica cada pedido bloquea el catálogo completo y los pedidos se ejecutan de a uno.
3. La fase de reserva tarda ~1.213 ms por pedido. Durante ese tiempo el pedido retiene una
   de las 10 conexiones del pool; el pool se agota (espera promedio de 3.365 ms por
   conexión).
4. El detalle no tiene ningún problema propio: hace fila por una conexión detrás de los
   pedidos. Por eso falla con un p95 de 4 s.

El escenario 3 sin tácticas colapsa igual (5.103 ms; 5.106 · 5.084 · 5.103): el cuello de
botella está antes de que importe cuántos productos se compran.

## Táctica 1 · índice único sobre `producto.sku`

**Idea:** reducir el trabajo que hace la base por cada pedido (que la búsqueda recorra y
bloquee una fila en lugar de todo el catálogo). **Cambio:** `db/03_indices.sql`, sin tocar código.

Se eligió un índice **UNIQUE** y no uno simple: con una búsqueda por igualdad sobre un
índice único, `FOR UPDATE` bloquea solo la fila encontrada; con uno no único InnoDB también
bloquea el hueco siguiente (*next-key lock*). Además la base pasa a garantizar SKU únicos.

| Escenario | p95 procesar | p99 | p95 detalle | Carga |
|---|---|---|---|---|
| E1 + E2 | **66 ms** (65 · 66 · 66) | 75 ms | 7 ms | 1.501/min ✅ |
| E3 · 20 SKU | **84 ms** (84 · 82 · 85) | 108 ms | 8 ms | 1.501/min ✅ |
| E3 · 5 SKU | **104 ms** (115 · 104 · 98) | 147 ms | 7 ms | 1.501/min |
| E3 · 1 SKU | **1.826 ms** (1.993 · 1.821 · 1.826) | 1.858 ms | 1.425 ms | 1.344/min ❌ |

De 5.322 a 66 ms en el escenario 1: una mejora de 80 veces con una sola línea de SQL.

### Por qué T1 no alcanza cuando la demanda se concentra

Con el índice, cada pedido bloquea solo las filas de los productos que compra. Pero el
bloqueo dura **toda la transacción**, y la transacción incluye la llamada a la pasarela de
pago (30 ms simulados). Si todos los pedidos compran el mismo producto, se ejecutan de a
uno sobre esa fila:

- El throughput medido fue **1.344/min** (22,4 pedidos/s): la fila atiende un pedido cada
  ~45 ms. Esa es la duración estimada de la transacción, de la que 30 ms son el pago.
- Se pidieron 1.500/min (25/s), más de lo que la fila puede atender, así que la cola
  crece durante toda la corrida y el p95 sube a casi 2 s.

Además las conexiones quedan ocupadas por pedidos que esperan el bloqueo, así que el
detalle vuelve a hacer fila (p95 de 1.425 ms).

Con 5 SKU la carga se reparte entre 5 filas y no hay colapso, pero las colas cortas sobre
cada fila engordan la cola de la distribución: el p95 queda **en el límite** del
requisito (una de las tres corridas cumplió con 98 ms; la mediana, 104 ms, no). Por la
regla de decisión del informe, T1 con 5 SKU **no cumple**, aunque no colapsa como con 1 SKU.

## Táctica 2 · el pago fuera de la transacción de inventario

**Idea:** reducir el tiempo que cada pedido retiene los recursos compartidos (la fila de
inventario y la conexión del pool). **Cambio:**
`PedidoService.procesar` (commit `a0b4028`), aplicado sobre T1.

| | Antes (T1) | Después (T2) |
|---|---|---|
| Transacciones por pedido | 1 | 2 cortas |
| Pago de 30 ms | dentro de la transacción, con la fila bloqueada y la conexión ocupada | sin transacción, sin bloqueos ni conexión |
| Pago rechazado | el *rollback* devuelve el stock | una transacción de compensación devuelve el stock y cancela el pedido |

| Escenario | p95 procesar | p99 | p95 detalle | Carga | vs. T1 |
|---|---|---|---|---|---|
| E1 + E2 | **83 ms** (82 · 83 · 86) | 101 ms | 7 ms | 1.501/min ✅ | +17 ms |
| E3 · 20 SKU | **86 ms** (86 · 87 · 84) | 106 ms | 7 ms | 1.501/min ✅ | +2 ms |
| E3 · 5 SKU | **78 ms** (84 · 78 · 78) | 101 ms | 7 ms | 1.502/min ✅ | −26 ms |
| E3 · 1 SKU | **95 ms** (96 · 95 · 93) | 118 ms | 7 ms | 1.501/min ✅ | −1.731 ms |

Sin el pago, la fila caliente queda bloqueada solo durante la reserva. La capacidad sobre
un único producto supera los 25 pedidos/s del requisito y la cola desaparece.

## Decisión y tradeoffs

**Configuración final recomendada: T1 + T2.** Es la única que cumple los cinco casos
medidos. El costo de cada táctica, medido y no supuesto:

1. **T2 cuesta latencia en la carga normal.** E1 pasa de 66 a 83 ms (+17 ms, +26 %): cada
   pedido abre y confirma dos transacciones en lugar de una. Se acepta porque sigue dentro
   del requisito y a cambio el sistema soporta un lanzamiento sin colapsar. Si el negocio
   nunca concentrara la demanda, T1 sola sería la mejor opción.
2. **T2 con 1 SKU cumple con poco margen.** 95 ms contra un límite de 100 ms (p99 de
   118 ms). No es una holgura cómoda: con más carga o una pasarela más lenta, el siguiente
   cuello de botella sería la propia transacción de reserva sobre la fila caliente.
3. **T2 cambia la consistencia.** Con una sola transacción, un pago rechazado se deshacía
   con el *rollback*. Ahora hay una ventana entre la reserva y el registro del pago: si la
   aplicación cae en ese momento, el pedido queda en `PENDIENTE_PAGO` con el stock
   reservado. En producción haría falta un proceso que concilie esos pedidos con la
   pasarela. La compensación por pago rechazado sí está implementada.
4. **T1 casi no tiene costo.** Un índice único hace un poco más lentas las inserciones de
   productos y ocupa espacio, algo irrelevante frente a una tabla que se lee en cada pedido.

## Integridad del inventario

El escenario 3 exige que ningún producto quede con stock negativo. Tras cada una de las
11 corridas que completaron el escenario (línea base de E3 y repeticiones de 5 y 1 SKU),
`SELECT COUNT(*) FROM producto WHERE stock < 0` devolvió **0** (ver `stock.txt` de cada
carpeta). Las corridas anteriores no guardaron esta verificación; no se reporta para ellas.

Para 5 y 1 SKU se aplicó antes `jmeter/lanzamiento_stock.sql` (stock de 100.000 unidades
en los productos de lanzamiento). No es una táctica: sin él, el stock sembrado se agota a
mitad de la corrida y la prueba mediría rechazos por falta de stock en lugar de contención.

## Limitaciones

- JMeter, la API y MySQL corren en la misma máquina y compiten por CPU
  (`entorno-de-pruebas.md`). Los valores absolutos serían menores en un servidor dedicado;
  las comparaciones son válidas porque todas las configuraciones se midieron igual.
- La pasarela de pago es simulada (30 ms fijos, sin rechazos). Una pasarela real tiene
  latencia variable, lo que aumentaría la ventaja de T2 y afectaría el margen de E3 con 1 SKU.
- Las corridas duran 5 minutos. No se evaluó el comportamiento sostenido (por ejemplo, el
  crecimiento de la tabla de pedidos durante horas).
