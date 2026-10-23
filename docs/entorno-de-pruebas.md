# Entorno de las pruebas de rendimiento

Condiciones en las que se midieron la línea base y las tácticas. Son las mismas para todas
las corridas: misma máquina, mismo plan y base restablecida antes de cada una.

## Hardware

| Componente | Detalle |
|---|---|
| Procesador | AMD Ryzen 7 5800X · 8 núcleos / 16 hilos · 3,8 GHz base |
| Memoria RAM | 32 GB DDR4 3600 MHz (4 × 8 GB, CL18) |
| Almacenamiento | Samsung SSD 990 PRO 1 TB · NVMe |
| Placa base | Gigabyte B550 AORUS ELITE AX V3 |
| Tarjeta gráfica | AMD Radeon RX 5500 XT (no interviene en las pruebas) |

## Software

| Componente | Versión / configuración |
|---|---|
| Sistema operativo | Windows 10 Education 22H2 (build 19045), 64 bits · plan de energía "Equilibrado" |
| Java | Oracle JDK 21.0.8 LTS · heap por defecto (sin `-Xmx`) |
| Spring Boot | 3.5.6 · la API se ejecuta como jar empaquetado (`java -jar`) |
| Base de datos | MySQL Community Server 8.0.46 en Docker (`mysql:8.0`), puerto 3307 |
| Configuración MySQL | Valores por defecto: `innodb_buffer_pool_size` 128 MB, `max_connections` 151, aislamiento `REPEATABLE-READ` |
| Docker | Docker Desktop 29.9.1 (backend WSL 2) · la VM ve 16 CPU y 15,6 GB de RAM |
| Pool de conexiones | HikariCP, `maximum-pool-size` 10 |
| Generador de carga | Apache JMeter 5.6.3, modo no-GUI |
| Análisis | Python 3.14.0 (`jmeter/analizar_resultados.py`) |

## Limitaciones de la medición

- **Todo corre en la misma máquina.** JMeter, la API y MySQL comparten CPU, memoria y
  disco, así que parte del tiempo medido es competencia entre el generador de carga y el
  sistema bajo prueba.
- **MySQL corre dentro de una VM (WSL 2).** El acceso a la red y al disco pasa por una
  capa de virtualización que no existiría en un servidor dedicado.
- **El servicio local `MySQL80` se detuvo durante las mediciones** para que no compitiera
  por recursos con el contenedor.
- **El plan de energía es "Equilibrado"**, no "Alto rendimiento": la frecuencia de la CPU
  puede variar entre corridas.
