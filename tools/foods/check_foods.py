"""Sanity checks for assets/foods.csv and assets/recipes.json. Run after editing data:
    python3 tools/foods/check_foods.py
Prints every row that breaks a rule; exit code 1 if anything is flagged (except known exceptions).
"""
import csv, json, os, sys, unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "app", "src", "main", "assets")

# Rows where calories legitimately don't match 4/4/9 (fiber-heavy foods, spices, alcohol, vinegar).
ATWATER_OK = {"Broccoli, raw", "Kale, raw", "Collard greens, cooked", "Parsley", "Brussels sprouts, cooked",
              "Balsamic vinegar", "Cocoa powder, unsweetened", "Black pepper", "Cinnamon", "Michelada", "Pulque",
              "Pumpkin seeds (pepitas)", "Nut bar (KIND type)", "White vinegar", "Chia seeds", "Flaxseed, ground",
              "Artichoke, cooked", "Bran flakes", "Raisin bran"}
SERVING_G = {  # label -> (min g, max g)
    "1 oz": (28, 29), "2 oz": (56, 57), "3 oz": (85, 86), "4 oz": (113, 114), "16 fl oz": (450, 500),
    "1 tbsp": (1, 25), "1 tsp": (1, 7), "1 shot": (28, 45), "1 cup": (8, 260), "½ cup": (40, 135),
    "¼ cup": (7, 70), "1 can": (90, 700), "1 can, drained": (100, 150), "1 slice": (8, 170),
    "1 bottle": (60, 600), "1 glass": (140, 400), "1 container": (110, 260), "1 scoop": (25, 40),
}
PROTEIN_DENSE = {"Beef jerky", "Whey protein powder", "Plant protein powder", "Powdered peanut butter",
                 "Chicharrón (pork rinds)", "Parmesan, grated", "Bacon, cooked", "Cecina", "Tuna, canned in oil",
                 "Protein bar", "Octopus, cooked", "Hemp seeds", "Turkey bacon", "Pumpkin seeds (pepitas)",
                 "Chicken breast, grilled", "Tuna steak, cooked", "Sirloin steak, grilled", "Filet mignon, cooked",
                 "Turkey breast, roasted", "Flank steak, cooked", "Pork rinds"}


def main():
    path = os.path.join(ASSETS, "foods.csv")
    rows = list(csv.DictReader(open(path, encoding="utf-8"), delimiter=";"))
    flags = []
    seen_es = {}
    for r in rows:
        n = r["en"]
        k, p, c, f, g = (float(r[x]) for x in ("kcal100", "protein100", "carbs100", "fat100", "serving_g"))
        est = 4 * p + 4 * c + 9 * f
        if k > 30 and abs(est - k) / k > 0.20 and n not in ATWATER_OK and r["category"] != "drink":
            flags.append(f"ATWATER  {n}: listed {k:.0f}, macros give {est:.0f}")
        if r["category"] == "drink" and est > k * 1.25 and k > 5:
            flags.append(f"ATWATER  {n}: drink macros exceed kcal ({est:.0f} > {k:.0f})")
        if p + c + f > 100.5:
            flags.append(f"MASS     {n}: macros sum {p + c + f:.1f} g per 100 g")
        if p > 35 and n not in PROTEIN_DENSE:
            flags.append(f"PROTEIN  {n}: {p} g protein per 100 g")
        lo_hi = SERVING_G.get(r["serving_en"])
        if lo_hi and not (lo_hi[0] <= g <= lo_hi[1]):
            flags.append(f"SERVING  {n}: '{r['serving_en']}' = {g} g (expected {lo_hi[0]}–{lo_hi[1]})")
        if k * g / 100 > 1300:
            flags.append(f"BIG      {n}: one serving = {k * g / 100:.0f} kcal")
        if not r["es"].strip() or not r["emoji"].strip():
            flags.append(f"MISSING  {n}: Spanish name or emoji empty")
        key = r["es"].lower()
        if key in seen_es:
            flags.append(f"DUP-ES   {n} and {seen_es[key]} share the Spanish name '{r['es']}'")
        seen_es[key] = n
        if r["category"] == "fruit" and k > 120 and not any(w in n.lower() for w in
                ("dried", "raisin", "prune", "date", "coconut", "avocado", "banana chips", "plantain", "mamey", "cranberries", "maraschino")):
            flags.append(f"RANGE    {n}: {k} kcal/100 g is high for fresh fruit")
        if r["category"] == "vegetable" and k > 120 and not any(w in n.lower() for w in
                ("fries", "hash", "tater", "sun-dried", "garlic", "olive", "coleslaw", "wedges", "patty", "mashed")):
            flags.append(f"RANGE    {n}: {k} kcal/100 g is high for a vegetable")

    # Recipes: every per-serving number must equal the sum of its ingredients ÷ servings.
    rec = json.load(open(os.path.join(ASSETS, "recipes.json"), encoding="utf-8"))["recipes"]
    foods = {r["en"]: r for r in rows}
    for r in rec:
        tot = sum(i["kcal"] for i in r["ingredients"]) / r["servings"]
        if abs(tot - r["kcal"]) > 1.5:
            flags.append(f"RECIPE   {r['en']}: kcal {r['kcal']} vs ingredients {tot:.0f}")
        for i in r["ingredients"]:
            if i["en"] not in foods:
                flags.append(f"RECIPE   {r['en']}: ingredient '{i['en']}' not in food list")
            else:
                exp = float(foods[i["en"]]["kcal100"]) * i["g"] / 100
                if abs(exp - i["kcal"]) > 1:
                    flags.append(f"RECIPE   {r['en']}: {i['en']} kcal {i['kcal']} vs food list {exp:.1f}")
            if not i["keys"]:
                flags.append(f"RECIPE   {r['en']}: {i['en']} has no fridge keys")
        if r["kcal"] > 1100 or r["kcal"] < 100:
            flags.append(f"RECIPE   {r['en']}: {r['kcal']} kcal per serving looks off")

    print(f"{len(rows)} foods, {len(rec)} recipes checked; {len(flags)} flags")
    for x in flags:
        print("  " + x)
    sys.exit(1 if flags else 0)


if __name__ == "__main__":
    main()
