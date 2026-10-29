# PyInstaller specification. All runtime speech dependencies and the model stay local.
from pathlib import Path
import os
from PyInstaller.utils.hooks import collect_all

root = Path(SPECPATH).parent
datas = []
binaries = []
hiddenimports = []
for package in ('faster_whisper', 'ctranslate2', 'av', 'onnxruntime', 'tokenizers', 'huggingface_hub'):
    package_data, package_binaries, package_imports = collect_all(package)
    datas += package_data
    binaries += package_binaries
    hiddenimports += package_imports
datas += [(os.environ['SAYITEM_MODEL_PATH'], 'models/base')]
a = Analysis([str(root / 'speech/desktop.py')], pathex=[str(root / 'speech')],
    binaries=binaries, datas=datas, hiddenimports=hiddenimports,
    excludes=['torch', 'transformers', 'tensorflow', 'matplotlib', 'IPython', 'pytest', 'tkinter'],
    noarchive=False)
# Qt uses the Windows ICU API; a third-party ICU with the same filename is incompatible.
a.binaries = [entry for entry in a.binaries if Path(entry[0]).name.lower() not in ('icuuc.dll', 'icuin.dll', 'icudt.dll')]
pyz = PYZ(a.pure)
exe = EXE(pyz, a.scripts, [], exclude_binaries=True, name='SayItem',
    debug=False, bootloader_ignore_signals=False, strip=False, upx=False, console=os.environ.get('SAYITEM_CONSOLE') == '1',
    disable_windowed_traceback=False)
collect = COLLECT(exe, a.binaries, a.datas, strip=False, upx=False, name='SayItem')
