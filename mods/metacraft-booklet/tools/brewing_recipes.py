#!/usr/bin/env python3
"""Writes the recipe pages of the guidebook's Brewing chapter from the drinks themselves.

Brewery's own drinks come from its jar (data/brewery/brewery_drinks and its en_us names); kultur's
from mods/metacraft-kultur. The rest of the chapter is written by hand. Run from the repo root after
Brewery or a drink changes:

    python3 mods/metacraft-booklet/tools/brewing_recipes.py

BrewingTests.everyDrinkHasARecipe fails when a loaded drink is missing from these pages.
"""
import glob
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACKS = os.path.join(ROOT, "src/main/resources/resourcepacks")
KULTUR = os.path.join(ROOT, "../metacraft-kultur/src/main/resources/data/kultur/brewery_drinks")


def brewery_jar():
    props = open(os.path.join(ROOT, "../../gradle.properties")).read()
    version = re.search(r"brewery_version=(\S+)", props).group(1)
    jars = glob.glob(os.path.expanduser(f"~/.gradle/caches/modules-2/files-2.1/maven.modrinth/brewery/{version}/*/brewery-{version}.jar"))
    if not jars:
        sys.exit(f"no Brewery {version} jar in the Gradle cache: build metacraft-kultur once first")
    return jars[0]


def duration(seconds):
    """Real time, the way a player waits for it: 70 min, 1 h 10 min, 6 h."""
    minutes = round(seconds / 60)
    if minutes < 60:
        return f"{minutes} min"
    hours, rest = divmod(minutes, 60)
    return f"{hours} h" + (f" {rest} min" if rest else "")


def barrels(info, names):
    kinds = info.get("best_barrel_type", ["*"])
    if kinds == ["*"] or not kinds:
        return "any barrel"
    return " or ".join(names.get(f"container.brewery.{k}_barrel", k) for k in kinds).lower()


def recipe(name, drink, names):
    info = drink.get("book_information", {})
    lines = [f"### Header: {name}"]
    for ingredient in drink["ingredients"]:
        items = ingredient["items"]
        count = ingredient.get("count", 1)
        if len(items) == 1:
            lines.append(f"- {count} × <citem '{items[0]}'>")
        else:
            lines.append(f"- {count} × any of " + ", ".join(f"<citem '{i}'>" for i in items))
    steps = [f"Cook for {duration(info['best_cooking_time'])}" if "best_cooking_time" in info else "Cook"]
    runs = drink.get("distillation_runs", 0)
    if runs:
        steps.append("distil once" if runs == 1 else f"distil {runs} times")
    age = info.get("best_barrel_age", -1)
    aged = bool(drink.get("barrel_definitions")) and age > 0
    if aged:
        steps.append(f"age in {barrels(info, names)} for {duration(age)}")
    sentence = ", then ".join(steps) + "." + ("" if aged else " No barrel.")
    if str(drink.get("alcoholic_value", "0")).strip() in ("0", "0.0"):
        sentence += " <gray>No alcohol.</gray>"
    lines.append("")   # a paragraph of its own, not the last ingredient's line
    lines.append(sentence)
    return "\n".join(lines)


def page(title, description, icon, order, image, intro, recipes):
    return (f"### Section: PageInfo\ntitle={title}\ndescription={description}\ncategory=metacraft:brewing\n"
            f"icon={icon}\norder={order}\n### EndSection: PageInfo\n### Image: metacraft:beside/brewing/{image} {intro}\n\n"
            + "\n\n".join(recipes) + "\n")


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)
    print("wrote", os.path.relpath(path, ROOT))


def main():
    jar = zipfile.ZipFile(brewery_jar())
    names = json.loads(jar.read("assets/brewery/lang/en_us.json"))

    brewery = []
    for entry in sorted(n for n in jar.namelist() if n.startswith("data/brewery/brewery_drinks/") and n.endswith(".json")):
        drink = json.loads(jar.read(entry))
        name = names.get(drink["name"].get("translate", ""), drink["name"].get("fallback", entry))
        brewery.append((name, drink))
    brewery.sort(key=lambda d: d[0])
    write(os.path.join(PACKS, "brewing/data/metacraft/booklet/pages/en_us/brewing/drinks.txt"), page(
        "Drinks", "Every drink Brewery knows: what goes in, how long, and where it ages.",
        "minecraft:potion", 5, "drinks",
        "Brewery's own drinks. Cook and age close to the times given for the best quality; far off and "
        "the drink comes out poor, or not at all.",
        [recipe(name, drink, names) for name, drink in brewery]))

    kultur = []
    for path in sorted(glob.glob(os.path.join(KULTUR, "*.json"))):
        drink = json.load(open(path))
        kultur.append((drink["name"].get("fallback", os.path.basename(path)), drink))
    write(os.path.join(PACKS, "brewing_kultur/data/metacraft/booklet/pages/en_us/brewing/chapter_drinks.txt"), page(
        "IT's drinks", "IT's drinks from PolymITer: Spiken, Släggan, Nyckeln and plain alcohol.",
        "minecraft:sweet_berries", 6, "chapter_drinks",
        "The chapters' own drinks, brewed the same way. Each one sends you fast or slow (Släggan: slow "
        "and strong, or fast and weak), harder the drunker you are, and past a point it blacks you out: "
        "you come to somewhere else with a headache, a little hurt and blind for a moment.",
        [recipe(name, drink, names) for name, drink in kultur]))


if __name__ == "__main__":
    main()
