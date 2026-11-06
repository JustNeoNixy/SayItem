# SayItem

Say an item's name in another language to unlock it. Inspired by custom mods in
[this video](https://www.youtube.com/watch?v=nv5q454A7zA) and
[this one](https://www.youtube.com/watch?v=AJmBKnxSh2U).

## Play

1. Install Fabric Loader and Fabric API for Minecraft 26.1.2, 26.2, or 26.3.
2. Add the matching SayItem jar to `mods/`.
3. Joining your first world downloads and checks the local speech runtime and model
   into `.sayitem/speech/`. Later launches work offline. No Python or companion app.
4. Collect an item, **hold V**, say the HUD text, then release. **K** opens settings.

Locked item types enter a queue when collected. Only the first item's timer runs;
each waiting type gets its full 15 seconds by default when it reaches the front.
A correct answer unlocks it for that world; timeout removes all copies of that type
from your inventory. Collect it again to retry. Creative and Spectator bypass locks.
The bottom-left HUD counter and K settings show unlocked / total item types.

The required text match defaults to 90%. The world owner or server operators
can choose 60 to 100% and a 10 to 30 second timer in K settings. Timer changes apply
to newly collected locked types; active deadlines stay fixed. Server settings are saved.
The score compares recognized text with the item name. Short words can be harder to recognize.

Owners/operators can turn Sharing on to give every player the same unlocks,
including existing progress. Turning it off keeps unlocks already received.
Progress and sharing are saved in the world's `data/sayitem-unlocks.json`;
reconnecting and restarting preserve unlocks. Include this file in world backups.

## Speech

CPU Base is the default. On Windows x64, K settings also offer GPU Base
and the larger GPU Small model through Vulkan (AMD/NVIDIA/Intel, compatible
driver required). Choose a profile and rejoin the world to apply it. The GPU
warms up before challenges start; unavailable GPUs fall back to CPU Base.
Audio stays inside Minecraft and is never uploaded. Models/libraries download once
and work offline afterward; the small mod jar contains no other mods or model weights.

All 1,657 item types across the supported versions are covered in 26 built-in languages:
English, Spanish, French, German, Italian, Portuguese, Dutch, Polish, Czech, Slovak,
Ukrainian, Russian, Bulgarian, Swedish, Danish, Norwegian, Finnish, Greek, Turkish,
Hungarian, Romanian, Japanese, Korean, Chinese, Indonesian, and Vietnamese.
Readings in K settings shows approximate Latin-letter guides below item names.
Japanese includes romaji. Custom packs can set `reading`; blank readings are converted
automatically where supported. Recognition varies by language and item name.
CPU speech uses CTranslate2 INT8; GPU speech uses whisper.cpp. All models are multilingual.

Open K > Language to choose a language or Random per item. Random mode chooses once
per item challenge and keeps that choice for retries. Match Minecraft language also
switches menus and item names to that language. Timers pause during resource reloads.
Turning it off or leaving the world restores your previous Minecraft language.

## Packs and servers

Add UTF-8 JSON to `config/sayitem/languages/` and restart the world/server. Example:

```json
{"schemaVersion":1,"id":"it","name":"Italiano","whisperLanguage":"it","items":{"minecraft:apple":{"text":"mela","reading":"","aliases":["una mela"]}}}
```

Missing names use a marked English fallback. Install SayItem, Fabric API, and the
packs on multiplayer servers too; update clients and server together. Only clients
load speech. SayItem relies on players reporting speech results honestly.

Build with Java 25: `./gradlew build verifySingleJar`; use
`-Pminecraft_version=26.2` or `26.3` for another target. Speech data is downloaded
once; only your operating system's runtime is cached. Runtime sources/checksums are
in `native/` and the jar's manifests. Licenses ship with the jar/runtime;
test-audio attribution is in `src/test/resources/audio/*-fixtures.json`.
SayItem is independent of Mojang and the video creators.
