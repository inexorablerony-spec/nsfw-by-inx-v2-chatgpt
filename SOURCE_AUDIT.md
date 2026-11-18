# Full source audit — Goonhub by INX

Audit target: the uploaded `nsfw-by-inx-v2-chatgpt-main.zip` source tree.

## What this project currently is

- Android application module: `app`.
- Application ID: `io.github.inx.goonhub`.
- Launcher label: `Goonhub by INX`.
- The app has a provider-first Home route (`UnifiedHomeScreen`) and the root tab resolver currently exposes only `FlowTab.Home`.
- `ProviderRegistry` currently registers exactly two providers: `EpornerProvider` and `WebViewProvider`.
- The provider player calls `EnhancedPlayerManager.playExternalStream(...)`; that method explicitly reuses the existing player and does not create another ExoPlayer.
- ABI filtering is `arm64-v8a` only. The universal APK setting does not add other CPU architectures.

## Changes made in this audit package

1. `UnifiedHomeScreen.kt`
   - Added a provider-wide search field and Search action.
   - Added Clear search and Refresh actions.
   - Added page-based Load more, preserving prior results and de-duplicating provider/link pairs.
   - Kept the direct website URL entry for the WebView extractor.
   - Kept provider playback routed through the existing `ProviderVideoPlayer` and `EnhancedPlayerManager`.
2. `AndroidManifest.xml`
   - Removed YouTube, YouTube Music, Shorts, youtu.be, youtube-nocookie, and Piped URL intent filters so Android no longer offers this app as a handler for those services' links.
   - Removed the separately declared Media3 Music Service, the music-only MediaButtonReceiver resumption declaration, and the music lock-screen activity declaration.
   - Removed microphone/record-audio and Android 13+ audio-library permissions that were only declared for music recognition/local audio use.
   - Kept the video playback service, internet/network permissions, video permissions, camera permission for device sync, and the app's other non-music functionality.

## Critical blockers found — not represented as fixed

### 1. The checked-in source tree is incomplete

- `data/newmusic` contains only `MusicArtistLookup.kt`; `InnertubeMusicService` is missing even though production classes import and call it (`DlnaMusicDriver`, `LastFmDiscovery`, and `FavouriteArtistPicker`).
- `data/lyrics` contains only a placeholder `LyricsHelper.kt`. The production code and test tree refer to missing lyric models, parsers, provider/cache classes, and timing helpers, including `LyricsEntry`, `WordTimestamp`, `LyricsUtils`, `LyricsChecks`, `InFlightRuns`, `NonMusicPart`, `onVideoClock`, and `MusicVideoLyricsTiming`.
- `MusicArtistLookup` uses a suspend function as an injected constructor dependency without a production Hilt binding. Its tests expect lambda-based construction, but production DI does not provide that function.
- These are source-level compilation/DI risks. The ZIP cannot honestly be described as a verified build until they are repaired or the dependent music/lyrics code is removed cleanly.
- Legacy source classes and widgets still reference `Media3MusicService`; those dormant callers need to be removed or redirected before a release. The manifest no longer exposes the service, so an old music widget/action may not work.

### 2. YouTube and music code still exists internally

The user-facing root navigation is provider-only, and this package removes the Android link-capture entry points and music service declaration. However, the project still contains substantial legacy Flow/YouTube code (InnerTube, NewPipe, subscriptions, music, Shorts, lyrics, YouTube-oriented player support, and related settings/screens). It has not been deleted wholesale because these modules are referenced throughout the project; deleting them without replacing shared types and DI bindings would create more compile failures.

This is therefore a provider-first app-shell cleanup, not a claim that every YouTube symbol has been removed from the source tree. A complete source-level excision is a separate refactor requiring removal of the dependent screens, settings, services, data models, tests, and DI bindings together.

### 3. WARP is not production-ready

- No `libusque.so` or other native library is present under `app/src/main`.
- `WarpProxyManager.registerAccount()` reads the child process output to EOF before applying its 30-second `waitFor` timeout; if the process hangs without closing stdout, the timeout cannot take effect.
- The manager verifies a local WARP proxy but does not install/restore that proxy through `AppProxyManager`; the provider's `DefaultHttpDataSource` is also not wired to it. The current WARP switch therefore must not be treated as a verified app-wide proxy.
- WARP was not silently removed or represented as fixed in this package.

### 4. Provider playback limitations

- `ProviderVideoPlayer` currently selects the first stream returned by a provider. It does not expose a stream/quality picker.
- Provider metadata/details are not presented as a dedicated details screen.
- Eporner's current routes and page markup, and WebView extraction on live sites, require network/device testing. The parser smoke tests in the supplied repair report are not a substitute for that.
- WebView is a URL extractor, not a catalogue provider; it does not contribute cards or provider search results.

## Live repository cross-check

The uploaded source tree matches the current repository main snapshot at `31bbc29fc32d560031a53d82bf28dd58fbe361c5` for the missing-file checks. GitHub returned 404 for `data/newmusic/InnertubeMusicService.kt`, `data/lyrics/LyricsUtils.kt`, `data/lyrics/InFlightRuns.kt`, and `data/lyrics/MusicVideoLyricsTiming.kt`; the live `LyricsHelper.kt` is the same 3-line placeholder. This confirms these are not just files omitted accidentally by ZIP creation.

## Build and verification status

- Manifest parsed successfully as XML after edits.
- Static checks confirmed no YouTube/Piped URL hosts remain in manifest intent filters, no Media3 Music Service declaration remains, and no audio/microphone permissions remain.
- The provider search/pagination change was reviewed against the existing `UnifiedCatalogueRepository` API and `CatalogueResult` model.
- Full Gradle compilation, KSP, Hilt validation, unit tests, APK generation, install, and playback on Android were not run. This environment has no Android SDK configured, and the Gradle wrapper distribution download previously failed because the build host could not resolve `services.gradle.org`.

## Recommended next engineering step

Choose one coherent direction and complete it before calling this app release-ready:

- **Provider-only build:** remove legacy YouTube/Music/Shorts screens, services, data modules, settings, permissions, and DI bindings in dependency order; replace the missing music/lyrics pieces by removing all their consumers; then compile and run the provider tests.
- **Hybrid build:** restore the missing upstream Flow source files at a compatible pinned revision, keep legacy internals for compilation, but keep all YouTube/Music/Shorts entry points hidden and disabled.

The current package is a source snapshot with targeted provider and app-shell improvements. It is not a signed or verified APK.
