"""Small whole-utterance Finnish FLEURS control set. Not item-name coverage.

Stream only until enough short utterances are found; do not download the 565 MB
archive or splice words from sentences. CC BY 4.0 attribution stays in the manifest.
"""
import csv
import hashlib
import io
import json
import tarfile
import urllib.request
from pathlib import Path
import av
import numpy as np
import wave

ROOT = Path(__file__).resolve().parents[1]
REVISION = '70bb2e84b976b7e960aa89f1c648e09c59f894dd'
BASE = f'https://huggingface.co/datasets/google/fleurs/resolve/{REVISION}/data/fi_fi/'
if __name__ == '__main__':
    with urllib.request.urlopen(BASE + 'test.tsv', timeout=45) as response: index = response.read()
    entries = {}
    for row in csv.reader(io.StringIO(index.decode()), delimiter='\t'):
        if 3200 <= int(row[5]) <= 96000 and len(row[2]) <= 128: entries[row[1]] = row
    folder = ROOT / 'build/human-speech/wav'; folder.mkdir(parents=True, exist_ok=True)
    records = []; transcripts = set()
    with urllib.request.urlopen(BASE + 'audio/test.tar.gz', timeout=60) as response, tarfile.open(fileobj=response, mode='r|gz') as archive:
        for member in archive:
            name = Path(member.name).name
            if not member.isfile() or name not in entries: continue
            row = entries[name]
            if row[2] in transcripts: continue
            payload = archive.extractfile(member).read()
            identifier = 'fi-fleurs-' + name.removesuffix('.wav')
            with av.open(io.BytesIO(payload)) as audio:
                resample = av.AudioResampler(format='s16', layout='mono', rate=16000); frames = []
                for frame in audio.decode(audio=0): frames.extend(resample.resample(frame))
                frames.extend(resample.resample(None))
                pcm = np.concatenate([f.to_ndarray().reshape(-1) for f in frames]).astype('<i2')
            if not 3200 <= len(pcm) <= 96000: continue
            output = folder / (identifier + '.wav')
            with wave.open(str(output), 'wb') as writer:
                writer.setnchannels(1); writer.setsampwidth(2); writer.setframerate(16000); writer.writeframes(pcm.tobytes())
            records.append(dict(id=identifier, language='fi', item=None, kind='general_speech', expected=row[2],
                partition='calibration' if len(records) < 4 else 'validation',
                wav=output.name, source='https://huggingface.co/datasets/google/fleurs',
                license='CC BY 4.0', licenseUrl='https://creativecommons.org/licenses/by/4.0/',
                artist='Google FLEURS contributors and dataset authors',
                containerUrl=BASE + 'audio/test.tar.gz', containerPath=member.name,
                sourceSha256=hashlib.sha256(payload).hexdigest(), revision=REVISION))
            transcripts.add(row[2]); print(identifier, len(pcm)/16000, flush=True)
            if len(records) == 6: break
    if len(records) != 6: raise RuntimeError('Insufficient whole Finnish utterances')
    (ROOT / 'tools/finnish-human-speech-sources.json').write_text(json.dumps(dict(
        purpose='General Finnish speech controls; no claim of isolated item-word accuracy.', cases=records),
        ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    main = ROOT / 'tools/human-speech-sources.json'; document = json.loads(main.read_text(encoding='utf-8'))
    document['supplementalManifest'] = 'finnish-human-speech-sources.json'
    main.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
