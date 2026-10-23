#!/usr/bin/env python3
"""
Analiza uno o dos archivos de resultados de JMeter (.jtl en CSV) contra los requisitos
de ElectroShop y, si se pasan dos, compara la línea base con la versión optimizada.

Uso:
    python3 analizar_resultados.py resultados/linea_base.jtl
    python3 analizar_resultados.py resultados/linea_base.jtl resultados/optimizado.jtl
    python3 analizar_resultados.py resultados/linea_base.jtl --percentil 99 --markdown

Qué responde (preguntas de la sustentación):
  * ¿La carga real fue 1.500 pedidos/min?  -> columna "por min" y el detalle minuto a minuto.
  * ¿Se cumple el límite con el estadístico declarado?  -> p95 (o p99) contra 100 ms / 50 ms.
  * ¿Cuánto mejoró cada endpoint?  -> tabla de comparación cuando se pasan dos archivos.

Solo usa la biblioteca estándar de Python 3.8+.
"""
import argparse
import csv
import math
import sys
from collections import defaultdict

# Requisitos del enunciado. La clave se busca dentro de la etiqueta del muestreador.
REQUISITOS = {
    "procesar": {"limite_ms": 100, "carga_min": 1500},
    "detalle": {"limite_ms": 50, "carga_min": None},   # el enunciado no fija la carga de consultas
}


def percentil(valores_ordenados, p):
    """Percentil por rango más cercano (el mismo criterio que usa JMeter)."""
    if not valores_ordenados:
        return float("nan")
    rango = max(1, math.ceil(p / 100 * len(valores_ordenados)))
    return valores_ordenados[rango - 1]


def requisito_de(etiqueta):
    for clave, req in REQUISITOS.items():
        if clave in etiqueta:
            return req
    return None


def cargar(ruta, descartar_s):
    with open(ruta, newline="", encoding="utf-8") as f:
        lector = csv.reader(f)
        primera = next(lector, None)
        if primera is None:
            sys.exit(f"{ruta}: archivo vacío")
        if primera[0] == "timeStamp":
            columnas, datos = primera, list(lector)
        else:
            # .jtl sin encabezados: orden por defecto de JMeter
            columnas = ["timeStamp", "elapsed", "label", "responseCode", "responseMessage",
                        "threadName", "dataType", "success"]
            datos = [primera] + list(lector)
        filas = [dict(zip(columnas, d)) for d in datos if len(d) >= len(columnas[:8])]
        filas = [r for r in filas if not r["label"].startswith("[")]
    if not filas:
        sys.exit(f"{ruta}: no hay muestras de carga (¿el archivo es un .jtl en CSV con encabezados?)")

    inicio = min(int(r["timeStamp"]) for r in filas)
    fin = max(int(r["timeStamp"]) for r in filas)
    corte = inicio + descartar_s * 1000
    ventana = [r for r in filas if int(r["timeStamp"]) >= corte]
    duracion_s = (fin - corte) / 1000
    if duracion_s <= 0 or not ventana:
        sys.exit(f"{ruta}: la prueba dura {(fin - inicio) / 1000:.0f} s; no alcanza para descartar {descartar_s} s")
    return ventana, duracion_s, corte


