# Copilot Instructions — VH CC Tweaks

## Language & Platform

- **Java 17** Forge mod for **Minecraft 1.18.2** (Forge 40.3.11)
- Targets CC:Tweaked 1.101.3 and optionally Advanced Peripherals 0.7.31r
- Build system: **Gradle 7.6.4** with ForgeGradle 5.1

## Project Structure

- `src/main/java/com/vhcctweaks/` — All mod source code
- `src/main/resources/` — Assets, mods.toml, mixins config
- `scripts/` — CraftTweaker `.zs` recipe scripts (copied to instance `scripts/`)
- `docs/` — API reference docs (Lua stubs + markdown)

## Release Process (Automated)

Releases are fully automated via GitHub Actions. To publish a new version:

1. **Update `gradle.properties`** — bump `mod_version` (e.g., `2.1.0` → `2.2.0`)
2. **Update `CHANGELOG.md`** — add a new `## [X.Y.Z] - YYYY-MM-DD` section at the top with changes
3. **Update `README.md`** — bump any version references (badge, installation section, build output)
4. **Commit** — `git commit -am "Release vX.Y.Z"`
5. **Tag** — `git tag vX.Y.Z`
6. **Push** — `git push && git push --tags`

The `release.yml` workflow will automatically:
- Build the JAR
- Extract the changelog section for that version
- Create a GitHub Release with the JAR attached

> **Important:** The tag must match `vX.Y.Z` format and the version in `gradle.properties` must match (without the `v` prefix). The CHANGELOG.md section header must be `## [X.Y.Z]` for release notes extraction to work.

### CI Builds

Every push/PR to `master` triggers `build.yml` which builds and uploads the JAR as a CI artifact (not a release).

## Version Checklist

When bumping the version, update ALL of these:
- `gradle.properties` → `mod_version`
- `CHANGELOG.md` → new version section
- `README.md` → version badge, installation JAR filename, build output line
- `src/main/resources/META-INF/mods.toml` → `version` field (if it references a hardcoded version)

## Build Dependencies

Compile-only dependencies (not bundled in the JAR):
- `cc-tweaked-1.18.2:1.101.3` — from SquidDev Maven
- `AdvancedPeripherals-1.18.2-0.7.31r.jar` — downloaded from Modrinth in CI
- `the_vault-1.18.2-3.20.3.6055.jar` — from local `../mods/` (not in CI, uses compileOnly)
- `curios-forge-1.18.2-5.0.9.2.jar` — from local `../mods/`
- `jei-1.18.2-9.7.2.1001.jar` — downloaded from Modrinth in CI

The Vault and Curios JARs are only needed for local development (mixin compile targets). CI can build without them since they're `compileOnly`.

## Local Deploy (Required)

After every build, **always** deploy the JAR to the Minecraft instance mods folder:

```
Copy-Item build\libs\vhcctweaks-*.jar "$env:APPDATA\PrismLauncher\instances\Vault Paradise\minecraft\mods\" -Force
```

The Minecraft instance is at: `%APPDATA%\PrismLauncher\instances\Vault Paradise\minecraft\`

This ensures the user is always testing with the latest build. Never skip this step after a successful build.

## Coding Conventions

- Mixins use string `targets` for optional mod classes (AP) so they no-op gracefully if the mod is absent
- All config patching is idempotent (marker comments prevent re-patching)
- Security-sensitive code (economy, auth, transfers) uses `synchronized` blocks and `SecureRandom`
- Lua API methods validate all inputs server-side; never trust client data
