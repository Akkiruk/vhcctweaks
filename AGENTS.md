# vhcctweaks Instructions

## Scope

- This repo is the source of truth for the `vhcctweaks` Forge mod.
- Primary branch: `master`
- Target stack: Java 17, Minecraft 1.18.2, Forge 40.3.11
- Runtime target: `%APPDATA%\PrismLauncher\instances\Vault Paradise\minecraft\`

## Project Layout

- `src/main/java/` for Java source
- `src/main/resources/` for assets, mixins config, and Forge metadata
- `scripts/*.zs` for CraftTweaker recipe scripts that should also be deployed to the runtime instance
- `docs/` for API references and Lua stubs
- `.vscode/release.ps1` for the formal versioned release flow

## Build And Deploy

- Use `scripts/build-and-deploy-vhcctweaks.ps1` only for intermediate local validation before the final release step.
- That helper forces a Java 17 toolchain locally, builds with `gradlew.bat build`, copies the latest `vhcctweaks-*.jar` into the runtime `mods/` folder, and copies `scripts/*.zs` into the runtime `scripts/` folder.
- Build output lives at `build/libs/vhcctweaks-<mod_version>.jar`.
- After any change in this repo that should be kept, always finish by running `.vscode/release.ps1` so the change is built, deployed, committed, tagged, pushed, and published as the next GitHub release.
- Use `.vscode/release.ps1` for the formal tagged release flow. It freezes `CHANGELOG.md`'s `[Unreleased]` notes into the next versioned section, updates README version references, pushes the tag, and creates or updates the GitHub release.
- Do not describe a change as "released" unless `.vscode/release.ps1` completed successfully.
- `scripts/build-and-deploy-vhcctweaks.ps1` and `scripts/push-all.ps1` are not release workflows. They produce deployed or pushed changes only.
- Keep `CHANGELOG.md`'s `[Unreleased]` section current while developing so release notes stay accurate.

## Coding Conventions

- Optional Advanced Peripherals mixins should keep using string targets so they no-op cleanly when AP is absent.
- Config patching must stay idempotent.
- Security-sensitive APIs should validate inputs server-side and keep side effects crash-safe.
- Keep the local runtime integration aligned with the Vault Paradise instance paths in this repo and the PrismLauncher instance.

## Git Workflow Preference

- Default behavior after vhcctweaks changes: run `.vscode/release.ps1` without waiting for user review.
- Treat build-only, deploy-only, or push-only flows as incomplete unless the user explicitly asks not to release yet.
- Do not rewrite history or force-push unless the user explicitly asks.
