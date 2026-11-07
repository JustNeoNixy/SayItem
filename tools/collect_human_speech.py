"""Collect attributed human dictionary recordings for SayItem evaluation, never runtime.

Audio stays in ignored build/. The manifest records source/voice/licence/checksum;
it does not claim dictionary coverage proves recognition for every item or speaker.
"""
import argparse
import hashlib
import html
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / 'build/human-speech'
MANIFEST = ROOT / 'tools/human-speech-sources.json'
AGENT = 'SayItem-SpeechEvaluation/1.0 (https://github.com/JustNeoNixy/SayItem)'
ITEMS = ('apple', 'stone', 'sand', 'glass', 'bow', 'arrow', 'coal', 'stick', 'bread',
         'sugar', 'paper', 'egg', 'feather', 'string', 'bone', 'book', 'bucket', 'diamond', 'emerald', 'snowball')
LANGUAGES = ('en', 'ja', 'es', 'fr', 'de', 'pl', 'bg', 'ru', 'it', 'pt', 'nl', 'sv', 'da',
             'no', 'fi', 'cs', 'sk', 'uk', 'el', 'tr', 'hu', 'ro', 'ko', 'zh', 'id', 'vi')
HEADINGS = dict(zip(LANGUAGES, ('English', 'Japanese', 'Spanish', 'French', 'German', 'Polish',
    'Bulgarian', 'Russian', 'Italian', 'Portuguese', 'Dutch', 'Swedish', 'Danish', 'Norwegian Bokmål',
    'Finnish', 'Czech', 'Slovak', 'Ukrainian', 'Greek', 'Turkish', 'Hungarian', 'Romanian',
    'Korean', 'Chinese', 'Indonesian', 'Vietnamese')))


def fetch(url, limit=12_000_000):
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': AGENT})
            with urllib.request.urlopen(req, timeout=45) as response:
                data = response.read(limit + 1)
            if len(data) > limit: raise ValueError('Response exceeds size limit')
            return data
        except urllib.error.HTTPError as error:
            if error.code not in (429, 503) or attempt == 3: raise
            time.sleep(min(30, int(error.headers.get('Retry-After', '5'))))


def api(host, parameters):
    url = f'https://{host}/w/api.php?' + urllib.parse.urlencode(dict(format='json', **parameters))
    key = hashlib.sha256(url.encode()).hexdigest()
    target = CACHE / 'api' / (key + '.json'); target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists(): target.write_bytes(fetch(url))
    data = json.loads(target.read_bytes())
    if 'error' in data: raise RuntimeError(data['error'])
    return data


def plain(value):
    return html.unescape(re.sub('<[^>]*>', '', str(value))).strip()


def appropriate_recording(language, item, entry, filename):
    # English "bow" has /bau/ and /bou/ recordings. Exclude it rather than
    # label the wrong dictionary sense as a failed Minecraft weapon pronunciation.
    if language == 'en' and item == 'bow': return False
    name = unicodedata.normalize('NFKC', filename).lower().removeprefix('file:')
    if language not in ('ja', 'zh'):
        word = unicodedata.normalize('NFKC', entry['text']).lower()
        stem = re.sub(r'\.(ogg|oga|wav|mp3|flac)$', '', name)
        return bool(re.search(r'(?:^|[-_])' + re.escape(word) + r'(?:[-_]\d+| \([^)]*\))?$', stem)
            or re.fullmatch(re.escape(language) + r'\s+' + re.escape(word), stem))
    if language == 'zh':
        if name.startswith('zh-'): return True  # Mandarin pinyin files in this word's pronunciation section.
        return name.removesuffix('.wav').endswith('-' + entry['text'].lower())
    kana = lambda value: ''.join(chr(ord(c)-96) if '\u30a1' <= c <= '\u30f6' else c for c in value)
    name = kana(name)
    if any(kana(alias) in name for alias in entry['aliases'] if alias): return True
    reading = entry.get('reading', '').strip().lower().replace(' ', '-')
    return bool(reading and re.search(r'(?:^|[-_ (])' + re.escape(reading) + r'(?:[-_ ).]|$)', name))


def bare_word_description(description, expected):
    # Older Shtooka files explicitly state an article in brackets even when their
    # filename omits it, e.g. "arc" [l'arc]. They are not bare-item-word recordings.
    word = expected.casefold()
    for spoken in re.findall(r'\[([^\]]+)\]|"([^\"]+)"', description):
        spoken = next((part for part in spoken if part), '').strip().casefold()
        if spoken != word and spoken.endswith(word) and len(spoken) > len(word):
            prefix = spoken[:-len(word)].strip()
            if prefix in ('le', 'la', 'les', "l'", 'un', 'une', 'il', 'lo', 'una', 'uno', 'el', 'los', 'las', 'the', 'a', 'an'):
                return False
    return True


