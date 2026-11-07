"""Summarize the real Java engine run; no audio or model weights enter the repository."""
import argparse
import json
import math
import statistics
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def statistics_for(rows):
    measured = [r for r in rows if 'accepted' in r]
    times = sorted(r['elapsed_ms'] for r in measured)
    return dict(recordings=len(measured), accepted=sum(r['accepted'] for r in measured),
                median_ms=round(statistics.median(times)) if times else None,
                p95_ms=times[math.ceil(len(times)*.95)-1] if times else None,
                empty=sum(not r['heard'].strip() for r in measured))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', type=Path, default=ROOT/'build/human-final.json')
    parser.add_argument('--output', type=Path, default=ROOT/'tools/speech-accuracy.json')
    args = parser.parse_args()
    manifest = json.loads((ROOT/'tools/human-speech-sources.json').read_text(encoding='utf-8'))
    cases = manifest['cases'] + json.loads((ROOT/'tools'/manifest['supplementalManifest']).read_text(encoding='utf-8'))['cases']
    sources = {c['id']: c for c in cases}
    rows = json.loads(args.input.read_text(encoding='utf-8'))
    expected = {(p, c['id']) for p in ('gpu_base', 'gpu_small', 'gpu_turbo') for c in cases}
    actual = {(r['profile'], r['id']) for r in rows}
    if actual != expected or len(actual) != len(rows) or any('accepted' not in r for r in rows):
        raise ValueError('Incomplete, duplicated, or skipped evaluation cases')
    for row in rows:
        row['partition'] = sources[row['id']]['partition']
    profiles = {}
    for profile in ('gpu_base', 'gpu_small', 'gpu_turbo'):
        selected = [r for r in rows if r['profile'] == profile]
        profiles[profile] = {
            kind: {part: statistics_for([r for r in selected if r['kind'] == kind and
                                       (part == 'all' or r['partition'] == part)])
                   for part in ('all', 'calibration', 'validation')}
            for kind in ('item_name', 'general_speech')}
        profiles[profile]['languages'] = {
            language: statistics_for([r for r in selected if r['language'] == language and r['kind'] == 'item_name'])
            for language in sorted({c['language'] for c in cases})}
    small = {r['id']: r for r in rows if r['profile'] == 'gpu_small' and r['kind'] == 'item_name'}
    turbo = {r['id']: r for r in rows if r['profile'] == 'gpu_turbo' and r['kind'] == 'item_name'}
    document = dict(
        date='2026-11-07', version='0.9.0',
        hardware=dict(gpu='AMD Radeon RX 570, 4 GB', driver='31.0.21925.1001', cpu='Intel Core i5-3450'),
        method=dict(engine='Actual LocalWhisper Java path, Vulkan whisper.cpp, Silero VAD, normalized PCM16 16 kHz',
                    bridge='whisper.cpp-v1.9.5-d1be6fde-bridge3', beam=5, context='Full model audio context',
                    prompts='None; expected item names are never supplied to the recognizer',
                    requirement_percent=90, latency='Warm inference including VAD; excludes model loading, download, recording and game rendering',
                    sources='human-speech-sources.json and finnish-human-speech-sources.json',
                    partition='Declared calibration/validation split. Turbo chosen using calibration pilots before validation was inspected.'),
        coverage=dict(item_recordings=sum(c['item'] is not None for c in cases),
                      item_languages=len({c['language'] for c in cases if c['item'] is not None}),
                      general_speech_recordings=sum(c['item'] is None for c in cases),
                      item_counts=dict(sorted(Counter(c['language'] for c in cases if c['item'] is not None).items()))),
        limitations=[
            'A small curated sample of human dictionary recordings, not all 1,657 items, accents or microphones.',
            'Finnish has whole-sentence FLEURS controls only; no isolated Finnish item-word coverage.',
            'Some languages have only one or two recordings and no independent validation sample.',
            'Sources label recordings as human; native speakers have not independently reviewed every recording or generated reading.',
            'No live Minecraft frame-rate, microphone or visual test. GPU sharing with Minecraft can change latency.',
            'CPU Base was not benchmarked in this human GPU comparison. Synthetic regression tests are separate.',
            'Text-match acceptance is not a pronunciation score. Homophones and short isolated words remain difficult.'
        ],
        known_cpu_misses=[dict(language='ru', fixture='src/test/resources/audio/ru-iron-pickaxe.wav',
                              expected='Железная кирка', heard='Железно, Кирка.', accepted_at_90=False,
                              source='Windows CI run 42332941490, CTranslate2 Base INT8; synthetic regression fixture')],
        experiments_rejected=[
            'Shortened audio context, artificial padding and generic inventory prompts had inconsistent calibration gains and regressions.',
            'A grammar containing every item name remained inaccurate in a five-case pilot and added substantial sampling cost.',
            'No answer hints, relaxed requirement or misspelling aliases were added to make the benchmark pass.'
        ], profiles=profiles,
        turbo_vs_small=dict(improved=sum(turbo[k]['accepted'] and not v['accepted'] for k, v in small.items()),
                            regressed=sum(v['accepted'] and not turbo[k]['accepted'] for k, v in small.items())),
        results=rows)
    args.output.write_text(json.dumps(document, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    for profile, data in profiles.items(): print(profile, data['item_name'])


if __name__ == '__main__':
    main()
