"""SayItem companion: automatic local connection and an in-memory transcript log."""
from __future__ import annotations
import argparse
import io
import json
import os
import sys
import threading
import time
from datetime import datetime
from http.server import ThreadingHTTPServer
from pathlib import Path
from PySide6.QtCore import QObject, Signal, QTimer, Qt
from PySide6.QtGui import QFont
from PySide6.QtWidgets import (QApplication, QWidget, QVBoxLayout, QHBoxLayout, QLabel,
                              QComboBox, QPlainTextEdit, QPushButton)
from server import make_handler

LANGUAGES = {"ja": "Japanese", "en": "English", "es": "Spanish", "fr": "French", "de": "German", "pl": "Polish"}


def resource_root() -> Path:
    return Path(sys._MEIPASS) if getattr(sys, "frozen", False) else Path(__file__).resolve().parents[1]


class Events(QObject):
    game = Signal(dict)
    transcript = Signal(str, str)
    engine = Signal(str)


class Engine:
    def __init__(self):
        self.model = None

    def transcribe(self, *args, **kwargs):
        if self.model is None:
            raise ValueError("Whisper is still loading. Wait for Ready in SayItem.")
        return self.model.transcribe(*args, **kwargs)


class SayItemWindow(QWidget):
    def __init__(self, port=8765, start=True):
        super().__init__()
        self.events = Events()
        self.engine = Engine()
        self.server = None
        self.language_choice = "ja"
        self.available = dict(LANGUAGES)
        self.last_game = 0.0
        self.last_result = ""
        self.setWindowTitle("SayItem")
        self.resize(600, 430)
        self.setMinimumSize(460, 320)
        self.setFont(QFont("Segoe UI", 10))
        layout = QVBoxLayout(self)
        layout.setContentsMargins(24, 20, 24, 18)
        layout.setSpacing(12)
        header = QHBoxLayout()
        title = QLabel("SayItem")
        title.setFont(QFont("Segoe UI", 18, QFont.Weight.DemiBold))
        header.addWidget(title)
        header.addStretch()
        language_label = QLabel("&Language")
        self.language = QComboBox()
        self.language.setAccessibleName("Challenge language")
        self.language.setMinimumWidth(150)
        for id, name in self.available.items():
            self.language.addItem(name, id)
        language_label.setBuddy(self.language)
        header.addWidget(language_label)
        header.addWidget(self.language)
        self.language.currentIndexChanged.connect(self.choose_language)
        layout.addLayout(header)
        self.connection = QLabel("Waiting for Minecraft…")
        self.connection.setWordWrap(True)
        self.connection.setAccessibleName("Minecraft connection status")
        layout.addWidget(self.connection)
        self.engine_label = QLabel("Starting local Whisper…")
        self.engine_label.setWordWrap(True)
        self.engine_label.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        layout.addWidget(self.engine_label)
        log_header = QHBoxLayout()
        log_header.addWidget(QLabel("Whisper heard"))
        log_header.addStretch()
        clear = QPushButton("Clear")
        clear.setAccessibleName("Clear transcript log")
        log_header.addWidget(clear)
        layout.addLayout(log_header)
        self.log = QPlainTextEdit()
        self.log.setReadOnly(True)
        self.log.setFont(QFont("Segoe UI", 11))
        self.log.setMaximumBlockCount(300)
        self.log.setPlaceholderText("Recognized speech will appear here. Hold V in Minecraft to speak.")
        self.log.setAccessibleName("Recognized speech and answer results")
        layout.addWidget(self.log, 1)
        clear.clicked.connect(self.log.clear)
        footer = QLabel("Audio stays on this computer. Challenges appear in Minecraft.")
        footer.setWordWrap(True)
        footer.setStyleSheet("color: #4b5563;")
        layout.addWidget(footer)
        self.events.game.connect(self.show_game)
        self.events.transcript.connect(self.show_transcript)
        self.events.engine.connect(self.engine_label.setText)
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.check_connection)
        self.timer.start(500)
        if start:
            self.start_service(port)

    def choose_language(self, index):
        if index >= 0:
            self.language_choice = str(self.language.itemData(index))

    def on_game(self, body):
        self.events.game.emit(body)
        available = body.get("languages", {})
        language = self.language_choice
        if available and language not in available:
            language = body.get("selectedLanguage", "ja")
        return {"language": language}

    def show_game(self, body):
        self.last_game = time.monotonic()
        version = str(body.get("version", ""))[:32]
        self.connection.setText(f"Connected to Minecraft {version}" + (" · in world" if body.get("inWorld") else " · main menu"))
        languages = body.get("languages")
        if isinstance(languages, dict) and languages and languages != self.available:
            desired = self.language_choice
            self.available = {str(key): str(value) for key, value in languages.items()}
            self.language.blockSignals(True)
            self.language.clear()
            for id, name in sorted(self.available.items()):
                self.language.addItem(name, id)
            index = self.language.findData(desired)
            if index < 0:
                index = max(0, self.language.findData(body.get("selectedLanguage", "ja")))
            self.language.setCurrentIndex(index)
            self.language.blockSignals(False)
            self.choose_language(index)
        result = str(body.get("result", ""))
        if result and result != self.last_result and ("match" in result.lower() or "time's up" in result.lower()):
            self.log.appendPlainText(f"{datetime.now():%H:%M:%S}  {result}")
        self.last_result = result

    def show_transcript(self, text, language):
        self.log.appendPlainText(f"{datetime.now():%H:%M:%S}  [{language}]  {text or '(no speech detected)'}")

    def check_connection(self):
        if not self.last_game or time.monotonic() - self.last_game > 5:
            self.connection.setText("Waiting for Minecraft… open the game with SayItem installed.")

    def start_service(self, port):
        try:
            self.server = ThreadingHTTPServer(("127.0.0.1", port), make_handler(self.engine,
                on_game=self.on_game, on_transcript=self.events.transcript.emit))
            self.server.daemon_threads = True
            threading.Thread(target=self.server.serve_forever, daemon=True).start()
            threading.Thread(target=self.load_engine, daemon=True).start()
        except OSError:
            self.engine_label.setText("Could not connect: another SayItem app may already be running. Close it and reopen this app.")

    def load_engine(self):
        try:
            from faster_whisper import WhisperModel
            bundled = resource_root() / "models/base"
            if bundled.is_dir():
                self.engine.model = WhisperModel(str(bundled), device="cpu", compute_type="int8", local_files_only=True, cpu_threads=4)
            else:
                self.events.engine.emit("Loading model (downloads once for source builds)…")
                self.engine.model = WhisperModel("base", device="cpu", compute_type="int8",
                    download_root=str(resource_root() / "speech/models"), cpu_threads=4)
            self.events.engine.emit("Whisper ready · recognition runs locally")
        except Exception as exc:
            self.events.engine.emit(f"Whisper could not start: {exc}")

    def closeEvent(self, event):
        if self.server:
            self.server.shutdown()
            self.server.server_close()
        event.accept()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    if args.self_test:
        os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
    app = QApplication([sys.argv[0]])
    app.setApplicationName("SayItem")
    app.setStyle("Fusion")
    if args.self_test:
        window = SayItemWindow(start=False)
        from faster_whisper import WhisperModel
        model = WhisperModel(str(resource_root() / "models/base"), device="cpu", compute_type="int8", local_files_only=True)
        import wave
        audio = io.BytesIO()
        with wave.open(audio, "wb") as wav:
            wav.setnchannels(1); wav.setsampwidth(2); wav.setframerate(16000); wav.writeframes(b"\x00\x00" * 16000)
        audio.seek(0)
        segments, _ = model.transcribe(audio, language="ja", vad_filter=True)
        assert not list(segments)
        window.events.game.emit({"version": "26.3", "inWorld": True, "languages": {"ja": "Japanese"}})
        window.events.transcript.emit("synthetic test", "en")
        app.processEvents()
        assert "Connected" in window.connection.text() and "synthetic test" in window.log.toPlainText()
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps({"ok": True, "gui": "Qt", "local_inference": True}), encoding="utf-8")
        window.close()
        return
    if getattr(sys, "frozen", False):
        sys.stdout = sys.stderr = io.StringIO()
    window = SayItemWindow()
    window.show()
    sys.exit(app.exec())


if __name__ == "__main__":
    main()
