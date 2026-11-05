"""Development-only CC0 synthetic Bulgarian/Russian recordings, never a mod dependency."""
import io
import sys
import wave
from pathlib import Path
import av
from piper import PiperVoice

sys.stdout.reconfigure(encoding='utf-8')
root = Path(__file__).resolve().parents[1]
voices = {'bg': 'bg_BG-dimitar-medium', 'ru': 'ru_RU-dmitri-medium'}
phrases = {
    'bg': {'stone': 'Камък', 'apple': 'Ябълка', 'diamond-sword': 'Диамантен меч',
           'iron-pickaxe': 'Желязна кирка', 'unrelated': 'Днес времето е хубаво.'},
    'ru': {'stone': 'Камень', 'apple': 'Яблоко', 'diamond-sword': 'Алмазный меч',
           'iron-pickaxe': 'Железная кирка', 'unrelated': 'Сегодня хорошая погода.'}}
for language, voice_name in voices.items():
    voice = PiperVoice.load(root / f'build/cyrillic-voices/{voice_name}.onnx')
    for name, text in phrases[language].items():
        buffer = io.BytesIO()
        with wave.open(buffer, 'wb') as output: voice.synthesize_wav(text, output)
        buffer.seek(0)
        resampler = av.AudioResampler(format='s16', layout='mono', rate=16000)
        frames = []
        with av.open(buffer) as source:
            for frame in source.decode(audio=0):
                frames.extend(part.to_ndarray().tobytes() for part in resampler.resample(frame))
            frames.extend(part.to_ndarray().tobytes() for part in resampler.resample(None))
        target = root / f'src/test/resources/audio/{language}-{name}.wav'
        with wave.open(str(target), 'wb') as output:
            output.setnchannels(1); output.setsampwidth(2); output.setframerate(16000)
            output.writeframes(b''.join(frames))
        print(f'{target.name}: {text}')
