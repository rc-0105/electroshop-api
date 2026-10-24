# ElectroShop · API de pedidos

Taller de rendimiento · Ingeniería de Software 2 · Universidad El Bosque

API en Spring Boot 3.5 + MySQL 8 con los dos endpoints del enunciado, un plan de JMeter que
reproduce la carga del requisito y un script que verifica los resultados contra los límites.
**Esta es la versión 1 (línea base), sin optimizar a propósito**: las tácticas de la
actividad 3 se aplican después de medirla.

| Endpoint | Requisito |
|---|---|
| `POST /api/pedidos/procesar` | p95 ≤ 100 ms con 1.500 pedidos/min |
| `GET /api/pedidos/detalle/{id}` | p95 ≤ 50 ms |

## Requisitos

- Java 21
- Maven 3.9+ (IntelliJ ya lo trae integrado)
- MySQL 8.0
- Apache JMeter 5.6.3
- Python 3.8+ (solo para `analizar_resultados.py`; usa la biblioteca estándar)

## Estructura

```
electroshop-api/
├── pom.xml
├── db/
│   ├── 01_schema.sql          esquema (solo índices por defecto: PK y FK)
│   └── 02_seed.sql            50k clientes, 100k productos, 500k pedidos, 1,5M ítems
├── docs/
│   └── escenarios-de-calidad.md   borrador de escenarios y estadístico para el informe
├── jmeter/
│   ├── ElectroShop_Rendimiento.jmx
│   ├── ejecutar_prueba.sh / .bat  corrida no-GUI + reporte HTML + resumen
│   └── analizar_resultados.py     p50/p90/p95/p99, carga real y veredicto
└── src/main/java/co/edu/unbosque/electroshop/
    ├── domain/        entidades JPA
    ├── repository/    Spring Data JPA
    ├── service/       PedidoService, pasarela de pago simulada, métricas por fase
    ├── web/           PedidoController (anotado con OpenAPI)
    ├── dto/           solicitudes y respuestas
    ├── exception/     errores como application/problem+json
    └── config/        OpenAPI
```

## Puesta en marcha

### 1. Base de datos

```bash
mysql -u root -p < db/01_schema.sql
mysql -u root -p electroshop < db/02_seed.sql      # 1 a 3 minutos
```

El esquema crea el usuario `electroshop` / `electroshop`. Para usar otro, defina las
variables `DB_USER`, `DB_PASSWORD`, `DB_HOST` y `DB_PORT` antes de arrancar la API.

### 2. API

**IntelliJ:** *File → Open* → seleccionar `pom.xml` → *Open as Project*. Esperar a que
Maven descargue las dependencias y ejecutar `ElectroShopApplication`.

**Consola:**

```bash
mvn spring-boot:run
# o, para medir, mejor el jar empaquetado:
mvn -DskipTests package && java -jar target/electroshop-api-1.0.0.jar
```

### 3. Documentación OpenAPI

- Swagger UI: http://localhost:8080/swagger-ui.html
- Contrato JSON: http://localhost:8080/v3/api-docs
- Contrato YAML (para anexar al informe): http://localhost:8080/v3/api-docs.yaml

### 4. Prueba rápida

```bash
curl -s http://localhost:8080/api/pedidos/detalle/250000

curl -s -X POST http://localhost:8080/api/pedidos/procesar \
  -H 'Content-Type: application/json' \
  -d '{"clienteId":1250,"metodoPago":"TARJETA_CREDITO","direccionEnvio":"Calle 80 # 69-70, Bogotá",
       "items":[{"sku":"ELEC-54321","cantidad":1},{"sku":"ELEC-1203","cantidad":2}]}'
```

## Prueba de carga

### Cómo genera el plan los 1.500 pedidos por minuto

La guía del curso sugiere 100 hilos, 100 s de subida y una iteración: eso produce unas
100 peticiones en total (≈ 1 por segundo), muy lejos de las 25 por segundo del requisito.
El plan usa en cambio:

| Grupo | Hilos | Duración | Tasa (Temporizador de Rendimiento Constante) |
|---|---|---|---|
| 0 · Verificación previa | 1 | 1 iteración | — (si falla, la prueba se detiene) |
| A · Pedidos | 40 | 300 s + 30 s de subida, bucle infinito | 1.500/min |
| B · Lanzamiento (desactivado) | 40 | igual | 1.500/min, demanda concentrada en 20 SKU |
| C · Consultas de detalle | 30 | igual | 3.000/min (supuesto a justificar) |

El temporizador **solo frena** a los hilos para no pasar de la tasa; no puede acelerarlos.
Por eso hacen falta hilos de sobra: con 40 hilos la tasa se sostiene mientras el tiempo de
respuesta sea menor a 40 / 25 = 1,6 s. Si la API se vuelve más lenta, el throughput real
cae por debajo de 1.500/min, y eso también es un incumplimiento que el script reporta.

Todos los parámetros se cambian desde la línea de comandos sin editar el `.jmx`:

| Propiedad | Por defecto | Significado |
|---|---|---|
| `host`, `port` | localhost, 8080 | dónde corre la API |
| `duracion` | 300 | segundos de carga por grupo |
| `rampa` | 30 | segundos para arrancar todos los hilos |
| `pedidos_min` | 1500 | tasa objetivo de pedidos |
| `consultas_min` | 3000 | tasa objetivo de consultas |
| `hilos_pedidos`, `hilos_consultas` | 40, 30 | hilos disponibles por grupo |
| `productos_lanzamiento` | 20 | SKU que concentran la demanda en el grupo B |

### Ejecutar una corrida

