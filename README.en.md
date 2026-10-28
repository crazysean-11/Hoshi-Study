# Hoshi Study

An open-source Android Japanese reading and study app derived from [Hoshi Reader Android](https://github.com/HuangAntimony/Hoshi-Reader-Android), itself a native Android implementation of [Hoshi Reader](https://github.com/Manhhao/Hoshi-Reader).

[中文说明 and build instructions](README.md) · [License](LICENSE) · [Attribution](NOTICE.md) · [Privacy](PRIVACY.md)

## Features

- EPUB reading with vertical/horizontal text, pagination/scrolling, themes, highlights and statistics.
- Offline Yomitan dictionary lookup, including recursive lookup inside definitions. Supply your own permitted dictionaries.
- Selected-passage AI explanations: Chinese translations, grammar, vocabulary and exercises using a configurable HTTPS chat-completions API, including DeepSeek.
- Local word/sentence notebooks, multi-selection and combined review worksheets with answers collected at the end.
- One SMTP email for multiple selected notes, connection testing and optional daily review delivery.
- AnkiDroid/AnkiConnect integration, standard Basic field defaults and actionable add-result feedback.
- E-ink defaults and inherited audiobook, synchronization and backup capabilities.

## Public edition

All API keys, SMTP credentials and email addresses start empty. There is no credential-bundling build task. No personal APKs, signing keys, private build scripts, books or dictionary archives are distributed. The public application ID is `org.hoshistudy.reader`, separate from private builds.

This independent derivative retains the upstream GPLv3 license and attribution. Its new source history intentionally excludes private development commits and personal author metadata; upstream provenance and submodule revisions are documented in NOTICE.md.

Build requirements: JDK 21, Android SDK 36.1, NDK 29.0.14206865, CMake 3.31.6, Rust with aarch64-linux-android and cargo-ndk. Clone with `--recurse-submodules`, then run `./gradlew test lint assembleDebug` and `node --test app/src/test/js/*.test.mjs`. Configure your own signing credentials for release builds. See the Chinese README and `docs/VALIDATION.md` for details.

The preceding private development build passed 1946 JVM and 322 JavaScript tests; this is not a claim that this sanitized public edition has been rebuilt or tested on a physical reader. This public source passed 322 JavaScript tests and 19 study-model tests, including blank credential defaults. Full Android build verification remains pending; check CI and validation notes. Real Anki writes, account delivery and e-ink behavior require device validation.
