import os
os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")
import unittest
from PySide6.QtWidgets import QApplication
from desktop import SayItemWindow


class DesktopTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = QApplication.instance() or QApplication([])

    def setUp(self):
        self.window = SayItemWindow(start=False)

    def tearDown(self):
        self.window.close()

    def test_live_connection_and_transcript_are_visible_without_any_folder(self):
        self.window.events.game.emit({"version": "26.3", "inWorld": True, "languages": {"ja": "Japanese"}})
        self.window.events.transcript.emit("りんご", "ja")
        self.app.processEvents()
        self.assertIn("Connected to Minecraft 26.3", self.window.connection.text())
        self.assertIn("りんご", self.window.log.toPlainText())
        self.assertEqual(self.window.language.currentData(), "ja")

    def test_custom_server_languages_are_selectable(self):
        self.window.show_game({"version": "26.2", "inWorld": True, "languages": {"it": "Italiano"}, "selectedLanguage": "it"})
        self.assertEqual(self.window.language.currentData(), "it")
        self.assertEqual(self.window.on_game({"languages": {"it": "Italiano"}})["language"], "it")

    def test_log_is_bounded_and_does_not_interpret_markup(self):
        for i in range(320):
            self.window.show_transcript(f"<item>{i}</item>", "en")
        self.assertLessEqual(self.window.log.document().blockCount(), 300)
        self.assertIn("<item>319</item>", self.window.log.toPlainText())
        self.window.last_game = 0
        self.window.check_connection()
        self.assertIn("Waiting", self.window.connection.text())


if __name__ == "__main__":
    unittest.main()
