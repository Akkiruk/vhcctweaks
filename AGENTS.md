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

- Use `scripts/build-and-deploy-vhcctweaks.ps1` for ordinary local changes.
- That helper builds with `gradlew.bat build`, copies the latest `vhcctweaks-*.jar` into the runtime `mods/` folder, and copies `scripts/*.zs` into the runtime `scripts/` folder.
- Build output lives at `build/libs/vhcctweaks-<mod_version>.jar`.
- Use `.vscode/release.ps1 -Version x.y.z` only when the task is a formal tagged release with changelog and README version updates.

## Coding Conventions

- Optional Advanced Peripherals mixins should keep using string targets so they no-op cleanly when AP is absent.
- Config patching must stay idempotent.
- Security-sensitive APIs should validate inputs server-side and keep side effects crash-safe.
- Keep the local runtime integration aligned with the Vault Paradise instance paths in this repo and the PrismLauncher instance.

## Git Workflow Preference

- Default behavior after changes: commit and push directly to `master` without waiting for user review.
- Use `scripts/push-all.ps1` for the default fast path.
- Do not rewrite history or force-push unless the user explicitly asks.
