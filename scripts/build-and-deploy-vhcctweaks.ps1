param(
    [switch]$SkipBuild,
    [switch]$SkipJarDeploy,
    [switch]$SkipScriptDeploy,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Get-ModVersion {
    param([string]$RepoRoot)

    $propsPath = Join-Path $RepoRoot "gradle.properties"
    $props = Get-Content $propsPath -Raw
    if ($props -notmatch "mod_version=(\S+)") {
        throw "Could not read mod_version from $propsPath"
    }
    return $Matches[1]
}

function Get-BuiltJar {
    param(
        [string]$RepoRoot,
        [string]$Version
    )

    $expected = Join-Path $RepoRoot "build\libs\vhcctweaks-$Version.jar"
    if (Test-Path $expected) {
        return Get-Item $expected
    }

    $fallback = Get-ChildItem (Join-Path $RepoRoot "build\libs") -Filter "vhcctweaks-*.jar" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $fallback) {
        throw "No built vhcctweaks JAR found under build/libs"
    }

    return $fallback
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$modsDir = Join-Path $env:APPDATA "PrismLauncher\instances\Vault Paradise\minecraft\mods"
$instanceScriptsDir = Join-Path $env:APPDATA "PrismLauncher\instances\Vault Paradise\minecraft\scripts"
$gradlew = Join-Path $repoRoot "gradlew.bat"
$version = Get-ModVersion -RepoRoot $repoRoot

Write-Host "Repo:    $repoRoot"
Write-Host "Version: $version"

if (-not $SkipBuild) {
    if (-not (Test-Path $gradlew)) {
        throw "Gradle wrapper not found: $gradlew"
    }

    if ($DryRun) {
        Write-Host "[dry-run] Would run: .\gradlew.bat build" -ForegroundColor Yellow
    } else {
        Push-Location $repoRoot
        try {
            & $gradlew build
            if ($LASTEXITCODE -ne 0) {
                throw "Gradle build failed"
            }
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipJarDeploy) {
    if ($DryRun) {
        Write-Host "[dry-run] Would deploy the latest vhcctweaks JAR to $modsDir" -ForegroundColor Yellow
    } else {
        if (-not (Test-Path $modsDir)) {
            throw "Mods directory not found: $modsDir"
        }

        $jar = Get-BuiltJar -RepoRoot $repoRoot -Version $version
        Get-ChildItem $modsDir -Filter "vhcctweaks-*.jar" -File -ErrorAction SilentlyContinue | Remove-Item -Force
        Copy-Item $jar.FullName $modsDir -Force
        Write-Host "Deployed $($jar.Name) to $modsDir" -ForegroundColor Green
    }
}

if (-not $SkipScriptDeploy) {
    $recipeScripts = Get-ChildItem $PSScriptRoot -Filter "*.zs" -File -ErrorAction SilentlyContinue

    if ($DryRun) {
        Write-Host "[dry-run] Would copy $($recipeScripts.Count) CraftTweaker scripts to $instanceScriptsDir" -ForegroundColor Yellow
    } else {
        if (-not (Test-Path $instanceScriptsDir)) {
            New-Item -ItemType Directory -Path $instanceScriptsDir -Force | Out-Null
        }

        foreach ($file in $recipeScripts) {
            Copy-Item $file.FullName $instanceScriptsDir -Force
        }

        Write-Host "Copied $($recipeScripts.Count) CraftTweaker scripts to $instanceScriptsDir" -ForegroundColor Green
    }
}
