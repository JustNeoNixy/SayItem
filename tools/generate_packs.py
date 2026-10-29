"""Build complete vocabulary from exported registries and locally cached Minecraft assets."""
import argparse
import json
import hashlib
import urllib.request
import zipfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--loom-cache", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    catalog = {}
    sources = {}
    translations = {locale: {} for locale in ("ja_jp", "es_es", "fr_fr", "de_de", "pl_pl")}
    english = {}
    for version in ("26.1.2", "26.2", "26.3"):
        catalog.update(json.loads((root / "build" / f"catalog-{version}.json").read_text(encoding="utf-8")))
        info = json.loads((args.loom_cache / version / "mojang_minecraft_info.json").read_text())
        index_path = root / "build" / f"assets-{version}.json"
        index = json.loads(urllib.request.urlopen(info["assetIndex"]["url"]).read())
        index_path.write_text(json.dumps(index), encoding="utf-8")
        sources[version] = {"assetIndex": info["assetIndex"]["url"], "languages": {}}
        with zipfile.ZipFile(args.loom_cache / version / "minecraft-client.jar") as jar:
            english.update(json.loads(jar.read("assets/minecraft/lang/en_us.json")))
        for locale in translations:
            obj = index["objects"][f"minecraft/lang/{locale}.json"]
            digest = obj["hash"]
            cached = args.loom_cache / "assets" / "objects" / digest[:2] / digest
            content = cached.read_bytes() if cached.exists() else urllib.request.urlopen(
                f"https://resources.download.minecraft.net/{digest[:2]}/{digest}").read()
            assert hashlib.sha1(content).hexdigest() == digest
            translations[locale].update(json.loads(content))
            sources[version]["languages"][locale] = digest
    from pykakasi import kakasi
    converter = kakasi()
    destination = root / "src/main/resources/languages"
    overrides_path = root / "tools/pack-overrides.json"
    if not overrides_path.exists():
        overrides = {id: json.loads((destination / f"{id}.json").read_text(encoding="utf-8"))["items"]
                     for id in ("ja", "es", "fr", "de", "pl")}
        overrides_path.write_text(json.dumps(overrides, ensure_ascii=False, indent=2), encoding="utf-8")
    overrides = json.loads(overrides_path.read_text(encoding="utf-8"))
    definitions = [("en", "English", english), ("ja", "Japanese · 日本語", translations["ja_jp"]),
                   ("es", "Spanish · Español", translations["es_es"]), ("fr", "French · Français", translations["fr_fr"]),
                   ("de", "German · Deutsch", translations["de_de"]), ("pl", "Polish · Polski", translations["pl_pl"])]
    for id, name, dictionary in definitions:
        items = {}
        missing = []
        for item, key in sorted(catalog.items()):
            if key not in dictionary:
                missing.append((item, key))
                continue
            text = dictionary[key]
            entry = {"text": text, "reading": "", "aliases": []}
            if id == "ja":
                parts = converter.convert(text)
                entry["reading"] = " ".join(p["hepburn"] for p in parts)
                kana = "".join(p["hira"] for p in parts)
                entry["aliases"] = [kana] if kana != text else []
            if item in overrides.get(id, {}):
                original = overrides[id][item]
                entry["aliases"] = list(dict.fromkeys(entry["aliases"] + original.get("aliases", [])
                    + ([original["text"]] if original["text"] != text else [])))
                if original.get("reading"):
                    entry["reading"] = original["reading"]
            items[item] = entry
        if missing:
            raise SystemExit(f"Untranslated {id} items: {missing}")
        pack = {"schemaVersion": 1, "id": id, "name": name, "whisperLanguage": id, "items": items}
        (destination / f"{id}.json").write_text(json.dumps(pack, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"{id}: {len(items)} items")
    (root / "src/main/resources/item-catalog.json").write_text(json.dumps(catalog, indent=2), encoding="utf-8")
    (root / "tools/translation-sources.json").write_text(json.dumps(sources, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
