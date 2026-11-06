"""Builds app/src/main/assets/foods.csv from the data_*.py lists.

Run from this folder:  python3 generate_foods.py
Add or fix a food by editing a data_*.py row and re-running. The app reloads the
catalog automatically because it reads the CSV on start.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from data_a import FRUITS, VEGETABLES, MEAT          # noqa: E402
from data_b import FISH, DAIRY, GRAINS, LEGUMES      # noqa: E402
from data_c import MEXICAN                           # noqa: E402
from data_d import FAST_FOOD, HOME, SNACKS           # noqa: E402
from data_e import DRINKS, CONDIMENTS                # noqa: E402

# serving code -> (English label, Spanish label)
SERVINGS = {
    "md": ("1 medium", "1 mediano"), "sm": ("1 small", "1 chico"), "lg": ("1 large", "1 grande"),
    "pc": ("1 piece", "1 pieza"), "hlf": ("½ fruit", "½ pieza"),
    "cup": ("1 cup", "1 taza"), "hcup": ("½ cup", "½ taza"), "qcup": ("¼ cup", "¼ taza"),
    "tbsp": ("1 tbsp", "1 cda"), "tsp": ("1 tsp", "1 cdita"),
    "sl": ("1 slice", "1 rebanada"), "oz": ("1 oz", "1 oz"), "oz2": ("2 oz", "2 oz"),
    "oz3": ("3 oz", "3 oz"), "oz4": ("4 oz", "4 oz"),
    "can": ("1 can", "1 lata"), "cand": ("1 can, drained", "1 lata, drenada"),
    "bot": ("1 bottle", "1 botella"), "gl": ("1 glass", "1 vaso"),
    "g16": ("16 fl oz", "16 oz fl"), "shot": ("1 shot", "1 caballito"),
    "srv": ("1 serving", "1 porción"), "bar": ("1 bar", "1 barra"), "pk": ("1 package", "1 paquete"),
    "scp": ("1 scoop", "1 scoop"), "fil": ("1 fillet", "1 filete"), "taco": ("1 taco", "1 taco"),
    "bowl": ("1 bowl", "1 plato"), "cont": ("1 container", "1 envase"),
    "clv": ("1 clove", "1 diente"), "stk": ("1 stalk", "1 tallo"), "spr": ("1 spray", "1 disparo"),
    "eg2": ("2 eggs", "2 huevos"),
}

CATEGORIES = [
    ("fruit", FRUITS), ("vegetable", VEGETABLES), ("meat", MEAT), ("fish", FISH),
    ("dairy", DAIRY), ("grain", GRAINS), ("legume", LEGUMES), ("mexican", MEXICAN),
    ("restaurant", FAST_FOOD), ("home", HOME), ("snack", SNACKS), ("drink", DRINKS),
    ("condiment", CONDIMENTS),
]


def main():
    rows, seen, problems = [], set(), []
    for cat, (default_emoji, block) in CATEGORIES:
        for line in block.strip().splitlines():
            parts = line.split("|")
            if len(parts) != 9:
                problems.append(f"bad field count: {line}")
                continue
            en, es, emoji, kcal, p, c, f, srv, grams = parts
            if srv not in SERVINGS:
                problems.append(f"unknown serving '{srv}': {en}")
                continue
            key = en.lower()
            if key in seen:
                problems.append(f"duplicate: {en}")
                continue
            seen.add(key)
            kcal, p, c, f, grams = float(kcal), float(p), float(c), float(f), float(grams)
            if not (0 <= kcal <= 902 and p + c + f <= 101 and grams > 0):
                problems.append(f"out of range: {en}")
            for text in (en, es):
                if ";" in text:
                    problems.append(f"semicolon in name: {text}")
            s_en, s_es = SERVINGS[srv]
            rows.append([en, es, emoji or default_emoji, cat,
                         fmt(kcal), fmt(p), fmt(c), fmt(f), s_en, s_es, fmt(grams)])
    if problems:
        print("\n".join(problems))
        sys.exit(1)

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                       "..", "..", "app", "src", "main", "assets", "foods.csv")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as fh:
        fh.write("en;es;emoji;category;kcal100;protein100;carbs100;fat100;serving_en;serving_es;serving_g\n")
        for r in rows:
            fh.write(";".join(r) + "\n")
    print(f"{len(rows)} foods -> {os.path.normpath(out)}")


def fmt(x: float) -> str:
    return str(int(x)) if x == int(x) else f"{x:g}"


if __name__ == "__main__":
    main()
