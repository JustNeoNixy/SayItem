# SayItem

Say an item's name in another language to unlock it. Inspired by custom mods in
[this video](https://www.youtube.com/watch?v=nv5q454A7zA) and
[this one](https://www.youtube.com/watch?v=AJmBKnxSh2U).

## Play

1. Install **Fabric Loader** for Minecraft **26.1.2, 26.2, or 26.3**.
2. Put the matching **SayItem jar** in `mods/`. That's the only mod you need to add:
   Fabric API, Whisper, and the multilingual speech model are bundled.
3. Launch Minecraft. Speech initializes automatically, entirely offline.
4. Play Survival. Collect an item, **hold V**, say the words on the HUD, then release.
   Press **K** for language, microphone, and speech status settings.

You have **15 seconds** to get a **90% text match**. Allow time for local recognition
after releasing V. Success unlocks that item type for the run. On timeout, all copies
of that locked type disappear from your inventory; collect it again to retry.
Each new type has its own timer. Unlocks reset when the world/server restarts;
reconnecting to the same running server keeps them. Creative and Spectator bypass locks.

All **1,657 item types** across the supported versions are covered. Japanese includes
romaji; English, Spanish, French, German, and Polish are also included.
The score compares recognized words, not your accent. Audio stays in memory inside
Minecraft: **no Python, companion app, server process, account, or API key**.
The model is extracted to Minecraft's `.sayitem` cache automatically on first launch.

## Custom languages

Add UTF-8 JSON packs to `config/sayitem/languages/`, then restart the world/server.
Missing translations use a marked English fallback. Minimal `it.json`:

```json
{"schemaVersion":1,"id":"it","name":"Italiano","whisperLanguage":"it","items":{"minecraft:apple":{"text":"mela","reading":"","aliases":["una mela"]}}}
```

On multiplayer, install SayItem and the packs on the server too. Only clients run speech.
This is a cooperative challenge, not cheat protection.

Build with Java 25: `./gradlew build verifySingleJar`; add `-Pminecraft_version=26.2`
or `26.3` for other targets. The build fetches a checksum-pinned model; players need no
downloads after installing the jar. Bundled native engines cover Windows x64, Linux
x64/ARM64, and macOS x64/ARM64. Native engine rebuild recipes and pinned
sources are in `native/`; licenses ship inside the jar. Minecraft translation source
hashes are in `tools/translation-sources.json`. SayItem is independent of Mojang and the video creators.
