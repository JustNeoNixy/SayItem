"""Protocol tests use a stub model; no microphone, internet, or ML dependencies."""
import http.client
import io
import json
import threading
import unittest
import wave
from types import SimpleNamespace
from http.server import ThreadingHTTPServer
from server import make_handler, validate_wav
from server import Connections
from unittest.mock import patch


def wav_data(rate=16000, frames=8000):
    out = io.BytesIO()
    with wave.open(out, "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(rate)
        audio.writeframes(b"\x00\x00" * frames)
    return out.getvalue()


class Model:
    def __init__(self):
        self.calls = []

    def transcribe(self, audio, **kwargs):
        self.calls.append(kwargs)
        return iter([
            SimpleNamespace(text="りんご", no_speech_prob=0.01, avg_logprob=-0.1),
            SimpleNamespace(text="hallucination", no_speech_prob=0.99, avg_logprob=-0.1),
        ]), None


class SpeechTests(unittest.TestCase):
    def setUp(self):
        self.model = Model()
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(self.model, "test-token"))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def request(self, path, body=None, headers=None, method="POST"):
        connection = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=5)
        request_headers = {"Authorization": "Bearer test-token", "Content-Type": "audio/wav"}
        request_headers.update(headers or {})
        connection.request(method, path, body, request_headers)
        response = connection.getresponse()
        result = response.status, json.loads(response.read())
        connection.close()
        return result

    def test_health_and_auth(self):
        self.assertEqual(self.request("/health", method="GET")[0], 200)
        self.assertEqual(self.request("/health", headers={"Authorization": "wrong"}, method="GET")[0], 401)
        self.assertEqual(self.request("/health", headers={"Origin": "https://example.com"}, method="GET")[0], 403)

    def test_transcribes_in_requested_language_and_filters_no_speech(self):
        status, result = self.request("/transcribe?language=ja", wav_data())
        self.assertEqual((status, result["text"]), (200, "りんご"))
        self.assertEqual(self.model.calls[0]["language"], "ja")
        self.assertEqual(self.model.calls[0]["task"], "transcribe")
        self.assertNotIn("initial_prompt", self.model.calls[0])

    def test_rejects_bad_audio_before_inference(self):
        self.assertEqual(self.request("/transcribe?language=ja", b"not wav")[0], 400)
        self.assertEqual(self.request("/transcribe?language=ja", wav_data(rate=8000))[0], 400)
        self.assertEqual(self.request("/transcribe?language=ja", wav_data(frames=100))[0], 400)
        self.assertEqual(self.model.calls, [])

    def test_bounds_body_and_requires_language(self):
        self.assertEqual(self.request("/transcribe", wav_data())[0], 400)
        self.assertEqual(self.request("/transcribe?language=ja", b"x" * 256001)[0], 413)
        self.assertEqual(self.request("/transcribe?language=ja", wav_data(), {"Content-Type": "text/plain"})[0], 415)
        self.assertEqual(self.model.calls, [])

    def test_validates_truncation_and_duration(self):
        validate_wav(wav_data())
        with self.assertRaises(ValueError):
            validate_wav(wav_data()[:-10])
        with self.assertRaises(ValueError):
            validate_wav(wav_data(frames=16000 * 7))

    def test_authenticated_game_detection_returns_selected_language(self):
        status, result = self.request("/game", json.dumps({"version": "26.3", "inWorld": True}), {"Content-Type": "application/json"})
        self.assertEqual((status, result["language"]), (200, "ja"))
        self.assertEqual(self.request("/game", "[]", {"Content-Type": "application/json"})[0], 400)


class AutomaticConnectionTests(unittest.TestCase):
    def test_automatic_pairing_requires_no_shared_file_and_blocks_browser_origins(self):
        model = Model()
        server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(model,
            on_game=lambda body: {"language": "it"}))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        token = "a" * 64
        def send(path, body, content_type="application/json", extra=None):
            connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=5)
            headers = {"Authorization": "Bearer " + token, "Content-Type": content_type}
            headers.update(extra or {})
            connection.request("POST", path, body, headers)
            response = connection.getresponse()
            result = response.status, json.loads(response.read())
            connection.close()
            return result
        try:
            body = json.dumps({"protocol": 2, "version": "26.3", "inWorld": True})
            self.assertEqual(send("/game", body)[0], 401)
            self.assertEqual(send("/hello", body, extra={"Origin": "https://example.com"})[0], 403)
            self.assertEqual(send("/hello", body, extra={"Host": "example.com"})[0], 403)
            status, response = send("/hello", body)
            self.assertEqual((status, response["language"]), (200, "it"))
            self.assertEqual(send("/game", body)[0], 200)
            self.assertEqual(send("/transcribe?language=ja", wav_data(), "audio/wav")[1]["text"], "りんご")
        finally:
            server.shutdown(); server.server_close(); thread.join()

    def test_stale_connection_expires(self):
        clients = Connections()
        with patch("server.time.monotonic", return_value=10):
            self.assertTrue(clients.pair("b" * 64))
            self.assertTrue(clients.accepts("b" * 64))
        with patch("server.time.monotonic", return_value=26):
            self.assertFalse(clients.accepts("b" * 64))


if __name__ == "__main__":
    unittest.main()
