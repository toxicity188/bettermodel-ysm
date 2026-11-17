# Project map

This standalone Java 25 plugin adapts encrypted YSM model assets to BetterModel 3.7.0-SNAPSHOT on Paper 26.3.

- `build.gradle.kts`: dependency scopes, resource-factory plugin descriptor, shaded deployment JAR, verification inputs.
- `BetterModelYsmPlugin`: registers the importer in `onLoad`, registers plugin ImageIO readers, releases readers on disable.
- `YsmImporter`: preserves editable conversions, isolates output identities, coordinates concurrent conversion, delegates to BBMODEL.
- `EncryptedFixture`: generates original encrypted YSM V1 data without third-party model redistribution.
- `YsmImporterTest`: executes the actual BetterModel parser and blueprint loading with only platform services substituted.
- `PluginJarTest`: validates converter-only deployment contents, generated library declarations, and conversion using parent-provided libraries.

Existing converted files always win. Reconvert only after the user moves the output away. Never infer permission to overwrite from a changed source timestamp.
SHA-256 input is the exact asset raw name plus a NUL separator and the model name. Moving or renaming sources changes identity.
Shade only YSM Converter without transitive dependencies. Use Minecraft Gson; declare zstd-jni and TwelveMonkeys through resource-factory Bukkit libraries. Keep the converter license resources.

The workspace has no Git repository, AGENTS.md or CodeGraph index. Follow the agent instructions supplied in the chat.
The connected projectmem server is pinned to C:\Users\angry and rejects this project's path. Project-specific memory writes could not be recorded there.

Verification: Gradle `build --offline` with `-PysmTestModel=<local Twe_moss.ysm>` passed all 11 tests, with no failures or skips.
The deployment JAR converts encrypted models using parent-provided Gson, ImageIO services and the native zstd codec. These libraries are not bundled or relocated.
A Paper server was not launched; in-game rendering and resource-pack delivery remain outside this verification.

