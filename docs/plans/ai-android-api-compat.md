# DeepSeek SDK Android collection API compatibility

## Confirmed defect

The official `prompt-executor-deepseek-client-android:1.3.0-beta` calls
`java.util.List.removeLast()` in its assistant-message merge branch. That method
requires Android API 35; this app supports API 26+. On the API 33 tablet, the
synthetic branch against installed Mock 126 returned `SDK_API_MISSING_MERGE`.
The fixture uses no credentials, network or business data. This is a distinct
latent compatibility defect, not a reclassification of the earlier HTTP 400.

## Minimal fix

Use the complete official `prompt-executor-deepseek-client-jvm:1.3.0-beta`
publication, without its transitive dependency graph, and explicitly retain the
existing Android OpenAI base 1.3.0 dependency. Exclude the Android DeepSeek module.
All 27 client/model/serializer classes move together; no bytecode patching.
Independent comparison found identical class sets and public client ABI; internal
serializer module suffixes differ. The sole differing external member reference
is Java List.removeLast versus Kotlin CollectionsKt.removeLast(List).

## Local evidence

- Mock 127 + test APK build successful; 61 host tests, zero failures/errors/skips.
- The forced merge branch also passes on Java 17 (which lacks Java 21 List.removeLast).
- Final main DEX has no API-35 List/ArrayList end-method or Sequenced collection
  references. SDK merge calls Kotlin CollectionsKt.removeLast(List).
- Runtime dependency graph contains only the JVM DeepSeek module, not Android.
- Version 127, `0.1.0-local.ai-api26-127`; existing debug signing certificate retained.
- Main SHA256: `9e34d8bc0a054edba71d9500ce970ca6baa387aad1d55d289d5df687a2a709fe`.
- Test SHA256: `ad6783bd98445086012b4300a3d063bfe1d6134a76bb155b6845cf0239d8121b`.

API 33 post-fix device verification remains pending at writing. No API 26 device
or emulator execution is claimed. No paid API calls are needed for this fixture;
no real Debug installation, user-data clearing or public repository actions.
