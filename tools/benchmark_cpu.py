"""Development-only comparison of encoder lengths/beam sizes using the pinned CPU model."""
import argparse
import json
import sys
import time
import wave
from pathlib import Path
import ctranslate2
import numpy as np
from faster_whisper import WhisperModel
from faster_whisper.audio import pad_or_trim
from faster_whisper.vad import VadOptions, collect_chunks, get_speech_timestamps
sys.stdout.reconfigure(encoding='utf-8')

parser = argparse.ArgumentParser()
parser.add_argument('--frames', type=int, nargs='+', default=[3000, 1500, 1000, 600])
parser.add_argument('--beams', type=int, nargs='+', default=[5])
parser.add_argument('--repeats', type=int, default=1)
parser.add_argument('--clips', nargs='+')
parser.add_argument('--output', type=Path, default=Path('build/cpu-benchmark.json'))
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
model = WhisperModel(str(root / 'build/model-source/base'), device='cpu', compute_type='int8', cpu_threads=3, num_workers=1)
cases = [('diamond-sword', 'en')] + [('pl-' + word, 'pl') for word in
         ('stone', 'cobblestone', 'diamond-sword', 'oak-planks', 'iron-pickaxe', 'apple', 'unrelated')]
cases += [(language + '-' + word, language) for language in ('bg', 'ru')
          for word in ('stone', 'apple', 'diamond-sword', 'iron-pickaxe', 'unrelated')]
if args.clips: cases = [case for case in cases if case[0] in args.clips]
rows = []
for name, language in cases:
    with wave.open(str(root / f'src/test/resources/audio/{name}.wav'), 'rb') as audio:
        samples = np.frombuffer(audio.readframes(audio.getnframes()), dtype='<i2').astype(np.float32) / 32768
    samples -= np.mean(samples, dtype=np.float64)
    peak = float(np.max(np.abs(samples)))
    rms = float(np.sqrt(np.mean(samples * samples, dtype=np.float64)))
    samples *= np.float32(min(.95 / peak, max(1, min(8, .04 / rms))))
    options = VadOptions(min_silence_duration_ms=2000, speech_pad_ms=400)
    chunks = get_speech_timestamps(samples, options)
    speech, _ = collect_chunks(samples, chunks)
    speech = np.concatenate(speech).astype(np.float32)
    mel = model.feature_extractor(speech, padding=160)
    prefix = [model.hf_tokenizer.token_to_id(token) for token in
              ('<|startoftranscript|>', '<|' + language + '|>', '<|transcribe|>', '<|notimestamps|>')]
    for repeat in range(args.repeats):
        for frames in args.frames:
            if mel.shape[-1] > frames: continue
            features = ctranslate2.StorageView.from_array(np.ascontiguousarray(pad_or_trim(mel, frames)[None]))
            for beam in args.beams:
                started = time.perf_counter()
                encoded = model.model.encode(features)
                encoded_at = time.perf_counter()
                result = model.model.generate(encoded, [prefix], beam_size=beam, max_length=64,
                                              return_scores=True, return_no_speech_prob=True)[0]
                ended = time.perf_counter()
                text = model.hf_tokenizer.decode(result.sequences_ids[0], skip_special_tokens=True).strip()
                row = dict(clip=name, frames=frames, beam=beam, repeat=repeat, text=text,
                           encode_ms=round((encoded_at-started)*1000), total_ms=round((ended-started)*1000),
                           score=result.scores[0], no_speech=result.no_speech_prob)
                rows.append(row); print(json.dumps(row, ensure_ascii=False), flush=True)
                args.output.write_text(json.dumps(rows, indent=2, ensure_ascii=False), encoding='utf-8')
