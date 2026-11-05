"""Reproduce Bulgarian/Russian/Italian packs from checksum-pinned Mojang assets."""
import hashlib
import json
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANGUAGES = {'bg': ('bg_bg', 'Bulgarian · Български'), 'ru': ('ru_ru', 'Russian · Русский'),
             'it': ('it_it', 'Italian · Italiano')}


def generate():
    catalog = json.loads((ROOT / 'src/main/resources/item-catalog.json').read_text(encoding='utf-8'))
    sources = json.loads((ROOT / 'tools/translation-sources.json').read_text(encoding='utf-8'))
    cache = ROOT / 'build/language-source'; cache.mkdir(parents=True, exist_ok=True)
    # New locales use the same pinned asset indexes as the existing packs.
    for source in sources.values():
        missing = [locale for locale, _ in LANGUAGES.values() if locale not in source['languages']]
        if not missing: continue
        with urllib.request.urlopen(source['assetIndex'], timeout=60) as response: payload = response.read()
        expected = source['assetIndex'].split('/')[-2]
        if hashlib.sha1(payload).hexdigest() != expected: raise RuntimeError('Mojang asset index checksum mismatch')
        assets = json.loads(payload)['objects']
        for locale in missing: source['languages'][locale] = assets[f'minecraft/lang/{locale}.json']['hash']
    (ROOT / 'tools/translation-sources.json').write_text(json.dumps(sources, indent=2) + '\n', encoding='utf-8')
    for language, (locale, name) in LANGUAGES.items():
        names = {}
        for version, source in sources.items():
            digest = source['languages'][locale]
            target = cache / f'{locale}-{digest}.json'
            if not target.is_file() or hashlib.sha1(target.read_bytes()).hexdigest() != digest:
                url = f'https://resources.download.minecraft.net/{digest[:2]}/{digest}'
                with urllib.request.urlopen(url, timeout=60) as response: payload = response.read()
                if hashlib.sha1(payload).hexdigest() != digest: raise RuntimeError('Mojang translation checksum mismatch')
                target.write_bytes(payload)
            names.update(json.loads(target.read_text(encoding='utf-8')))
        items = {}
        for identifier, key in sorted(catalog.items()):
            text = names.get(key)
            if not text: raise RuntimeError(f'{locale} has no native name for {identifier}: {key}')
            aliases = []
            if language == 'ru' and 'ё' in text.lower(): aliases.append(text.replace('ё', 'е').replace('Ё', 'Е'))
            if language == 'bg' and 'ѝ' in text: aliases.append(text.replace('ѝ', 'и'))
            items[identifier] = {'text': text, 'reading': '', 'aliases': aliases}
        pack = {'schemaVersion': 1, 'id': language, 'name': name, 'whisperLanguage': language, 'items': items}
        output = ROOT / f'src/main/resources/languages/{language}.json'
        output.write_text(json.dumps(pack, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(f'{language}: {len(items)} native item names')


if __name__ == '__main__': generate()
