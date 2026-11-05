"""Package only ordinary native DLLs/licenses; no executables, mods, models or build tools."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('source', type=Path)
parser.add_argument('--output', type=Path, default=Path('build/speech-runtimes/speech-vulkan-windows-x64.zip'))
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
files = ['sayitem-ggml-base.dll', 'sayitem-ggml-cpu.dll', 'sayitem-ggml-vulkan.dll',
         'sayitem-ggml.dll', 'sayitem-whisper.dll', 'sayitem-vulkan.dll', 'whisper-MIT.txt', 'source.txt']
args.output.parent.mkdir(parents=True, exist_ok=True)
checksums = {}
with zipfile.ZipFile(args.output, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name in files:
        payload = (args.source / name).read_bytes()
        entry = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
        entry.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(entry, payload)
        checksums[name] = hashlib.sha256(payload).hexdigest()
payload = args.output.read_bytes()
models = {}
for line in (root / 'build/gpu-models/models.jsonl').read_text(encoding='utf-8').splitlines():
    spec = json.loads(line)
    key = 'small' if 'small' in spec['name'] else 'base'
    models[key] = {'filename': spec['name'], 'url': spec['url'], 'bytes': spec['bytes'], 'sha256': spec['sha256']}
manifest = {'runtime': {'url': 'https://github.com/JustNeoNixy/SayItem/releases/download/speech-vulkan-0.7.0/' + args.output.name,
    'bytes': len(payload), 'sha256': hashlib.sha256(payload).hexdigest(), 'files': checksums}, 'models': models}
(root / 'src/main/resources/sayitem-speech/vulkan-index.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
print(f'GPU libraries: {len(payload):,} bytes; no model weights or executable included')
