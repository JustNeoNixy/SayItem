"""Build the standalone Qt companion. The Minecraft mod is distributed separately."""
import hashlib
import os
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    from huggingface_hub import snapshot_download
    model = Path(snapshot_download("Systran/faster-whisper-base", cache_dir=str(ROOT / "speech/models"),
        allow_patterns=["config.json", "model.bin", "tokenizer.json", "vocabulary.json", "vocabulary.txt"], local_files_only=True))
    # Do not let unrelated tools on PATH supply incompatible ICU/OpenSSL DLLs.
    system_root = os.environ.get("SystemRoot", "C:\\Windows")
    build_env = {**os.environ, "SAYITEM_MODEL_PATH": str(model), "PATH": os.pathsep.join([
        str(Path(sys.executable).parent), sys.base_prefix, str(Path(system_root) / "System32"), system_root])}
    subprocess.run([sys.executable, "-m", "PyInstaller", "--noconfirm", "--distpath", str(ROOT / "dist"),
        "--workpath", str(ROOT / "build/pyinstaller"), str(ROOT / "tools/SayItem.spec")], check=True,
        cwd=ROOT, env=build_env)
    output = ROOT / "dist/SayItem"
    shutil.copy2(ROOT / "README.md", output / "README.md")
    shutil.copy2(ROOT / "LICENSE", output / "LICENSE.txt")
    licenses = output / "third-party-licenses"
    licenses.mkdir(exist_ok=True)
    for name, url in {
        "whisper-MIT.txt": "https://raw.githubusercontent.com/openai/whisper/main/LICENSE",
        "faster-whisper-MIT.txt": "https://raw.githubusercontent.com/SYSTRAN/faster-whisper/master/LICENSE",
    }.items():
        urllib.request.urlretrieve(url, licenses / name)
    from importlib.metadata import distributions
    included = {"pyside6-essentials", "shiboken6", "faster-whisper", "ctranslate2", "av", "onnxruntime",
                "numpy", "huggingface-hub", "tokenizers", "pyinstaller"}
    for distribution in distributions():
        name = distribution.metadata.get("Name", "").lower().replace("_", "-")
        if name not in included:
            continue
        for file in distribution.files or ():
            if ".dist-info/licenses/" in str(file).replace("\\", "/") or file.name.lower() in ("license", "license.txt", "copying"):
                path = Path(distribution.locate_file(file))
                if path.is_file():
                    target = licenses / name / path.name
                    target.parent.mkdir(exist_ok=True)
                    shutil.copy2(path, target)
    archive = Path(shutil.make_archive(str(ROOT / "dist/SayItem-0.2.0-Windows"), "zip", ROOT / "dist", "SayItem"))
    archive.with_suffix(".zip.sha256").write_text(f"{hashlib.sha256(archive.read_bytes()).hexdigest()}  {archive.name}\n", encoding="ascii")
    print(f"EXE: {output / 'SayItem.exe'}")
    print(f"ZIP: {archive} ({archive.stat().st_size / 1024 / 1024:.1f} MB)")


if __name__ == "__main__":
    main()
