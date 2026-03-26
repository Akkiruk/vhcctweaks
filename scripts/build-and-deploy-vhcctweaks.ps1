param(
    [switch]$SkipBuild,
    [switch]$SkipJarDeploy,
    [switch]$SkipScriptDeploy,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$commonPath = Join-Path $PSScriptRoot "vhcctweaks-common.ps1"
. $commonPath

$repoRoot = Get-RepoRoot -ScriptRoot $PSScriptRoot
$modsDir = Join-Path $env:APPDATA "PrismLauncher\instances\Vault Paradise\minecraft\mods"
$instanceScriptsDir = Join-Path $env:APPDATA "PrismLauncher\instances\Vault Paradise\minecraft\scripts"
$version = Get-ModVersion -RepoRoot $repoRoot
$javaHome = Get-Java17Home

Write-Host "Repo:    $repoRoot"
Write-Host "Version: $version"
Write-Host "Java 17: $javaHome"

if (-not $SkipBuild) {
    if ($DryRun) {
        Write-Host "[dry-run] Would run: .\gradlew.bat build under Java 17" -ForegroundColor Yellow
    } else {
        Invoke-GradleBuild -RepoRoot $repoRoot
        $version = Get-ModVersion -RepoRoot $repoRoot
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
