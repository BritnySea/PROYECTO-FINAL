"""
Generador de gráficos para el documento académico.
Lee el JSON de resultados de pytest y produce imágenes PNG por iteración.

Uso:
    python generar_graficos.py
    python generar_graficos.py --json resultados/pytest_resultados.json
"""
import argparse
import json
import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np

COLORES = {
    "passed":  "#4CAF50",
    "failed":  "#F44336",
    "skipped": "#9E9E9E",
    "fondo":   "#0A0A0A",
    "panel":   "#1a1a2e",
    "texto":   "#FFFFFF",
    "dorado":  "#D4AF37",
    "borde":   "#333355",
}

OUTPUT = Path(__file__).parent / "resultados"

# Cómo clasificar cada test en su iteración
ITERACIONES = {
    "IT3 — App Móvil": lambda nid: "APP/" in nid or "test_iteracion_3" in nid,
    "IT4 — Panel Web": lambda nid: "WEB/" in nid or "test_iteracion_4" in nid,
    "IT5 — Integración": lambda nid: "INTEGRACION/" in nid or "test_iteracion_5" in nid,
}

# Nombre legible del módulo de prueba
MODULOS_NOMBRE = {
    "test_autenticacion":       "Autenticación",
    "test_reporte_perdido":     "Reporte Perdido",
    "test_reporte_encontrado":  "Reporte Encontrado",
    "test_coincidencias":       "Coincidencias",
    "test_mis_reportes":        "Mis Reportes",
    "test_autenticacion_admin": "Autenticación Admin",
    "test_gestion_usuarios":    "Gestión Usuarios",
    "test_supervision_reportes":"Supervisión Reportes",
    "test_end_to_end":          "End-to-End",
    "test_regresion":           "Regresión",
    "test_rendimiento":         "Rendimiento",
    "test_seguridad":           "Seguridad",
}


def cargar_json(path: Path) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def estado(t: dict) -> str:
    return t.get("outcome", "skipped")


def nombre_modulo(t: dict) -> str:
    nid = t.get("nodeid", "")
    archivo = nid.split("::")[0].split("/")[-1].replace(".py", "")
    return MODULOS_NOMBRE.get(archivo, archivo.replace("test_", "").replace("_", " ").title())


def nombre_corto(t: dict) -> str:
    nid = t.get("nodeid", "")
    raw = nid.split("::")[-1]
    # Quitar prefijo largo tipo "test_PF_APP_IT3_001__"
    if "__" in raw:
        raw = raw.split("__", 1)[-1]
    return raw.replace("test_", "").replace("_", " ")[:60]


def duracion_ms(t: dict) -> float:
    return t.get("call", {}).get("duration", 0) * 1000


# ─── Gráfico 0: Resumen general (torta) ──────────────────────────────────────

def g0_resumen_general(tests: list[dict], summary: dict):
    counts = {
        "passed":  summary.get("passed",  0),
        "failed":  summary.get("failed",  0),
        "skipped": summary.get("skipped", 0),
    }
    etqs = {"passed": "Aprobados", "failed": "Fallidos", "skipped": "Omitidos"}

    vals = [v for v in counts.values() if v > 0]
    lbls = [f"{etqs[k]}\n({v})" for k, v in counts.items() if v > 0]
    cols = [COLORES[k] for k, v in counts.items() if v > 0]

    fig, ax = plt.subplots(figsize=(7, 6))
    fig.patch.set_facecolor(COLORES["fondo"])
    ax.set_facecolor(COLORES["fondo"])

    wedges, texts, autotexts = ax.pie(
        vals, labels=lbls, colors=cols, autopct="%1.1f%%",
        startangle=140, pctdistance=0.78,
        wedgeprops={"edgecolor": COLORES["fondo"], "linewidth": 2},
    )
    for t in texts:
        t.set_color(COLORES["texto"]); t.set_fontsize(11)
    for at in autotexts:
        at.set_color(COLORES["fondo"]); at.set_fontweight("bold"); at.set_fontsize(10)

    total = sum(vals)
    pct_ok = counts["passed"] / total * 100 if total else 0
    ax.set_title(
        f"Resumen General — Sistema WOOF\n{total} casos  |  {pct_ok:.1f}% aprobados",
        color=COLORES["dorado"], fontsize=13, fontweight="bold",
    )
    plt.tight_layout()
    out = OUTPUT / "00_resumen_general.png"
    plt.savefig(out, dpi=150, bbox_inches="tight", facecolor=COLORES["fondo"])
    plt.close()
    print(f"  Guardado: {out.name}")


# ─── Gráfico por iteración: barras de resultados individuales ────────────────

