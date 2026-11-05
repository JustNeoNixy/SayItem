"""Summarize the reproducible Java/native benchmark without storing personal microphone data."""
import json
import math
import statistics
from pathlib import Path

root = Path(__file__).resolve().parents[1]
rows = json.loads((root / 'build/speech-benchmark.json').read_text(encoding='utf-8'))
models = {'cpu': 'multilingual Base, CTranslate2 INT8 CPU',
          'gpu_base': 'multilingual Base, whisper.cpp Vulkan FP16 weights',
          'gpu_small': 'multilingual Small Q5_1, whisper.cpp Vulkan'}
summary = {
    'date': '2026-11-05',
    'hardware': {'gpu': 'Radeon RX 570, 4 GB', 'cpuThreads': 3, 'platform': 'Windows x64'},
    'method': 'Actual LocalWhisper Java/native mod path, initialized and warmed; 18 identical English/Polish/Bulgarian/Russian recordings, two runs per profile. No Minecraft rendering load. Some recordings are synthetic; this small regression set is not a general accuracy benchmark.',
    'profiles': {},
    'results': rows}
for profile, model in models.items():
    selected = [row for row in rows if row['profile'] == profile]
    latencies = sorted(row['elapsed_ms'] for row in selected)
    positive = [row for row in selected if 'match_percent' in row]
    summary['profiles'][profile] = {'model': model, 'samples': len(selected),
        'median_ms': statistics.median(latencies),
        'p95_ms': latencies[math.ceil(.95*len(latencies))-1],
        'positive_samples': len(positive),
        'matched_at_90_percent': sum(row['match_percent'] >= 90 for row in positive)}
(root / 'tools/speech-performance.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(json.dumps(summary['profiles'], indent=2))
