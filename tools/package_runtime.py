"""Create small platform-specific speech downloads. None of these native files go into the mod jar."""
import argparse, hashlib, io, json, zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--native', type=Path, required=True)
parser.add_argument('--onnx', type=Path, required=True)
parser.add_argument('--output', type=Path, default=Path('build/speech-runtimes'))
parser.add_argument('--index', type=Path, default=Path('src/main/resources/sayitem-speech/runtime-index.json'))
parser.add_argument('--platform')
args = parser.parse_args()
platforms = {'windows-x64':'win-x64', 'linux-x64':'linux-x64', 'linux-arm64':'linux-aarch64',
             'macos-x64':'osx-x64', 'macos-arm64':'osx-aarch64'}
def digest(data): return hashlib.sha256(data).hexdigest()
def put(archive, name, data):
    entry = zipfile.ZipInfo(name, (1980,1,1,0,0,0)); entry.compress_type = zipfile.ZIP_DEFLATED
    entry.external_attr = 0o100644 << 16
    archive.writestr(entry, data)
args.output.mkdir(parents=True, exist_ok=True)
index = {}
with zipfile.ZipFile(args.onnx) as original:
    for platform, ort_platform in platforms.items():
        if args.platform and args.platform != platform: continue
        roots = list(args.native.glob(f'**/{platform}/source.json'))
        if len(roots) != 1: raise RuntimeError(f'Expected one native artifact for {platform}')
        root = roots[0].parent
        native = json.loads(roots[0].read_text(encoding='utf-8-sig'))
        data = {'native/source.json': roots[0].read_bytes()}
        for license_file in Path('src/main/resources/licenses').glob('*.txt'):
            data['licenses/' + license_file.name] = license_file.read_bytes()
        for name, expected in native['files'].items():
            contents = (root / name).read_bytes()
            if digest(contents) != expected: raise RuntimeError(f'Bad native checksum: {name}')
            data['native/' + name] = contents
        jar_bytes = io.BytesIO()
        with zipfile.ZipFile(jar_bytes, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as reduced:
            for name in sorted(original.namelist()):
                if name.endswith('/') or '.dSYM/' in name or name.endswith(('.SF','.RSA','.DSA')): continue
                if 'ai/onnxruntime/native/' in name and not name.startswith(f'ai/onnxruntime/native/{ort_platform}/'): continue
                put(reduced, name, original.read(name))
        data['onnxruntime.jar'] = jar_bytes.getvalue()
        target = args.output / f'speech-runtime-{platform}.zip'
        with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, contents in sorted(data.items()): put(archive, name, contents)
        archive_bytes = target.read_bytes()
        index[platform] = {'url':f'https://github.com/JustNeoNixy/SayItem/releases/download/speech-runtime-0.4.0/{target.name}',
                          'bytes':len(archive_bytes), 'sha256':digest(archive_bytes),
                          'files':{name:digest(contents) for name,contents in sorted(data.items())}}
        print(platform, len(archive_bytes), 'bytes')
args.index.write_text(json.dumps(index, indent=2) + '\n', encoding='utf-8')