def g_iteracion(nombre_iter: str, tests: list[dict], idx: int):
    if not tests:
        return

    # Agrupar por módulo para insertar separadores visuales
    modulos: dict[str, list] = {}
    for t in tests:
        m = nombre_modulo(t)
        modulos.setdefault(m, []).append(t)

    filas = []   # (nombre_test, estado, duracion_ms, modulo)
    for mod, grupo in modulos.items():
        for t in grupo:
            filas.append((nombre_corto(t), estado(t), duracion_ms(t), mod))

    nombres   = [f[0] for f in filas]
    estados   = [f[1] for f in filas]
    duraciones = [f[2] for f in filas]
    cols      = [COLORES[e] for e in estados]

    n = len(nombres)
    fig_h = max(4, n * 0.42 + 2.5)
    fig, axes = plt.subplots(1, 2, figsize=(16, fig_h),
                             gridspec_kw={"width_ratios": [3, 1]})
    fig.patch.set_facecolor(COLORES["fondo"])

    # Panel izquierdo: estado de cada test
    ax_est = axes[0]
    ax_est.set_facecolor(COLORES["panel"])
    y = np.arange(n)

    bars = ax_est.barh(y, [1] * n, color=cols, alpha=0.85, height=0.65,
                       left=0)
    for i, (bar, nom, est) in enumerate(zip(bars, nombres, estados)):
        etq = {"passed": "PASS", "failed": "FAIL", "skipped": "SKIP"}[est]
        ax_est.text(0.02, bar.get_y() + bar.get_height() / 2,
                    f"  [{etq}]  {nom}",
                    va="center", color=COLORES["texto"], fontsize=7.5,
                    fontweight="bold" if est == "failed" else "normal")

    ax_est.set_xlim(0, 1)
    ax_est.set_yticks([])
    ax_est.set_xticks([])
    ax_est.invert_yaxis()
    for sp in ax_est.spines.values():
        sp.set_edgecolor(COLORES["borde"])
    ax_est.set_title(f"Casos de Prueba — {nombre_iter}",
                     color=COLORES["dorado"], fontsize=12, fontweight="bold", pad=10)

    # Panel derecho: duración en ms (solo tests que tuvieron duración)
    ax_dur = axes[1]
    ax_dur.set_facecolor(COLORES["panel"])
    dur_cols = [COLORES[e] for e in estados]
    ax_dur.barh(y, duraciones, color=dur_cols, alpha=0.75, height=0.65)
    for i, (d, est) in enumerate(zip(duraciones, estados)):
        if d > 0:
            ax_dur.text(d + max(duraciones) * 0.01, i,
                        f"{d:.0f}ms", va="center",
                        color=COLORES["texto"], fontsize=7)
    ax_dur.set_yticks([])
    ax_dur.invert_yaxis()
    ax_dur.set_xlabel("Duración (ms)", color=COLORES["texto"], fontsize=8)
    ax_dur.tick_params(colors=COLORES["texto"], labelsize=7)
    ax_dur.xaxis.set_tick_params(labelcolor=COLORES["texto"])
    for sp in ax_dur.spines.values():
        sp.set_edgecolor(COLORES["borde"])
    ax_dur.set_title("Duración", color=COLORES["dorado"], fontsize=11,
                     fontweight="bold", pad=10)

    # Leyenda
    parches = [
        mpatches.Patch(color=COLORES["passed"], label="PASS — Aprobado"),
        mpatches.Patch(color=COLORES["failed"], label="FAIL — Fallido"),
        mpatches.Patch(color=COLORES["skipped"], label="SKIP — Omitido"),
    ]
    fig.legend(handles=parches, loc="lower center", ncol=3,
               facecolor=COLORES["panel"], labelcolor=COLORES["texto"],
               framealpha=0.8, fontsize=9,
               bbox_to_anchor=(0.5, 0.0))

    # Resumen numérico en el título general
    n_pass = sum(1 for e in estados if e == "passed")
    n_fail = sum(1 for e in estados if e == "failed")
    n_skip = sum(1 for e in estados if e == "skipped")
    fig.suptitle(
        f"{nombre_iter}   —   {n} tests:  {n_pass} PASS  |  {n_fail} FAIL  |  {n_skip} SKIP",
        color=COLORES["texto"], fontsize=11, y=1.01,
    )

    plt.tight_layout(rect=[0, 0.05, 1, 1])
    fname = f"{idx:02d}_{nombre_iter.split('—')[0].strip().replace(' ', '_').lower()}.png"
    out = OUTPUT / fname
    plt.savefig(out, dpi=150, bbox_inches="tight", facecolor=COLORES["fondo"])
    plt.close()
    print(f"  Guardado: {out.name}")