def resumir(ventana, duracion_s, corte, p_objetivo, max_errores):
    por_etiqueta = defaultdict(list)
    for r in ventana:
        por_etiqueta[r["label"]].append(r)

    resumen = {}
    for etiqueta, filas in sorted(por_etiqueta.items()):
        tiempos = sorted(int(r["elapsed"]) for r in filas)
        errores = sum(1 for r in filas if r["success"].lower() != "true")
        por_minuto = defaultdict(int)
        for r in filas:
            por_minuto[(int(r["timeStamp"]) - corte) // 60000] += 1
        minutos_completos = [n for m, n in sorted(por_minuto.items()) if (m + 1) * 60 <= duracion_s]

        req = requisito_de(etiqueta)
        datos = {
            "muestras": len(filas),
            "errores_pct": 100 * errores / len(filas),
            "por_min": len(filas) / duracion_s * 60,
            "min_por_min": min(minutos_completos) if minutos_completos else None,
            "max_por_min": max(minutos_completos) if minutos_completos else None,
            "promedio": sum(tiempos) / len(tiempos),
            "p50": percentil(tiempos, 50),
            "p90": percentil(tiempos, 90),
            "p95": percentil(tiempos, 95),
            "p99": percentil(tiempos, 99),
            "max": tiempos[-1],
            "objetivo": percentil(tiempos, p_objetivo),
            "req": req,
        }
        if req:
            motivos = []
            if datos["objetivo"] > req["limite_ms"]:
                motivos.append(f"p{p_objetivo} {datos['objetivo']} ms > {req['limite_ms']} ms")
            if req["carga_min"] and datos["por_min"] < req["carga_min"] * 0.98:
                motivos.append(f"carga real {datos['por_min']:.0f}/min < {req['carga_min']}/min")
            if datos["errores_pct"] > max_errores:
                motivos.append(f"errores {datos['errores_pct']:.2f} % > {max_errores} %")
            datos["veredicto"] = "CUMPLE" if not motivos else "NO CUMPLE: " + "; ".join(motivos)
        else:
            datos["veredicto"] = "(sin requisito)"
        resumen[etiqueta] = datos
    return resumen


def imprimir(titulo, resumen, duracion_s, p_objetivo, markdown):
    print(f"\n## {titulo}  (ventana analizada: {duracion_s:.0f} s)\n")
    cols = ["Endpoint", "Muestras", "Por min", "Errores %", "Promedio", "p50", "p90", "p95", "p99", "Máx", "Veredicto"]
    filas = []
    for etiqueta, d in resumen.items():
        filas.append([
            etiqueta, str(d["muestras"]), f"{d['por_min']:.0f}", f"{d['errores_pct']:.2f}",
            f"{d['promedio']:.1f}", str(d["p50"]), str(d["p90"]), str(d["p95"]), str(d["p99"]),
            str(d["max"]), d["veredicto"],
        ])
    tabla(cols, filas, markdown)
    print("\nTiempos en ms. Percentil por rango más cercano. "
          f"Estadístico de decisión: p{p_objetivo}.")
    for etiqueta, d in resumen.items():
        if d["min_por_min"] is not None:
            print(f"  {etiqueta}: entre {d['min_por_min']} y {d['max_por_min']} peticiones por minuto completo")


def comparar(base, opt, markdown):
    print("\n## Comparación línea base → optimizado\n")
    cols = ["Endpoint", "Métrica", "Línea base", "Optimizado", "Cambio"]
    filas = []
    for etiqueta in base:
        if etiqueta not in opt:
            continue
        for metrica, unidad in (("p95", "ms"), ("p99", "ms"), ("promedio", "ms"),
                                ("por_min", "/min"), ("errores_pct", "%")):
            a, b = base[etiqueta][metrica], opt[etiqueta][metrica]
            cambio = f"{(b - a) / a * 100:+.1f} %" if a else "—"
            filas.append([etiqueta, metrica, f"{a:.1f} {unidad}", f"{b:.1f} {unidad}", cambio])
    tabla(cols, filas, markdown)


def tabla(cols, filas, markdown):
    if markdown:
        print("| " + " | ".join(cols) + " |")
        print("|" + "|".join("---" for _ in cols) + "|")
        for f in filas:
            print("| " + " | ".join(f) + " |")
        return
    anchos = [max(len(c), *(len(f[i]) for f in filas)) for i, c in enumerate(cols)]
    print("  ".join(c.ljust(a) for c, a in zip(cols, anchos)))
    print("  ".join("-" * a for a in anchos))
    for f in filas:
        print("  ".join(v.ljust(a) for v, a in zip(f, anchos)))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("jtl", nargs="+", help="uno (análisis) o dos (comparación) archivos .jtl")
    ap.add_argument("--descartar", type=int, default=60,
                    help="segundos iniciales que se descartan (subida de hilos y calentamiento de la JVM). Por defecto 60")
    ap.add_argument("--percentil", type=int, default=95, choices=[90, 95, 99],
                    help="estadístico con el que se decide si se cumple el requisito. Por defecto 95")
    ap.add_argument("--max-errores", type=float, default=1.0,
                    help="porcentaje máximo de errores aceptado. Por defecto 1.0")
    ap.add_argument("--markdown", action="store_true", help="imprime las tablas en Markdown para el informe")
    args = ap.parse_args()
    if len(args.jtl) > 2:
        ap.error("pase uno o dos archivos")

    resumenes = []
    for i, ruta in enumerate(args.jtl):
        ventana, duracion_s, corte = cargar(ruta, args.descartar)
        resumen = resumir(ventana, duracion_s, corte, args.percentil, args.max_errores)
        titulo = ("Línea base" if i == 0 else "Optimizado") if len(args.jtl) == 2 else ruta
        imprimir(titulo, resumen, duracion_s, args.percentil, args.markdown)
        resumenes.append(resumen)

    if len(resumenes) == 2:
        comparar(*resumenes, args.markdown)


if __name__ == "__main__":
    main()
