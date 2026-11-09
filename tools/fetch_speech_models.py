"""Prepare pinned model files for builds/tests; players download them through Minecraft."""
import argparse, hashlib, json, os, time, urllib.request
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--destination', type=Path, default=Path('build/model-source'))
args = parser.parse_args()
version = next(line.split('=',1)[1].strip() for line in Path('gradle.properties').read_text().splitlines() if line.startswith('speechlib_version='))
if not all(character in '0123456789.-abcdefghijklmnopqrstuvwxyz' for character in version): raise ValueError('Invalid SpeechLib version')
url = f'https://raw.githubusercontent.com/JustNeoNixy/SpeechLib/v{version}/src/main/resources/speechlib/model.json'
with urllib.request.urlopen(url, timeout=45) as response:
    manifest = json.load(response)
def sha(path):
    with path.open('rb') as stream: return hashlib.file_digest(stream, 'sha256').hexdigest()
for name, spec in manifest['files'].items():
    target = args.destination / name
    if target.is_file() and target.stat().st_size == spec['bytes'] and sha(target) == spec['sha256']: continue
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(target.name + '.part')
    for attempt in range(3):
        try:
            request = urllib.request.Request(spec['url'], headers={'User-Agent':'SayItem/0.4'})
            with urllib.request.urlopen(request, timeout=120) as response, temporary.open('wb') as output:
                total = 0
                while chunk := response.read(65536):
                    total += len(chunk)
                    if total > spec['bytes']: raise RuntimeError('Model exceeded its pinned size')
                    output.write(chunk)
            if temporary.stat().st_size != spec['bytes'] or sha(temporary) != spec['sha256']: raise RuntimeError('Model checksum mismatch')
            os.replace(temporary, target)
            print('Prepared', name, flush=True)
            break
        except Exception:
            temporary.unlink(missing_ok=True)
            if attempt == 2: raise
            time.sleep(1 + attempt)