# ─── Gráfico de módulos por iteración (barras agrupadas) ─────────────────────

def g_modulos_iteracion(nombre_iter: str, tests: list[dict], idx: int):
    modulos: dict[str, list] = {}
    for t in tests:
        m = nombre_modulo(t)
        modulos.setdefault(m, []).append(t)

    if not modulos:
        return

    mods = list(modulos.keys())
    n_pass  = [sum(1 for t in modulos[m] if estado(t) == "passed")  for m in mods]
    n_fail  = [sum(1 for t in modulos[m] if estado(t) == "failed")  for m in mods]
    n_skip  = [sum(1 for t in modulos[m] if estado(t) == "skipped") for m in mods]

    x = np.arange(len(mods))
    w = 0.25
    fig, ax = plt.subplots(figsize=(max(7, len(mods) * 2.2), 5))
    fig.patch.set_facecolor(COLORES["fondo"])
    ax.set_facecolor(COLORES["panel"])

    b1 = ax.bar(x - w, n_pass, w, label="PASS", color=COLORES["passed"], alpha=0.9)
    b2 = ax.bar(x,     n_fail, w, label="FAIL", color=COLORES["failed"], alpha=0.9)
    b3 = ax.bar(x + w, n_skip, w, label="SKIP", color=COLORES["skipped"], alpha=0.7)

    for bars in (b1, b2, b3):
        for bar in bars:
            h = bar.get_height()
            if h:
                ax.text(bar.get_x() + w / 2, h + 0.05, str(int(h)),
                        ha="center", va="bottom", color=COLORES["texto"], fontsize=9)

    ax.set_xticks(x)
    ax.set_xticklabels(mods, color=COLORES["texto"], fontsize=9, rotation=15, ha="right")
    ax.set_ylabel("Casos de prueba", color=COLORES["texto"])
    ax.tick_params(colors=COLORES["texto"])
    ax.yaxis.set_tick_params(labelcolor=COLORES["texto"])
    for sp in ax.spines.values():
        sp.set_edgecolor(COLORES["borde"])
    ax.set_title(f"Resultados por Módulo — {nombre_iter}",
                 color=COLORES["dorado"], fontsize=12, fontweight="bold", pad=10)
    ax.legend(facecolor=COLORES["panel"], labelcolor=COLORES["texto"], framealpha=0.8)

    plt.tight_layout()
    fname = f"{idx:02d}_{nombre_iter.split('—')[0].strip().replace(' ', '_').lower()}_modulos.png"
    out = OUTPUT / fname
    plt.savefig(out, dpi=150, bbox_inches="tight", facecolor=COLORES["fondo"])
    plt.close()
    print(f"  Guardado: {out.name}")


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", default=str(OUTPUT / "pytest_resultados.json"))
    args = parser.parse_args()

    json_path = Path(args.json)
    if not json_path.exists():
        print(f"No se encontró {json_path}")
        print("Primero ejecuta: python -m pytest APP/ WEB/ INTEGRACION/ -v")
        sys.exit(1)

    OUTPUT.mkdir(parents=True, exist_ok=True)
    data    = cargar_json(json_path)
    tests   = data.get("tests", [])
    summary = data.get("summary", {})

    if not tests:
        print("El JSON no contiene tests — ejecuta pytest primero.")
        sys.exit(1)

    total    = len(tests)
    passed   = summary.get("passed", 0)
    failed   = summary.get("failed", 0)
    skipped  = summary.get("skipped", 0)
    duracion = data.get("duration", 0)

    print(f"\n{'='*55}")
    print(f"  WOOF — Generando gráficos para el documento académico")
    print(f"{'='*55}")
    print(f"  Total: {total}  |  PASS {passed}  |  FAIL {failed}  |  SKIP {skipped}")
    print(f"  Duración total de la suite: {duracion:.1f} s\n")

    # 00 — Resumen general
    g0_resumen_general(tests, summary)

    # Un gráfico por iteración (detalle de cada test + duración)
    for i, (nombre_iter, filtro) in enumerate(ITERACIONES.items(), start=1):
        grupo = [t for t in tests if filtro(t.get("nodeid", ""))]
        g_iteracion(nombre_iter, grupo, i)
        g_modulos_iteracion(nombre_iter, grupo, i + 10)

    print(f"\n{'='*55}")
    print(f"  Gráficos guardados en: {OUTPUT.resolve()}")
    print(f"{'='*55}\n")


if __name__ == "__main__":
    main()
