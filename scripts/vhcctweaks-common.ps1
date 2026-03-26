Set-StrictMode -Version Latest

function Get-RepoRoot {
    param([string]$ScriptRoot)

    if (-not $ScriptRoot) {
        throw "ScriptRoot is required."
    }

    return (Split-Path -Parent $ScriptRoot)
}

function Get-GradleProperty {
    param(
        [string]$RepoRoot,
        [string]$Name
    )

    $propsPath = Join-Path $RepoRoot "gradle.properties"
    if (-not (Test-Path $propsPath)) {
        throw "gradle.properties not found: $propsPath"
    }

    $content = Get-Content $propsPath -Raw
    $match = [regex]::Match($content, "(?m)^" + [regex]::Escape($Name) + "=(.+)$")
    if (-not $match.Success) {
        throw "Could not read '$Name' from $propsPath"
    }

    return $match.Groups[1].Value.Trim()
}

function Get-ModVersion {
    param([string]$RepoRoot)

    return (Get-GradleProperty -RepoRoot $RepoRoot -Name "mod_version")
}

function Get-BuiltJar {
    param(
        [string]$RepoRoot,
        [string]$Version
    )

    $expected = Join-Path $RepoRoot "build\libs\vhcctweaks-$Version.jar"
    if (Test-Path $expected) {
        return (Get-Item $expected)
    }

    $fallback = Get-ChildItem (Join-Path $RepoRoot "build\libs") -Filter "vhcctweaks-*.jar" -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $fallback) {
        throw "No built vhcctweaks JAR found under build/libs"
    }

    return $fallback
}

function Set-Utf8NoBomContent {
    param(
        [string]$Path,
        [string]$Content
    )

    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Get-Java17Home {
    $currentJavaHome = $env:JAVA_HOME
    if ($currentJavaHome) {
        $currentJavaExe = Join-Path $currentJavaHome "bin\java.exe"
        if (Test-Path $currentJavaExe) {
            $versionLine = (& $currentJavaExe -version 2>&1 | Select-Object -First 1)
            if ($versionLine -match 'version "17\.') {
                return $currentJavaHome
            }
        }
    }

    $roots = @(
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files\Microsoft"
    )

    foreach ($root in $roots) {
        if (-not (Test-Path $root)) {
            continue
        }

        $candidate = Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "jdk-17*" } |
            Sort-Object Name -Descending |
            Select-Object -First 1

        if ($candidate) {
            $javaExe = Join-Path $candidate.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                return $candidate.FullName
            }
        }
    }

    throw "Could not find a Java 17 installation. Install Temurin/OpenJDK 17 or set JAVA_HOME to a JDK 17 path."
}

function Invoke-WithJava17 {
    param(
        [scriptblock]$ScriptBlock
    )

    if (-not $ScriptBlock) {
        throw "ScriptBlock is required."
    }

    $javaHome = Get-Java17Home
    $javaBin = Join-Path $javaHome "bin"
    $oldJavaHome = $env:JAVA_HOME
    $oldPath = $env:Path

    try {
        $env:JAVA_HOME = $javaHome

        $pathParts = @()
        $pathParts += $javaBin
        if ($oldPath) {
            $pathParts += ($oldPath -split ';' | Where-Object { $_ -and $_ -ne $javaBin })
        }
        $env:Path = $pathParts -join ';'

        & $ScriptBlock
    } finally {
        $env:JAVA_HOME = $oldJavaHome
        $env:Path = $oldPath
    }
}

function Invoke-GradleBuild {
    param([string]$RepoRoot)

    $gradlew = Join-Path $RepoRoot "gradlew.bat"
    if (-not (Test-Path $gradlew)) {
        throw "Gradle wrapper not found: $gradlew"
    }

    Invoke-WithJava17 {
        Push-Location $RepoRoot
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
