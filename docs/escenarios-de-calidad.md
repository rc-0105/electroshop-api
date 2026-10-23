# Escenarios de calidad de rendimiento (borrador para el informe)

Formato de Bass, Clements y Kazman (*Software Architecture in Practice*, 4.ª ed.).
Ajusten los valores marcados como **supuesto** si el equipo decide otra cosa, pero
justifíquenlo: el criterio 2 de la rúbrica pide que las variaciones estén argumentadas.

## Estadístico de decisión

**Percentil 95 (p95)** del tiempo de respuesta medido por JMeter, calculado sobre la
ventana estable de la prueba (se descartan los primeros 60 s: subida de hilos y
calentamiento de la JVM). Se reporta también el **p99** como evidencia complementaria.

Por qué no el promedio: el promedio esconde la cola. Con 25 pedidos por segundo, un p95
de 300 ms significa que más de un cliente por segundo espera más del triple del límite,
aunque el promedio quede en 80 ms. El requisito dice "garantizar un máximo", así que lo
que importa es cuántas peticiones lo superan.

Por qué p95 y no el máximo: el máximo depende de eventos aislados (una pausa del
recolector de basura, el sistema operativo) y no es reproducible entre corridas.

## Escenario 1 · Procesamiento de pedidos bajo carga pico

| Parte | Valor |
|---|---|
| Fuente | Clientes de ElectroShop desde la tienda en línea |
| Estímulo | Llegan pedidos a una tasa sostenida de 1.500 por minuto (25/s) |
| Artefacto | `POST /api/pedidos/procesar` (validación, reserva de inventario, pago, orden de preparación) |
| Entorno | Operación pico (lanzamiento o promoción), base de datos con volumen de producción simulado (100.000 productos, 500.000 pedidos históricos), en simultáneo con las consultas del escenario 2 |
| Respuesta | El pedido queda confirmado, con stock descontado, pago registrado y orden generada |
| Medida de respuesta | p95 ≤ 100 ms; throughput real ≥ 1.500 pedidos/min durante toda la ventana; errores ≤ 1 % (**supuesto**) |

## Escenario 2 · Consulta del detalle de un pedido

| Parte | Valor |
|---|---|
| Fuente | Clientes y área de servicio al cliente |
| Estímulo | Consultas del detalle de pedidos existentes a 3.000 por minuto (**supuesto**: dos consultas por pedido, la confirmación y un seguimiento) |
| Artefacto | `GET /api/pedidos/detalle/{id}` |
| Entorno | El mismo del escenario 1, en simultáneo (comparten base de datos y pool de conexiones) |
| Respuesta | Se devuelve el pedido con cliente, ítems, pago y orden de preparación |
| Medida de respuesta | p95 ≤ 50 ms; errores ≤ 1 % |

## Escenario 3 (variación) · Lanzamiento con demanda concentrada

| Parte | Valor |
|---|---|
| Estímulo | 1.500 pedidos/min donde el primer ítem de cada pedido es uno de 20 productos (grupo B del plan) |
| Entorno | Igual al escenario 1 |
| Medida de respuesta | p95 ≤ 100 ms; errores ≤ 1 %; ningún producto queda con stock negativo |

Justificación: el enunciado menciona los lanzamientos de productos. En ese caso varias
transacciones compiten por las mismas filas de inventario, un comportamiento que la carga
con productos aleatorios (escenario 1) no ejercita.

## Condiciones de la medición (iguales en la línea base y en la medición final)

- Misma máquina, mismo hardware, sin otras aplicaciones pesadas abiertas.
- Base de datos restablecida antes de cada corrida (`db/01_schema.sql` + `db/02_seed.sql`).
- Mismo plan `.jmx` y mismos parámetros (`-J`), en modo no-GUI.
- Duración: 5 minutos de carga más 30 s de subida de hilos; se analizan los últimos
  ~4,5 minutos (se descartan los primeros 60 s).
- Cada configuración se ejecuta 3 veces y se reporta la mediana de los p95.
- Documentar: CPU, RAM, sistema operativo, versión de Java, MySQL y JMeter, y si JMeter
  corre en la misma máquina que la API (si es así, anotarlo como limitación: comparten CPU).
