# SayItem

Say an item's name in another language to unlock it in Minecraft.
Inspired by custom mods in [this video](https://www.youtube.com/watch?v=nv5q454A7zA)
and [this one](https://www.youtube.com/watch?v=AJmBKnxSh2U).

## Play

1. Install SayItem and Fabric API for Minecraft **26.1.2, 26.2, or 26.3**.
2. Extract the companion download and open **SayItem.exe**. Keep its `_internal` folder beside it.
3. Launch Minecraft. The app connects automatically—no folders or setup. Wait for **Whisper ready**.
4. In Survival, collect an item. The HUD shows its name and a **15-second** timer.
5. **Hold V**, say the words, then release. A **90% text match** unlocks that item type for the rest of the run.

Run out of time and that item type disappears from your inventory. Collect it
again to retry. Each new type has its own timer. Unlocks reset when the world/server
restarts. Creative and Spectator bypass challenges.

All **1,657 item types** across the supported versions are covered. Japanese includes
romaji; English, Spanish, French, German, and Polish are also included. Choose a language
in the companion, which shows connection status and what Whisper heard. Speech runs
locally with the bundled model—no Python installation, API key, or speech subscription.
The 90% score compares recognized text; it isn't an accent grade.

## Custom languages

Add UTF-8 JSON packs to `config/sayitem/languages/`, then restart the world/server.
Use any language supported by multilingual Whisper. Missing entries use a marked English
fallback, so no item skips the challenge. A minimal `it.json`:

```json
{"schemaVersion":1,"id":"it","name":"Italiano","whisperLanguage":"it","items":{"minecraft:apple":{"text":"mela","reading":"","aliases":["una mela"]}}}
```

On multiplayer, the mod and packs belong on the server too; each player runs the companion.
This is a cooperative challenge, not cheat protection. Logs stay in the app's memory.

To build: Java 25, `./gradlew build` (`-Pminecraft_version=26.2` or `26.3` for other targets).
For the companion: install `speech/requirements-build.txt`, download the multilingual
`base` model to `speech/models/`, then run `python tools/build_desktop.py` on Windows.
Minecraft translations retain Mojang/community attribution; source hashes are in
`tools/translation-sources.json`. SayItem is independent of Mojang and the video creators.