def rendered_recordings(language, words, maximum):
    found = []; used_items = set()
    for word, entries in words.items():
        if all(item in used_items for item, _ in entries): continue
        try:
            sections = api('en.wiktionary.org', dict(action='parse', page=word, prop='sections', redirects='1'))['parse']['sections']
        except (RuntimeError, urllib.error.HTTPError): continue
        heading = next((s for s in sections if s['line'] in (HEADINGS[language], 'Norwegian' if language == 'no' else '')), None)
        if not heading: continue
        pronunciation = [s for s in sections if s['number'].startswith(heading['number'] + '.') and s['line'].startswith('Pronunciation')]
        indices = [s['index'] for s in pronunciation] or [heading['index']]
        for index in indices[:2]:
            markup = api('en.wiktionary.org', dict(action='parse', page=word, prop='text', section=index,
                redirects='1', disableeditsection='1', disablelimitreport='1'))['parse']['text']['*']
            files = []
            for value in re.findall(r'(?:src|href|resource)="([^\"]+)"', markup):
                value = html.unescape(value)
                if not value.startswith(('//upload.wikimedia.org/', 'https://upload.wikimedia.org/')): continue
                parts = urllib.parse.unquote(urllib.parse.urlsplit(value).path).split('/')
                name = parts[-2] if '/transcoded/' in value else parts[-1]
                if not name.lower().endswith(('.ogg', '.oga', '.wav', '.mp3', '.flac')): continue
                # Chinese pages also have other dialects. Use explicitly Mandarin/zh files.
                if language == 'zh' and not re.search(r'^(Zh-|Cmn-|LL-Q9192)', name, re.I): continue
                if name not in files: files.append(name)
            for filename in files:
                for item, entry in entries:
                    if not appropriate_recording(language, item, entry, filename): continue
                    found.append(dict(language=language, item='minecraft:' + item, word=word,
                        expected=entry['text'], aliases=entry['aliases'], file='File:' + filename.replace('_', ' ')))
                    used_items.add(item)
        if len(used_items) >= maximum: break
    return found


