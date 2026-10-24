#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Ciclo completo de las corridas del escenario 3 (lanzamiento), sin pasos a mano.
# Por cada corrida: restablece la BD -> arranca la API del commit indicado ->
# corre ElectroShop_Lanzamiento.jmx -> analiza -> verifica stock -> apaga la API.
#
# Configuraciones (cada táctica sobre el código y la BD que le corresponden):
#   base  código de f12c4b0 (igual a la versión 1), sin db/03_indices.sql
#   t1    código de f12c4b0 + db/03_indices.sql  (T1 solo toca la BD)
#   t2    código de HEAD    + db/03_indices.sql  (T2 se aplica sobre T1)
#
# Uso:
#   ./corridas_escenario3.sh                          # las corridas de la lista de abajo
#   ./corridas_escenario3.sh e3_prueba t2 5           # una sola: <nombre> <config> <skus>
#
# Requisitos: MySQL en el contenedor Docker $MYSQL_CONTAINER, Java 21, Maven,
# JMeter y Python. Variables opcionales:
#   MYSQL_CONTAINER (electroshop-mysql)  MYSQL_ROOT_PASSWORD (root)  DB_PORT (3307)
#   MVN (mvn)  JMETER_HOME  PYTHON (python3 o python)
# ---------------------------------------------------------------------------
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$DIR/.." && pwd)"
JARS="$REPO/target/jars"
LOG="$JARS/corridas_escenario3.log"
MYSQL_CONTAINER=${MYSQL_CONTAINER:-electroshop-mysql}
MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD:-root}
export DB_PORT=${DB_PORT:-3307}
MVN=${MVN:-mvn}
JMETER="${JMETER_HOME:+$JMETER_HOME/bin/}jmeter"
command -v "$JMETER" >/dev/null || JMETER="$JMETER.bat"   # Git Bash en Windows
PYTHON=${PYTHON:-$(command -v python3 >/dev/null && echo python3 || echo python)}
COMMIT_V1=f12c4b0
export PYTHONIOENCODING=utf-8

log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$LOG"; }

sql() {
  docker exec -i "$MYSQL_CONTAINER" mysql --default-character-set=utf8mb4 \
    -uroot -p"$MYSQL_ROOT_PASSWORD" "$@" 2>/dev/null
}

# Jar del código de la versión 1 (worktree aparte) y jar de HEAD (el repo mismo).
# El worktree se crea dentro de target/ porque una ruta larga (p. ej. %TEMP%)
# supera el límite de 260 caracteres de Windows.
construir_jars() {
  mkdir -p "$JARS"
  if [ ! -f "$JARS/v1.jar" ]; then
    local wt="$REPO/target/wt-v1"
    git -C "$REPO" worktree add -f "$wt" "$COMMIT_V1" >>"$LOG" 2>&1
    (cd "$wt" && "$MVN" -q -DskipTests package) >>"$LOG" 2>&1 || { log "fallo build v1"; exit 1; }
    cp "$wt"/target/electroshop-api-*.jar "$JARS/v1.jar"
    git -C "$REPO" worktree remove --force "$wt"
  fi
  (cd "$REPO" && "$MVN" -q -DskipTests package) >>"$LOG" 2>&1 || { log "fallo build HEAD"; exit 1; }
  cp "$REPO"/target/electroshop-api-*.jar "$JARS/head.jar"
}

apagar_api() {
  local pid
  if command -v taskkill >/dev/null; then
    pid=$(netstat -ano | awk '/:8080 .*LISTENING/ {print $NF; exit}')
    [ -n "$pid" ] && taskkill //PID "$pid" //F >/dev/null 2>&1
  else
    pid=$(lsof -ti tcp:8080 2>/dev/null)
    [ -n "$pid" ] && kill "$pid"
  fi
  sleep 3
}

corrida() {  # $1 = nombre, $2 = base|t1|t2, $3 = productos_lanzamiento
  local nombre=$1 config=$2 skus=$3 out="$DIR/resultados/$1" jar
  [ "$config" = t2 ] && jar=head || jar=v1
  log "== $nombre (config=$config skus=$skus)"

  apagar_api
  sql < "$REPO/db/01_schema.sql" || { log "fallo schema"; return 1; }
  sql electroshop < "$REPO/db/02_seed.sql" || { log "fallo seed"; return 1; }
  [ "$config" != base ] && sql electroshop < "$REPO/db/03_indices.sql"
  [ "$skus" -lt 20 ] && sql electroshop < "$DIR/lanzamiento_stock.sql"
  log "BD lista"

  java -jar "$JARS/$jar.jar" >"$JARS/api-$nombre.log" 2>&1 &
  for _ in $(seq 1 90); do
    curl -sf localhost:8080/actuator/health >/dev/null && break
    sleep 2
  done
  curl -sf localhost:8080/actuator/health >/dev/null || { log "API no levanto"; apagar_api; return 1; }

  rm -rf "$out"; mkdir -p "$out"
  (cd "$DIR" && "$JMETER" -n -t ElectroShop_Lanzamiento.jmx \
    -l "resultados/$nombre/resultados.jtl" -j "resultados/$nombre/jmeter.log" \
    -e -o "resultados/$nombre/reporte" \
    -Jjmeter.save.saveservice.output_format=csv \
    -Jjmeter.save.saveservice.print_field_names=true \
    -Jproductos_lanzamiento="$skus") >>"$LOG" 2>&1

  (cd "$DIR" && "$PYTHON" analizar_resultados.py "resultados/$nombre/resultados.jtl") > "$out/resumen.txt"
  {
    echo "Integridad del inventario tras la corrida ($(date '+%Y-%m-%d %H:%M'))"
    echo "SELECT COUNT(*) FROM producto WHERE stock < 0:"
    sql -N electroshop -e "SELECT COUNT(*) FROM producto WHERE stock < 0;"
    echo "Stock de los productos de lanzamiento (id <= $skus):"
    sql electroshop -e "SELECT id, sku, stock FROM producto WHERE id <= $skus ORDER BY id;"
  } > "$out/stock.txt"
  log "$(grep 'POST' "$out/resumen.txt" | head -1)"
  log "stock<0: $(sed -n 3p "$out/stock.txt")"
  apagar_api
}

mkdir -p "$DIR/resultados" "$JARS"
: > "$LOG"
log "Construyendo jars"
construir_jars

if [ $# -ge 3 ]; then
  corrida "$1" "$2" "$3"
else
  # Corridas que completaron el escenario 3 (3 por configuración, se reporta la mediana)
  for n in "" _2 _3; do corrida "e3_lanzamiento_base$n" base 20; done
  for n in _2 _3; do
    corrida "e3_lanzamiento_t1_5sku$n" t1 5
    corrida "e3_lanzamiento_t1_1sku$n" t1 1
    corrida "e3_lanzamiento_t2_5sku$n" t2 5
    corrida "e3_lanzamiento_t2_1sku$n" t2 1
  done
fi
log "FIN"
