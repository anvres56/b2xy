#!/usr/bin/env python3
"""
Проверка целей миксинов готового джара против маппингов Yarn.

В джаре имена уже intermediary (class_/method_/field_), поэтому сверять надо
именно с ними: берём из маппингов карту intermediary -> named и ищем
цели из аннотаций миксина.

Читаем constant pool, а не исходник: в байткоде @Mixin хранится как
value=[class Lnet/minecraft/class_636;], а method= как строка "method_2896".

Запуск: python3 tools/check-mixins.py [version] [jar]
"""
import re
import subprocess
import sys
import zipfile
from pathlib import Path

TINY = {
    "1.21.11": "/home/anvres/.gradle/caches/fabric-loom/1.21.11/net.fabricmc.yarn.1_21_11.1.21.11+build.3-v2/mappings-base.tiny",
    "1.21.10": "/home/anvres/.gradle/caches/fabric-loom/1.21.10/net.fabricmc.yarn.1_21_10.1.21.10+build.4/mappings-base.tiny",
    "1.21.8": "/home/anvres/.gradle/caches/fabric-loom/1.21.8/net.fabricmc.yarn.1_21_8.1.21.8+build.4/mappings-base.tiny",
    "1.21.5": "/home/anvres/.gradle/caches/fabric-loom/1.21.5/net.fabricmc.yarn.1_21_5.1.21.5+build.6/mappings-base.tiny",
    "1.21.4": "/home/anvres/.gradle/caches/fabric-loom/1.21.4/net.fabricmc.yarn.1_21_4.1.21.4+build.8/mappings-base.tiny",
}


def load_mappings(path):
    """Возвращает set intermediary-имён (классы, методы, поля)."""
    # Строки tiny-формата: "c\t<interm>\t<named>" либо "\tm\t<desc>\t<interm>\t<named>"
    # Ведущий таб у method/field-строк обязателен, поэтому пустые поля слева
    # отбрасываем, а не ищем индекс по фиксированной позиции.
    names = set()
    for line in Path(path).read_text(encoding="utf-8", errors="ignore").splitlines():
        parts = line.split("\t")
        i = 0
        while i < len(parts) and parts[i] == "":
            i += 1
        if i >= len(parts):
            continue
        kind, rest = parts[i], parts[i + 1:]
        if kind == "c" and rest:
            names.add(rest[0])
        elif kind in ("m", "f") and len(rest) >= 2:
            names.add(rest[1])
    return names


def main():
    version = sys.argv[1] if len(sys.argv) > 1 else "1.21.11"
    jar = Path(sys.argv[2] if len(sys.argv) > 2 else "build/libs/B2XY-0.1.2.jar")
    tiny = TINY.get(version)
    if not tiny or not Path(tiny).exists():
        print(f"нет маппингов для {version}: {tiny}")
        return 2
    if not jar.exists():
        print(f"нет джара {jar}")
        return 2

    known = load_mappings(tiny)
    tmp = Path("/tmp/opencode/_mixin_check.class")
    tmp.parent.mkdir(parents=True, exist_ok=True)

    bad = []
    checked = 0
    per_mixin = {}

    with zipfile.ZipFile(jar) as z:
        for name in sorted(z.namelist()):
            if not name.startswith("com/b2xy/mixin/") or not name.endswith(".class"):
                continue
            tmp.write_bytes(z.read(name))
            text = subprocess.run(["javap", "-p", "-v", str(tmp)],
                                  capture_output=True, text=True).stdout

            # Класс-владелец миксина: @Mixin(value=[class L...;])
            mixin = re.search(r"org\.spongepowered\.asm\.mixin\.Mixin\([^)]*?class\s+(L?[\w/$;]+)", text, re.S)
            if not mixin:
                bad.append((name, "не нашли @Mixin target"))
                continue
            owner = mixin.group(1).lstrip("L").rstrip(";")
            checked += 1
            # Владелец бывает и в маппингах Minecraft, и в джар Meteor/Baritone
            if owner not in known and owner.startswith("net/minecraft"):
                bad.append((name, f"владелец {owner} не найден в маппингах"))

            # Цели инъекций: строки method_XXXX / field_XXXX рядом с аннотациями Inject
            targets = re.findall(r"((?:method|field)_\d+)", text)
            # Цели @At: строки вида "Lnet/minecraft/class_3965;getBlockPos()V"
            at_calls = re.findall(r'(?:target|value)=\[?[c]?\s*"?L([\w/$]+);', text)

            uniq = sorted(set(targets))
            per_mixin[name] = (owner, uniq)

            for t in uniq:
                checked += 1
                if t not in known:
                    bad.append((name, f"{t} не найден в маппингах"))

            for call in at_calls:
                checked += 1
                if call not in known and call.startswith("net/minecraft"):
                    bad.append((name, f"At-target {call} не найден в маппингах"))

    print(f"версия: {version}   джар: {jar.name}")
    print(f"миксинов: {len(per_mixin)}   проверено целей: {checked}\n")
    for name, (owner, uniq) in per_mixin.items():
        short = name.rsplit("/", 1)[-1].removesuffix(".class")
        print(f"  {short:36} -> {owner}")
        if uniq:
            print(f"      {', '.join(uniq)}")

    if bad:
        print(f"\nПРОБЛЕМЫ ({len(bad)}):")
        for cls, why in sorted(set(bad)):
            print(f"  {cls}: {why}")
        return 1
    print("\nвсе цели миксинов на месте")
    return 0


if __name__ == "__main__":
    sys.exit(main())
