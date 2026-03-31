---
applyTo: '**'
---

# VH CC Tweaks — Coding Instructions

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

Releases are fully automated via GitHub Actions + a local PowerShell script.

### Formal Releases

Use the VS Code task **"Release New Version"** or run manually:

```powershell
.\.vscode\release.ps1
```

This single command handles everything:
1. Validates clean working tree and correct branch
2. Builds the JAR, auto-bumping `mod_version` in `gradle.properties` when tracked mod source changes require a new patch version
3. Freezes `CHANGELOG.md`'s `[Unreleased]` notes into the new versioned section
4. Updates version references in `README.md`
5. Deploys to local Minecraft instance
6. Commits, tags (`vX.Y.Z`), and pushes
7. Lets `release.yml` publish the GitHub Release from the pushed tag

The `release.yml` GitHub Action then automatically creates a GitHub Release with the JAR attached.

After any vhcctweaks repo change that should be kept, always finish by running this release flow. Build-only, deploy-only, and push-only workflows are incomplete unless the user explicitly says not to release yet.

Use `-DryRun` to preview changes without modifying anything.

### CI Builds

Every push/PR to `master` also triggers `build.yml` which:
1. Builds and uploads the JAR as a CI artifact
2. Updates the `latest` pre-release tag with the newest dev build JAR

## Version Checklist

When bumping the version, update ALL of these:
- `gradle.properties` → `mod_version`
- `CHANGELOG.md` → new version section
- `README.md` → version badge, installation JAR filename, build output line
- `src/main/resources/META-INF/mods.toml` → `version` field (if it references a hardcoded version)

## Build Dependencies

Compile-only dependencies (not bundled in the JAR, all stored in `libs/` via Git LFS):
- `cc-tweaked-1.18.2:1.101.3` — from SquidDev Maven (in `build.gradle`)
- `AdvancedPeripherals-1.18.2-0.7.31r.jar` — Git LFS
- `the_vault-1.18.2-3.20.3.6055.jar` — Git LFS
- `curios-forge-1.18.2-5.0.9.2.jar` — Git LFS
- `jei-1.18.2-9.7.2.1001.jar` — Git LFS

All LFS-tracked JARs are pulled automatically in CI (`checkout` with `lfs: true`).

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