def discover(per_language):
    candidates = []
    for language in LANGUAGES:
        pack = json.loads((ROOT / f'src/main/resources/languages/{language}.json').read_text(encoding='utf-8'))
        words = {}
        for item in ITEMS:
            entry = pack['items']['minecraft:' + item]
            titles = [entry['text'], entry['text'].lower()]
            if language == 'ja': titles = entry['aliases'][:1] + titles
            for title in titles: words.setdefault(title, []).append((item, entry))
        pages = api('en.wiktionary.org', dict(action='query', prop='revisions', rvprop='content',
            rvslots='main', titles='|'.join(words), redirects='1')).get('query', {}).get('pages', {})
        found = []
        allowed = {'no', 'nb'} if language == 'no' else {'zh', 'cmn'} if language == 'zh' else {language}
        for page in pages.values():
            word = page.get('title', '')
            entries = words.get(word) or next((v for k, v in words.items() if k.casefold() == word.casefold()), [])
            if not entries: continue
            wiki = page.get('revisions', [{}])[0].get('slots', {}).get('main', {}).get('*', '')
            recordings = re.findall(r'\{\{audio\s*\|\s*([^|{}]+)\s*\|\s*([^|{}]+)', wiki, re.I)
            for code, filename in recordings:
                if code.strip() not in allowed: continue
                filename = filename.strip().removeprefix('File:').replace('_', ' ')
                if not filename.lower().endswith(('.ogg', '.wav', '.oga', '.flac', '.mp3')): continue
                for item, entry in entries:
                    if not appropriate_recording(language, item, entry, filename): continue
                    found.append(dict(language=language, item='minecraft:' + item, word=word,
                        expected=entry['text'], aliases=entry['aliases'], file='File:' + filename))
        unique = {(c['item'], c['file']): c for c in found}
        if len(unique) < per_language:
            for candidate in rendered_recordings(language, words, per_language): unique[(candidate['item'], candidate['file'])] = candidate
        candidates.extend(unique.values())
        print(f'{language}: {len(unique)} dictionary recording candidates', flush=True)
    metadata = {}
    titles = list(dict.fromkeys(c['file'] for c in candidates))
    for start in range(0, len(titles), 40):
        pages = api('commons.wikimedia.org', dict(action='query', prop='imageinfo',
            iiprop='url|sha1|size|extmetadata', titles='|'.join(titles[start:start+40]))).get('query', {}).get('pages', {})
        for page in pages.values():
            if page.get('imageinfo'): metadata[page['title'].replace('_', ' ')] = page['imageinfo'][0]
    selected = []
    for language in LANGUAGES:
        options = []
        for candidate in candidates:
            if candidate['language'] != language: continue
            info = metadata.get(candidate['file'])
            if not info or info.get('size', 0) > 5_000_000: continue
            meta = {k: plain(v.get('value', '')) for k, v in info.get('extmetadata', {}).items()}
            license_url = meta.get('LicenseUrl', '')
            if not license_url and meta.get('LicenseShortName', '').lower() == 'public domain':
                license_url = 'https://commons.wikimedia.org/wiki/Commons:Copyright_tags#Public_domain'
            if not license_url.startswith(('https://creativecommons.org/', 'http://creativecommons.org/', 'https://commons.wikimedia.org/wiki/Commons:Copyright_tags#Public_domain')): continue
            description = ' '.join(meta.get(k, '') for k in ('ImageDescription', 'Categories', 'ObjectName'))
            if not bare_word_description(description, candidate['expected']): continue
            if re.search(r'computer.generated|text.to.speech|synthetic|AI.generated|\bTTS\b', description, re.I): continue
            url = urllib.parse.urlsplit(info['url'])
            if url.netloc != 'upload.wikimedia.org': continue
            item = candidate['item'].split(':')[1]
            identifier = f'{language}-{item}-{info["sha1"][:10]}'
            options.append(dict(id=identifier, **candidate, sha1=info['sha1'],
                url=urllib.parse.urlunsplit((url.scheme, url.netloc, url.path, '', '')),
                source=info.get('descriptionurl', 'https://commons.wikimedia.org/wiki/' + urllib.parse.quote(candidate['file'])),
                license=meta.get('LicenseShortName', ''), licenseUrl=license_url,
                artist=meta.get('Artist') or meta.get('Attribution') or 'See original recording page',
                description=description[:1500], wav=identifier + '.wav'))
        # Prefer different item names before adding a second voice for the same word.
        seen = set(); diverse = []; remainder = []
        for option in options:
            if option['item'] not in seen: diverse.append(option); seen.add(option['item'])
            else: remainder.append(option)
        selected.extend((diverse + remainder)[:per_language])
    document = dict(source='Wiktionary audio templates and Wikimedia Commons recording metadata',
        purpose='Human item-name regression set; limited words/speakers, not exhaustive recognition accuracy.',
        conversion='Decode whole recording, mono 16000 Hz PCM16; no speed changes or word splicing.',
        languages={lang: sum(c['language'] == lang for c in selected) for lang in LANGUAGES}, cases=selected)
    for language in LANGUAGES:
        group = [c for c in selected if c['language'] == language]
        for index, case in enumerate(group): case['partition'] = 'calibration' if index < max(1, len(group)-2) else 'validation'
    if (ROOT / 'tools/finnish-human-speech-sources.json').exists(): document['supplementalManifest'] = 'finnish-human-speech-sources.json'
    MANIFEST.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(document['languages']), flush=True)


def download():
    import av
    import numpy as np
    cases = json.loads(MANIFEST.read_text(encoding='utf-8'))['cases']
    raw_dir = CACHE / 'original'; raw_dir.mkdir(parents=True, exist_ok=True)
    wav_dir = CACHE / 'wav'; wav_dir.mkdir(parents=True, exist_ok=True)
    import wave
    for case in cases:
        target = raw_dir / (case['sha1'] + Path(urllib.parse.unquote(urllib.parse.urlsplit(case['url']).path)).suffix)
        if not target.exists() or hashlib.sha1(target.read_bytes()).hexdigest() != case['sha1']:
            content = fetch(case['url'], limit=5_000_000)
            if hashlib.sha1(content).hexdigest() != case['sha1']: raise ValueError('Wikimedia recording checksum mismatch')
            target.write_bytes(content)
        output = wav_dir / case['wav']
        with av.open(str(target)) as container:
            resampler = av.AudioResampler(format='s16', layout='mono', rate=16000)
            frames = []
            for frame in container.decode(audio=0): frames.extend(resampler.resample(frame))
            frames.extend(resampler.resample(None))
            pcm = np.concatenate([f.to_ndarray().reshape(-1) for f in frames]).astype('<i2')
        with wave.open(str(output), 'wb') as writer:
            writer.setnchannels(1); writer.setsampwidth(2); writer.setframerate(16000); writer.writeframes(pcm.tobytes())
        print(f'{case["id"]}: {len(pcm)/16000:.2f}s', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--discover', action='store_true')
    parser.add_argument('--download', action='store_true'); parser.add_argument('--per-language', type=int, default=6)
    args = parser.parse_args()
    if args.discover: discover(args.per_language)
    if args.download: download()
