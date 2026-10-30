"""Build tooling only: extract pinned native CTranslate2 libraries, never Python runtime files."""
import argparse, ctypes, hashlib, json, os, shutil, urllib.request, zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('platform')
parser.add_argument('destination', type=Path)
parser.add_argument('--package', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent
spec = json.loads((root / 'wheels.json').read_text(encoding='utf-8'))[args.platform]
dest = args.destination.resolve()
dest.mkdir(parents=True, exist_ok=True)
archive = dest / 'source.whl'
def sha(path): return hashlib.file_digest(path.open('rb'), 'sha256').hexdigest()
if not archive.is_file() or sha(archive) != spec['sha256']:
    with urllib.request.urlopen(spec['url'], timeout=120) as response, archive.open('wb') as output:
        shutil.copyfileobj(response, output)
if sha(archive) != spec['sha256']: raise RuntimeError('CTranslate2 source archive checksum mismatch')
files = []
with zipfile.ZipFile(archive) as source:
    for name in source.namelist():
        path = Path(name)
        if ('..' in path.parts or path.is_absolute()): raise RuntimeError('Unsafe dependency path')
        native = (path.suffix in ('.dll', '.dylib') or '.so' in path.name)
        if not native or path.name.startswith('_ext') or 'cudnn' in path.name: continue
        target = dest / 'runtime' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        with source.open(name) as input_file, target.open('wb') as output: shutil.copyfileobj(input_file, output)
        files.append(name)
library = next(name for name in files if Path(name).name.startswith(('libctranslate2', 'ctranslate2.dll')))
(dest / 'dependencies.cmake').write_text(f'set(CT2_LIBRARY "{(dest / "runtime" / library).as_posix()}")\n', encoding='utf-8')
metadata = {'engine': 'CTranslate2 4.8.4', 'sourceRevision': '5de572ef60b67a0276c5989ee73d0072808ff4c3',
            'archive': spec, 'library': library, 'files': files}
(dest / 'metadata.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
if args.package:
    package = args.package.resolve()
    output = package / args.platform
    output.mkdir(parents=True, exist_ok=True)
    for name in files:
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(dest / 'runtime' / name, target)
    extension = 'dll' if args.platform.startswith('windows') else 'dylib' if args.platform.startswith('macos') else 'so'
    wrapper = f'sayitem-ct2.{extension}'
    shutil.copy2(dest / 'compiled' / 'output' / wrapper, output / wrapper)
    metadata['files'] = {name: sha(output / name) for name in files + [wrapper]}
    metadata['wrapper'] = wrapper
    (output / 'source.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    dll_directories = []
    if os.name == 'nt':
        for directory in {str((output / name).parent) for name in files}:
            dll_directories.append(os.add_dll_directory(directory))
    engine = ctypes.CDLL(str(output / wrapper))
    engine.sayitem_ct2_version.restype = ctypes.c_char_p
    if engine.sayitem_ct2_version() != b'4.8.4': raise RuntimeError('Unexpected native engine version')
    for symbol in ('sayitem_ct2_load', 'sayitem_ct2_free', 'sayitem_ct2_transcribe'):
        getattr(engine, symbol)
    print(f'Native CTranslate2 bridge verified: {args.platform}')