Usen la interfaz gráfica de JMeter solo para revisar o editar el plan. Para medir, el modo
no-GUI (las buenas prácticas de JMeter lo exigen: la GUI y "Ver Árbol de Resultados"
consumen CPU y distorsionan los tiempos):

```bash
cd jmeter
./ejecutar_prueba.sh linea_base                    # Linux / macOS
ejecutar_prueba.bat linea_base                     # Windows
./ejecutar_prueba.sh linea_base_10min -Jduracion=600
```

Cada corrida deja en `jmeter/resultados/<nombre>/`:

- `resultados.jtl` — todas las muestras
- `reporte/index.html` — dashboard de JMeter con percentiles y gráficas (listo para capturas)
- `resumen.txt` — veredicto contra los requisitos

### Escenario 3 · lanzamiento

`ElectroShop_Lanzamiento.jmx` es el mismo plan con el grupo A desactivado y el B activado
(demanda concentrada en `productos_lanzamiento` SKU). Los `.bat`/`.sh` usan siempre el plan
principal, así que esta variante se corre directo con JMeter:

```bash
cd jmeter
jmeter -n -t ElectroShop_Lanzamiento.jmx -l resultados/e3/resultados.jtl -e -o resultados/e3/reporte \
  -Jjmeter.save.saveservice.output_format=csv -Jjmeter.save.saveservice.print_field_names=true
python3 analizar_resultados.py resultados/e3/resultados.jtl
```

Para el lanzamiento extremo (`-Jproductos_lanzamiento=5` o `=1`), aplicar antes
`lanzamiento_stock.sql`: sin él, el stock de esos SKU se agota a mitad de la corrida.

`corridas_escenario3.sh` automatiza el ciclo completo de cada corrida (restablecer la BD,
arrancar la API del commit que corresponde a la táctica, correr el plan, analizar y guardar
en `stock.txt` la verificación de stock negativo). Sin argumentos repite las corridas que
completaron el escenario 3; con `<nombre> <base|t1|t2> <skus>` hace una sola:

```bash
cd jmeter
./corridas_escenario3.sh e3_lanzamiento_t2_1sku_4 t2 1
```

### Analizar y comparar

```bash
python3 analizar_resultados.py resultados/linea_base/resultados.jtl
python3 analizar_resultados.py resultados/linea_base/resultados.jtl resultados/final/resultados.jtl --markdown
```

El script descarta los primeros 60 s (subida y calentamiento de la JVM), calcula p50/p90/p95/p99,
la carga real por minuto (responde "¿cómo verificaron que la carga fue 1.500/min?") y marca
CUMPLE / NO CUMPLE. Con dos archivos imprime la tabla de comparación para el informe.
Opciones: `--percentil 99`, `--descartar 90`, `--max-errores 0.5`.

### Reglas para que las comparaciones sean válidas

1. **Restablecer la base antes de cada corrida** (`01_schema.sql` + `02_seed.sql`): cada
   prueba inserta ~7.500 pedidos y descuenta stock.
2. Misma máquina, mismos parámetros, sin otras aplicaciones pesadas abiertas.
3. Repetir cada configuración 3 veces y reportar la mediana.
4. Aplicar **una táctica a la vez** y medir después de cada una; si se aplican dos juntas
   no se puede atribuir la mejora.

Tras una corrida, esta consulta debe devolver 0 (integridad del inventario):

```sql
SELECT COUNT(*) FROM electroshop.producto WHERE stock < 0;
```

## ¿Dónde se va el tiempo? (análisis de la línea base)

La API publica métricas que ayudan a explicar los resultados de JMeter:

| Qué | URL |
|---|---|
| Tiempo por fase del procesamiento (validación, reserva de inventario, pago, orden) con p50/p95/p99 | `/actuator/metrics/electroshop.pedido.fase?tag=fase:reserva_inventario` (cambiar la fase) |
| Tiempos HTTP por endpoint | `/actuator/metrics/http.server.requests?tag=uri:/api/pedidos/procesar` |
| Peticiones esperando una conexión de BD | `/actuator/metrics/hikaricp.connections.pending` |
| Tiempo esperando una conexión de BD | `/actuator/metrics/hikaricp.connections.acquire` |

Otras herramientas útiles: `EXPLAIN` sobre las consultas en MySQL, y activar
`logging.level.org.hibernate.SQL: DEBUG` en `application.yml` **para una sola petición**
(nunca durante la carga) y contar cuántas consultas genera cada endpoint.

### Decisiones de la versión 1 que conviene examinar con datos

Están escritas de la forma más directa posible. No las cambien sin medir antes:

- La reserva de inventario busca el producto por `sku` con `SELECT … FOR UPDATE`, y la
  columna `sku` no tiene índice. Vale la pena mirar el `EXPLAIN` y pensar qué filas bloquea
  InnoDB cuando no hay índice.
- El pago (30 ms simulados, `electroshop.pago.latencia-ms`) ocurre dentro de la misma
  transacción que tiene bloqueado el inventario y ocupada una conexión del pool.
- El detalle navega relaciones `LAZY` de JPA: pedido → cliente → ítems → producto de cada
  ítem → pago → orden. Contar las consultas por petición.
- Pool de conexiones de HikariCP en su valor por defecto (10), compartido por ambos endpoints.

Y una advertencia para la sustentación: la caché encaja en lecturas (el detalle de pedidos
históricos, datos de catálogo), **no** en el stock que se reserva. Cachear el inventario en
`procesar` puede vender unidades que ya no existen.

## Sugerencia de trabajo con Git

```bash
git tag v1-linea-base          # antes de cualquier optimización
# una rama o un commit por táctica, con su corrida medida
```

Así las imágenes de "código original vs. modificado" del informe salen directamente de
`git diff v1-linea-base`.
