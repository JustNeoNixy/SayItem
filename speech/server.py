"""SayItem's local-only speech bridge. No audio files, uploads, or transcript logs."""
from __future__ import annotations

import hmac
import io
import json
import re
import threading
import time
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

MAX_AUDIO_BYTES = 256_000


class Connections:
    """Ephemeral same-machine pairing; no config files or Minecraft path discovery."""
    def __init__(self):
        self.clients = {}
        self.lock = threading.Lock()

    def pair(self, token):
        if not re.fullmatch(r"[a-zA-Z0-9-]{32,128}", token):
            return False
        with self.lock:
            now = time.monotonic()
            self.clients = {key: seen for key, seen in self.clients.items() if now - seen < 15}
            if len(self.clients) >= 16 and token not in self.clients:
                return False
            self.clients[token] = now
        return True

    def accepts(self, token):
        with self.lock:
            if token not in self.clients or time.monotonic() - self.clients[token] > 15:
                return False
            self.clients[token] = time.monotonic()
            return True


def validate_wav(data: bytes) -> None:
    try:
        with wave.open(io.BytesIO(data), "rb") as audio:
            if (audio.getnchannels(), audio.getsampwidth(), audio.getframerate(), audio.getcomptype()) != (1, 2, 16000, "NONE"):
                raise ValueError("Expected 16 kHz, mono, 16-bit PCM WAV audio.")
            if not 3200 <= audio.getnframes() <= 6 * 16000:
                raise ValueError("Record between 0.2 and 6 seconds of speech.")
            if len(audio.readframes(audio.getnframes())) != audio.getnframes() * 2:
                raise ValueError("Truncated WAV audio.")
    except (wave.Error, EOFError) as exc:
        raise ValueError("Invalid WAV audio.") from exc


def make_handler(model, token: str | None = None, on_game=None, on_transcript=None):
    inference_lock = threading.Lock()
    connections = Connections()

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def setup(self):
            super().setup()
            self.connection.settimeout(10)

        def log_message(self, *_):
            pass  # Never write transcripts, request parameters, or authorization headers.

        def reply(self, status: int, body: dict):
            payload = json.dumps(body, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(payload)))
            self.send_header("Cache-Control", "no-store")
            self.send_header("Connection", "close")
            self.end_headers()
            self.wfile.write(payload)
            self.close_connection = True

        def authorized(self) -> bool:
            # No CORS; browser-origin requests are rejected even with a valid token.
            if self.headers.get("Origin") is not None:
                self.reply(403, {"error": "Browser requests are not supported."})
                return False
            supplied = self.headers.get("Authorization", "")
            valid = hmac.compare_digest(supplied.encode(), ("Bearer " + token).encode()) if token else connections.accepts(supplied.removeprefix("Bearer "))
            if not valid:
                self.reply(401, {"error": "Connecting to SayItem… try again in a moment."})
                return False
            return True

        def do_GET(self):
            if not self.authorized():
                return
            if self.path != "/health":
                self.reply(404, {"error": "Unknown endpoint."})
                return
            self.reply(200, {"ready": True, "engine": "faster-whisper", "local": True})

        def do_POST(self):
            url = urlsplit(self.path)
            if url.path == "/hello" and token is None:
                if self.headers.get("Origin") is not None or self.headers.get("Host") != f"127.0.0.1:{self.server.server_port}":
                    self.reply(403, {"error": "Local Minecraft connections only."})
                    return
                try:
                    length = int(self.headers.get("Content-Length", "0"))
                    if not 0 < length <= 8192 or self.headers.get("Content-Type") != "application/json":
                        raise ValueError()
                    body = json.loads(self.rfile.read(length))
                    if not isinstance(body, dict) or body.get("protocol") != 2:
                        raise ValueError()
                    supplied = self.headers.get("Authorization", "")
                    if not supplied.startswith("Bearer ") or not connections.pair(supplied[7:]):
                        raise ValueError()
                    self.reply(200, on_game(body) if on_game else {"language": "ja"})
                except (ValueError, TypeError):
                    self.reply(400, {"error": "Invalid Minecraft connection."})
                return
            if not self.authorized():
                return
            if url.path == "/game":
                try:
                    length = int(self.headers.get("Content-Length", "0"))
                    if not 0 < length <= 8192 or self.headers.get("Content-Type") != "application/json":
                        raise ValueError("Invalid game heartbeat")
                    body = json.loads(self.rfile.read(length))
                    if not isinstance(body, dict):
                        raise ValueError("Invalid game heartbeat")
                    response = on_game(body) if on_game else {"language": "ja"}
                    self.reply(200, response)
                except (ValueError, TypeError):
                    self.reply(400, {"error": "Invalid game heartbeat."})
                return
            if url.path != "/transcribe":
                self.reply(404, {"error": "Unknown endpoint."})
                return
            language = parse_qs(url.query).get("language", [""])[0]
            if not re.fullmatch(r"[a-z]{2,3}", language):
                self.reply(400, {"error": "A Whisper language code is required."})
                return
            if self.headers.get("Transfer-Encoding") or self.headers.get("Content-Type") != "audio/wav":
                self.reply(415, {"error": "Send a WAV body with Content-Length and Content-Type: audio/wav."})
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                self.reply(400, {"error": "Invalid Content-Length."})
                return
            if length <= 0 or length > MAX_AUDIO_BYTES:
                self.reply(413, {"error": "Audio body is empty or too large."})
                return
            if not inference_lock.acquire(blocking=False):
                self.reply(429, {"error": "Speech engine busy. Try again in a moment."})
                return
            try:
                data = self.rfile.read(length)
                if len(data) != length:
                    raise ValueError("Incomplete audio body.")
                validate_wav(data)
                # Fix the requested language: short item names are poor auto-detection inputs.
                # Do not supply the expected answer as a prompt; that can bias recognition.
                segments, _ = model.transcribe(
                    io.BytesIO(data), language=language, task="transcribe", beam_size=5,
                    condition_on_previous_text=False, vad_filter=True,
                    without_timestamps=True,
                )
                text = " ".join(segment.text.strip() for segment in segments
                                if segment.no_speech_prob < 0.6 and segment.avg_logprob > -1.0).strip()
                if len(text) > 256:
                    raise ValueError("Say only the displayed item name.")
                if on_transcript:
                    on_transcript(text, language)
                self.reply(200, {"text": text, "language": language})
            except ValueError as exc:
                self.reply(400, {"error": str(exc)})
            except Exception as exc:
                print(f"Speech inference failed ({type(exc).__name__}).", flush=True)
                self.reply(500, {"error": "Local recognition failed. Check the speech service terminal."})
            finally:
                inference_lock.release()

    return Handler
