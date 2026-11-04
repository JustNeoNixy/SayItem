# SayItem

Say an item's name in another language to unlock it. Inspired by custom mods in
[this video](https://www.youtube.com/watch?v=nv5q454A7zA) and
[this one](https://www.youtube.com/watch?v=AJmBKnxSh2U).

## Play

1. Install **Fabric Loader** and **Fabric API** for Minecraft **26.1.2, 26.2, or 26.3**.
2. Add the matching **SayItem jar** to `mods/`.
3. First launch downloads and checks the local speech runtime and multilingual model
   into `.sayitem/speech/`. Later launches work offline. No Python or companion app.
4. Collect an item, **hold V**, say the HUD text, then release. **K** opens settings.

Each locked item type gets **15 seconds** from entering your inventory. A correct
answer unlocks it for the run; timeout removes all copies of that type from your
inventory. Collect it again to retry. Unlocks survive reconnects and reset when the
world/server restarts. Creative and Spectator bypass locks.

The required text match defaults to **90%**. The world owner or server operators
can choose **60–100%** in K settings; everyone on the server uses that requirement.
It measures recognized text, not accent. Short words can be harder for speech models.

All **1,657 item types** across the supported versions are covered. Japanese includes
romaji; English, Spanish, French, German, and Polish are included. Audio is processed
inside Minecraft and is never uploaded. Recognition uses the original faster-whisper
**CTranslate2 INT8** engine, with speech filtering and CPU feature detection.
The small mod jar contains no other mods, model weights, or native engines.

## Packs and servers

Add UTF-8 JSON to `config/sayitem/languages/` and restart the world/server. Example:

```json
{"schemaVersion":1,"id":"it","name":"Italiano","whisperLanguage":"it","items":{"minecraft:apple":{"text":"mela","reading":"","aliases":["una mela"]}}}
```

Missing names use a marked English fallback. Install SayItem, Fabric API, and the
packs on multiplayer servers too. Only clients load speech. This is a cooperative
challenge, not cheat protection.

Build with Java 25: `./gradlew build verifySingleJar`; use
`-Pminecraft_version=26.2` or `26.3` for another target. Speech data is downloaded
once; only your operating system's runtime is cached. Runtime sources/checksums are
in `native/ct2/` and the jar's manifests. Licenses ship in `licenses/` inside the jar;
recorded test-audio attribution is in `src/test/resources/audio/pl-fixtures.json`.
SayItem is independent of Mojang and the video creators.
