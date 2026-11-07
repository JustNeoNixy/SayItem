"""Pinned, development-only GPU model comparison. Weights stay outside the repository."""
import argparse
import hashlib
import urllib.request
from pathlib import Path

REVISION = '5359861c739e955e79d9a303bcbc70fb988958b1'
MODELS = {
    'base': (147951465, '60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe'),
    'small-q5_1': (190085487, 'ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb'),
    'small-q8_0': (264464607, '49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f'),
    'medium-q5_0': (539212467, '19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f'),
    'large-v3-turbo-q5_0': (574041195, '394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2')
}
ROOT = Path(__file__).resolve().parents[1]
if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('models', nargs='+', choices=MODELS)
    args = parser.parse_args(); folder = ROOT / 'build/speech-eval-models'; folder.mkdir(parents=True, exist_ok=True)
    for name in args.models:
        size, expected = MODELS[name]; target = folder / f'ggml-{name}.bin'
        if target.exists() and target.stat().st_size == size and hashlib.file_digest(target.open('rb'), 'sha256').hexdigest() == expected:
            print(name, 'verified', flush=True); continue
        url = f'https://huggingface.co/ggerganov/whisper.cpp/resolve/{REVISION}/{target.name}'
        temporary = target.with_suffix('.part'); digest = hashlib.sha256(); count = 0
        with urllib.request.urlopen(url, timeout=90) as response, temporary.open('wb') as output:
            while chunk := response.read(1024*1024):
                count += len(chunk)
                if count > size: raise ValueError('Model exceeds pinned size')
                output.write(chunk); digest.update(chunk)
        if count != size or digest.hexdigest() != expected: raise ValueError('Model checksum mismatch')
        temporary.replace(target); print(name, size, 'verified', flush=True)
