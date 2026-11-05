"""Development-only RX/Vulkan comparison. The mod never launches this script or the CLI."""
import argparse
import ctypes
import json
import os
import sys
import time
import wave
from pathlib import Path
import numpy as np
from faster_whisper.vad import VadOptions, collect_chunks, get_speech_timestamps

sys.stdout.reconfigure(encoding='utf-8')
parser = argparse.ArgumentParser()
parser.add_argument('--runtime', type=Path, default=Path('build/vulkan-runtime'))
parser.add_argument('--model', default='ggml-base.bin')
parser.add_argument('--cpu', action='store_true')
parser.add_argument('--repeats', type=int, default=2)
parser.add_argument('--clips', nargs='+')
parser.add_argument('--output', type=Path, default=Path('build/vulkan-benchmark.json'))
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
runtime = args.runtime.resolve()
dll_directory = os.add_dll_directory(str(runtime)) if os.name == 'nt' else None
library = ctypes.CDLL(str(runtime / 'sayitem-vulkan.dll'))
check_type = ctypes.CFUNCTYPE(ctypes.c_int, ctypes.c_void_p)
library.sayitem_vk_version.restype = ctypes.c_char_p
library.sayitem_vk_device.restype = ctypes.c_char_p
library.sayitem_vk_load.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
library.sayitem_vk_load.restype = ctypes.c_void_p
library.sayitem_vk_free.argtypes = [ctypes.c_void_p]
library.sayitem_vk_transcribe.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_float), ctypes.c_int,
    ctypes.c_char_p, ctypes.c_int, ctypes.c_void_p, ctypes.c_int, check_type, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_int]
library.sayitem_vk_transcribe.restype = ctypes.c_int
print('Engine:', library.sayitem_vk_version().decode(), flush=True)
device = library.sayitem_vk_device().decode()
print('Vulkan GPU:', device or '(none)', flush=True)
if not args.cpu and not device: raise RuntimeError('No compatible Vulkan GPU; this test must not silently fall back to CPU')
error = ctypes.create_string_buffer(2048)
started = time.perf_counter()
context = library.sayitem_vk_load(str(root / 'build/gpu-models' / args.model).encode(), int(not args.cpu), 3, error, len(error))
if not context: raise RuntimeError(error.value.decode())
print('Model load:', round((time.perf_counter()-started)*1000), 'ms', flush=True)
cases = [('diamond-sword', 'en')] + [('pl-' + word, 'pl') for word in
    ('stone', 'cobblestone', 'diamond-sword', 'oak-planks', 'iron-pickaxe', 'apple', 'unrelated')]
cases += [(language + '-' + word, language) for language in ('bg', 'ru')
          for word in ('stone', 'apple', 'diamond-sword', 'iron-pickaxe', 'unrelated')]
if args.clips: cases = [case for case in cases if case[0] in args.clips]
cancelled = check_type(lambda _: 0)
rows = []
try:
    for name, language in cases:
        with wave.open(str(root / f'src/test/resources/audio/{name}.wav'), 'rb') as audio:
            samples = np.frombuffer(audio.readframes(audio.getnframes()), dtype='<i2').astype(np.float32) / 32768
        samples -= np.mean(samples, dtype=np.float64)
        peak = float(np.max(np.abs(samples))); rms = float(np.sqrt(np.mean(samples*samples, dtype=np.float64)))
        samples *= np.float32(min(.95/peak, max(1, min(8, .04/rms))))
        chunks = get_speech_timestamps(samples, VadOptions(min_silence_duration_ms=2000, speech_pad_ms=400))
        speech, _ = collect_chunks(samples, chunks)
        samples = np.ascontiguousarray(np.concatenate(speech), dtype=np.float32)
        for repeat in range(args.repeats):
            text = ctypes.create_string_buffer(2048)
            started = time.perf_counter()
            count = library.sayitem_vk_transcribe(context, samples.ctypes.data_as(ctypes.POINTER(ctypes.c_float)),
                len(samples), language.encode(), 5, text, len(text), cancelled, None, error, len(error))
            elapsed = round((time.perf_counter()-started)*1000)
            if count < 0: raise RuntimeError(error.value.decode())
            row = dict(clip=name, language=language, gpu=not args.cpu, device=device, model=args.model,
                       repeat=repeat, text=text.value.decode().strip(), total_ms=elapsed)
            rows.append(row); print(json.dumps(row, ensure_ascii=False), flush=True)
            args.output.write_text(json.dumps(rows, indent=2, ensure_ascii=False), encoding='utf-8')
finally: library.sayitem_vk_free(context)
