# Contributing to SayItem

Bug fixes, translations, pronunciation corrections, and speech improvements are welcome.
Check existing issues and pull requests first. For a large change, open an issue to
discuss the approach before spending time on it.

## Setup and checks

Fork the repository, clone your fork, and use **JDK 25** with the included Gradle wrapper.
The default Minecraft target is 26.1.2. Build and check the distribution jar with:

```sh
./gradlew build verifySingleJar
```

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.
The first run downloads dependencies, the speech runtime, and model files.
Jars are written to `build/libs/`.

Run the relevant tests for your change. Changes to shared code or Minecraft APIs
should also pass `build verifySingleJar` with `-Pminecraft_version=26.2` and
`-Pminecraft_version=26.3`. Optional GPU tests use `-Pgpu_tests=true` and require
Windows x64, a working Vulkan driver, and additional model downloads.
State which versions, profiles, and manual checks you actually tested.

## Languages and speech

- Built-in packs live in `src/main/resources/languages/`. Keep existing item IDs
  and cover the vanilla item types across all supported versions.
- Use `text` for the native item name, `reading` for a pronunciation guide, and
  `aliases` for valid alternative answers. Readings are display-only.
- Add legitimate spelling or naming variants, not recognition mistakes. Keep the
  configured matching requirement intact. Check pronunciation with native speakers
  or a cited source, including word boundaries and compound words.
- New languages also need registration in `BuiltInLanguages.java` and updates to
  generation tools where applicable. Use a Whisper-supported language code and
  add relevant pack or pronunciation tests.
- For speech changes, record the expected text, actual transcript, model/profile,
  hardware, and processing time. Separate synthetic and human recordings. Include
  source links, attribution, and licenses for audio fixtures.

## Pull requests

Create a branch in your fork and open a pull request against `main`. Keep it focused;
explain the problem, resulting behavior, and checks run using the PR template.
Link related issues and add screenshots for visible changes when possible.

Keep model weights, personal recordings, logs, local configuration, and generated
build files out of commits. Preserve contributor credits and third-party licenses.
Fabric API stays an external dependency; the mod jar must not bundle other mods.

Use the [issue forms](https://github.com/JustNeoNixy/SayItem/issues/new/choose)
to report bugs, speech problems, or feature requests.
