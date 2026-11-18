# Grok repair handoff — Flow provider integration

## Source snapshot and merge rules

- Repository: `inexorablerony-spec/Nsfw-by-inx`
- Uploaded source snapshot: commit `43e51b2146d25454920828745251821febab3649`
- This ZIP is a full source snapshot with a coordinated repair overlay. It does not contain Git history or credentials.
- First inspect the current repository HEAD. Do not reset, force-push, or overwrite newer work. Compare the current files with this package and apply only the intended changes, resolving conflicts against the newer source.
- `PROVIDER_REPAIR.patch` contains the source/test overlay relative to the snapshot. The full project tree is included for context.

## Changes already prepared in this package

1. Restored `EpornerProvider` compatibility with the full `Provider` interface and six-field `ProviderInfo` contract.
2. Restored UTF-8 search URL encoding and pagination URL construction.
3. Added HTTPS/host/port validation for Eporner page URLs and validation of every manually followed redirect.
4. Restored direct MP4/M3U8 stream handling, so direct media URLs are not fetched as HTML.
5. Hardened Eporner parsing, URL normalization, duplicate removal, preview filtering, and request headers.
6. Preserved HTTP connect/read timeouts, non-2xx errors, connection cleanup in `finally`, and cancellation propagation.
7. Reworked `UnifiedCatalogueRepository` to retain successful provider results when another provider fails, distinguish a true empty result from provider failures, deduplicate posts, and reject requests to missing/disabled providers.
8. Added provider capability flags so WebView is not mistaken for a catalogue/search provider.
9. Changed WebView extraction errors from silent empty stream lists into contextual errors; added URL validation and cancellation-safe timeout handling.
10. Added a visible URL-entry workflow for WebView in the existing unified provider catalogue screen.
11. Routed provider playback through the repository's enabled-provider checks while retaining Flow's existing `EnhancedPlayerManager` / Media3 player.
12. Updated legacy provider list/search screens and the settings screen to use shared registry state and guarded repository operations.
13. Added 27 focused provider/repository/WebView tests.

## Required work for Grok

### A. Verify and finish this provider patch

- Inspect every changed file and confirm it matches the current repository APIs, Compose version, Kotlin version, and existing app conventions.
- Run the real Gradle compile and test tasks on a machine with the required Gradle distribution, Android SDK, and dependency access. At minimum run the appropriate app compile task, `:app:testGithubDebugUnitTest` (or the actual test task for the configured variant), and lint if configured.
- Fix any compile/test failures. Do not report a build as successful unless the task actually ran and returned success.
- Test on an ARM64 Android device/emulator: load Eporner catalogue/search, open details/playback, enter a WebView URL, handle invalid URLs, handle extractor errors, disable a provider while a request is in progress, and navigate back to the catalogue.
- Verify Eporner's live site routes and markup. The parser intentionally supports known anchor patterns but must be validated against current pages; do not assume regex parsing is universally robust.
- Verify direct MP4/HLS requests preserve the headers required by the existing Flow playback pipeline.
- Inspect all call sites of `Provider.getMeta`, `getStream`, `getPosts`, and `getSearchPosts` to ensure no UI or background path bypasses enabled-provider checks or cancellation handling.

### B. Complete the provider user experience without redesigning Flow

- The current provider catalogue is still basic. Add proper provider metadata/details UI (title, artwork, synopsis, and any available structured metadata) using the existing Flow screen patterns.
- Add stream/server/quality choice when a provider returns multiple streams. Do not always select `streams.first()` without offering or applying a documented deterministic preference.
- Wire search, refresh, and pagination/load-more into the unified catalogue. Preserve partial provider successes and surface provider-specific failures without replacing the whole screen with an error.
- Keep the existing Flow visual identity, navigation graph, bottom navigation, settings structure, and `EnhancedPlayerManager` / Media3 player. Do not add a second player or a separate provider navigation host.
- Keep the provider list limited to the existing Eporner and WebView providers. Do not add other sites/providers.

### C. WARP is still an unresolved integration area — do not fake it

The uploaded source has `WarpProxyManager`, `AppProxyManager`, and `AppProxySelector`, but they are not yet integrated as one complete WARP route:

- `WarpProxyManager` expects `libusque.so`; no native binary or `jniLibs` payload is present in the uploaded project. The Gradle ABI filter is ARM64-only (`arm64-v8a`). Do not create a placeholder `.so` or claim WARP works without a real, reproducible, properly licensed ARM64 binary.
- `registerAccount()` reads the entire process output using `readText()` before calling timed `waitFor(30, ...)`; the blocking read can prevent the timeout from taking effect. Drain output concurrently and enforce a real bounded process wait/cleanup.
- On WARP start, the verified local proxy is not installed as the app's active proxy through `AppProxyManager`; on stop or process death, no corresponding proxy restoration is wired. Implement a safe temporary override that preserves/restores the user's existing proxy settings and evicts/rebuilds affected connection pools.
- Verify which app networking paths actually honor `AppProxyManager`. In particular, review provider `HttpURLConnection`, Media3 playback data sources, WebView, downloads, and long-lived OkHttp clients. Do not claim that all app traffic is routed unless each relevant path is confirmed.
- Add tests for process startup timeout, process death, proxy route activation, restoration of previous settings, and failed verification.
- Preserve the user's requested behavior: a real in-app ON/OFF app-level WARP proxy, not an external-app shortcut and not a fake preference. If the native dependency cannot be built/verified and included, report that blocker explicitly.

## Non-negotiable project constraints

- ARM64 only: `arm64-v8a`. Do not add other ABIs.
- Only Eporner and WebView providers for now.
- Keep Flow's existing player, UI identity, navigation, settings host, and bottom navigation.
- No second player and no competing NavHost.
- Do not make universal website compatibility promises for WebView extraction.
- Do not fabricate build, test, device, binary, or WARP verification results.

## Expected Grok response

Return:
1. The full commit SHA and changed-file list.
2. Exact Gradle commands executed and their exit results.
3. Test/device matrix with pass/fail/not-run status.
4. Any remaining blockers (especially native WARP binary and network routes).
5. Confirmation that only `arm64-v8a`, Eporner, and WebView remain in scope.
