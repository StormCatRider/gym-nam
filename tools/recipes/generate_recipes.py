"""Builds app/src/main/assets/recipes.json from recipes_data.py + assets/foods.csv.

Run:  python3 tools/recipes/generate_recipes.py   (run generate_foods.py first if you changed foods)
"""
import csv
import json
import os
import sys
import unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from recipes_data import RECIPES, STAPLES, KEYS, EXCLUDE  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "app", "src", "main", "assets")


def norm(s: str) -> str:
    s = unicodedata.normalize("NFD", s.lower())
    s = "".join(ch for ch in s if unicodedata.category(ch) != "Mn")
    return " ".join("".join(ch if ch.isalnum() else " " for ch in s).split())


def main():
    with open(os.path.join(ASSETS, "foods.csv"), encoding="utf-8") as fh:
        foods = {r["en"]: r for r in csv.DictReader(fh, delimiter=";")}

    out, problems = [], []
    for i, r in enumerate(RECIPES, 1):
        ings, tot = [], [0.0, 0.0, 0.0, 0.0]
        for name, grams, pcs in r["ing"]:
            f = foods.get(name)
            if f is None:
                problems.append(f"{r['en']}: unknown food '{name}'")
                continue
            k = grams / 100.0
            vals = [float(f["kcal100"]) * k, float(f["protein100"]) * k,
                    float(f["carbs100"]) * k, float(f["fat100"]) * k]
            tot = [a + b for a, b in zip(tot, vals)]
            keys = KEYS.get(name)
            if not keys:
                problems.append(f"{r['en']}: no fridge KEYS for '{name}' (add them to recipes_data.KEYS)")
                continue
            ings.append({
                "en": f["en"], "es": f["es"], "emoji": f["emoji"], "g": grams, "pcs": pcs,
                "kcal": round(vals[0], 1), "p": round(vals[1], 1), "c": round(vals[2], 1), "f": round(vals[3], 1),
                "keys": sorted({norm(k) for k in keys}), "not": sorted({norm(k) for k in EXCLUDE.get(name, [])}),
                "staple": name in STAPLES,
            })
        n = r["servings"]
        out.append({
            "id": f"b{i}", "en": r["en"], "es": r["es"], "emoji": r["emoji"],
            "servings": n, "minutes": r["minutes"],
            "kcal": round(tot[0] / n), "p": round(tot[1] / n, 1), "c": round(tot[2] / n, 1), "f": round(tot[3] / n, 1),
            "steps_en": r["steps_en"].split("|"), "steps_es": r["steps_es"].split("|"),
            "ingredients": ings,
        })
        if len(out[-1]["steps_en"]) != len(out[-1]["steps_es"]):
            problems.append(f"{r['en']}: EN/ES step count differs")

    if problems:
        print("\n".join(problems))
        sys.exit(1)
    path = os.path.join(ASSETS, "recipes.json")
    with open(path, "w", encoding="utf-8") as fh:
        json.dump({"recipes": out}, fh, ensure_ascii=False, separators=(",", ":"))
    for r in out:
        print(f"{r['kcal']:>4} kcal  {r['p']:>5} g P  {r['en']}")
    print(f"{len(out)} recipes -> {os.path.normpath(path)}")


if __name__ == "__main__":
    main()
