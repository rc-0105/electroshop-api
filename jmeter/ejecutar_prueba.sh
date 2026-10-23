#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Ejecuta el plan de rendimiento en modo no-GUI (el modo correcto para medir),
# genera el reporte HTML de JMeter y el resumen contra los requisitos.
#
# Uso:
#   ./ejecutar_prueba.sh <nombre_corrida> [opciones -J de JMeter]
#
# Ejemplos:
#   ./ejecutar_prueba.sh linea_base
#   ./ejecutar_prueba.sh t1_indice_sku
#   ./ejecutar_prueba.sh linea_base_10min -Jduracion=600
#
# Si jmeter no está en el PATH:  JMETER_HOME=/ruta/apache-jmeter-5.6.3 ./ejecutar_prueba.sh ...
# ---------------------------------------------------------------------------
set -euo pipefail

NOMBRE=${1:?"Indique un nombre para la corrida, p. ej.: ./ejecutar_prueba.sh linea_base"}
shift

DIR="$(cd "$(dirname "$0")" && pwd)"
SALIDA="$DIR/resultados/$NOMBRE"
JMETER="${JMETER_HOME:+$JMETER_HOME/bin/}jmeter"

rm -rf "$SALIDA"
mkdir -p "$SALIDA"

echo ">> Corrida '$NOMBRE' — resultados en $SALIDA"
"$JMETER" -n \
  -t "$DIR/ElectroShop_Rendimiento.jmx" \
  -l "$SALIDA/resultados.jtl" \
  -j "$SALIDA/jmeter.log" \
  -e -o "$SALIDA/reporte" \
  -Jjmeter.save.saveservice.output_format=csv \
  -Jjmeter.save.saveservice.print_field_names=true \
  "$@"

python3 "$DIR/analizar_resultados.py" "$SALIDA/resultados.jtl" | tee "$SALIDA/resumen.txt"
echo
echo ">> Reporte HTML: $SALIDA/reporte/index.html"
