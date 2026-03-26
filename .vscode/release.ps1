param(
    [string]$ChangelogEntry,
    [string]$ChangelogFile,

    [switch]$SkipBuild,
    [switch]$SkipGitHubRelease,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$CommonPath = Join-Path $RepoRoot 'scripts\vhcctweaks-common.ps1'
. $CommonPath

function Invoke-Git {
    param([string[]]$Arguments)

    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed"
    }
}

function Get-ReleaseNotes {
    param(
        [string]$ChangelogPath,
        [string]$InlineEntry,
        [string]$EntryFile
    )

    if ($InlineEntry -and $EntryFile) {
        throw "Use either -ChangelogEntry or -ChangelogFile, not both."
    }

    if ($EntryFile) {
        if (-not (Test-Path $EntryFile)) {
            throw "Changelog file not found: $EntryFile"
        }
        return (Get-Content $EntryFile -Raw).Trim()
    }

    if ($InlineEntry) {
        return $InlineEntry.Trim()
    }

    $content = (Get-Content $ChangelogPath -Raw).Replace("`r`n", "`n")
    $match = [regex]::Match($content, '(?ms)^## \[Unreleased\]\s*\n(?<body>.*?)(?=^## \[|\z)')
    if (-not $match.Success) {
        throw "CHANGELOG.md must contain an [Unreleased] section."
    }

    return $match.Groups['body'].Value.Trim()
}

function Update-ChangelogForRelease {
    param(
        [string]$ChangelogPath,
        [string]$Version,
        [string]$DateText,
        [string]$ReleaseNotes
    )

    $content = (Get-Content $ChangelogPath -Raw).Replace("`r`n", "`n")
    $match = [regex]::Match($content, '(?ms)^## \[Unreleased\]\s*\n(?<body>.*?)(?=^## \[|\z)')
    if (-not $match.Success) {
        throw "CHANGELOG.md must contain an [Unreleased] section."
    }

    $trimmedNotes = $ReleaseNotes.Trim()
    if (-not $trimmedNotes -or $trimmedNotes -eq '- No changes yet.') {
        throw "Release notes are empty. Update CHANGELOG.md [Unreleased] or pass -ChangelogEntry."
    }

    $replacement = @(
        '## [Unreleased]',
        '',
        '- No changes yet.',
        '',
        "## [$Version] - $DateText",
        '',
        $trimmedNotes,
        ''
    ) -join "`n"

    $updated = $content.Substring(0, $match.Index) + $replacement + $content.Substring($match.Index + $match.Length)
    Set-Utf8NoBomContent -Path $ChangelogPath -Content $updated
}

function Update-ReadmeVersionReferences {
    param(
        [string]$ReadmePath,
        [string]$Version
    )

    $content = (Get-Content $ReadmePath -Raw).Replace("`r`n", "`n")
    $content = [regex]::Replace(
        $content,
        'Download `vhcctweaks-\d+\.\d+\.\d+\.jar`',
        "Download ``vhcctweaks-$Version.jar``"
    )
    $content = [regex]::Replace(
        $content,
        'Output: `build/libs/vhcctweaks-\d+\.\d+\.\d+\.jar`',
        "Output: ``build/libs/vhcctweaks-$Version.jar``"
    )

    Set-Utf8NoBomContent -Path $ReadmePath -Content $content
}

Push-Location $RepoRoot
try {
    $changelogPath = Join-Path $RepoRoot 'CHANGELOG.md'
    $readmePath = Join-Path $RepoRoot 'README.md'
    $today = Get-Date -Format 'yyyy-MM-dd'

    Write-Host "`n=== VH CC Tweaks Release ===" -ForegroundColor Cyan

    $dirty = git status --porcelain
    if ($dirty) {
        throw "Working tree is dirty. Commit or stash changes first."
    }

    $branch = git rev-parse --abbrev-ref HEAD
    if ($branch -ne 'master') {
        throw "Must be on 'master' branch (currently on '$branch')."
    }

    Invoke-Git -Arguments @('fetch', '--tags', 'origin', 'master')
    $aheadBehind = (git rev-list --left-right --count HEAD...origin/master).Trim().Split()
    if ($aheadBehind.Count -ne 2 -or $aheadBehind[0] -ne '0' -or $aheadBehind[1] -ne '0') {
        throw "Release script requires master to match origin/master exactly."
    }

    if (-not $SkipGitHubRelease -and -not $DryRun) {
        & gh auth status | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "GitHub CLI is not authenticated. Run 'gh auth login' before releasing."
        }
    }

    $startingVersion = Get-ModVersion -RepoRoot $RepoRoot
    $releaseNotes = Get-ReleaseNotes -ChangelogPath $changelogPath -InlineEntry $ChangelogEntry -EntryFile $ChangelogFile
    $javaHome = Get-Java17Home

    if ($DryRun) {
        Write-Host "[dry-run] Would build under Java 17 at $javaHome" -ForegroundColor Yellow
        Write-Host "[dry-run] Starting version: $startingVersion" -ForegroundColor Yellow
        Write-Host "[dry-run] Would freeze [Unreleased] into the next release section" -ForegroundColor Yellow
        Write-Host "[dry-run] Would commit, tag, push, and create/update the GitHub release" -ForegroundColor Yellow
        return
    }

    Write-Host "  Java 17:         $javaHome" -ForegroundColor Gray
    Write-Host "  Starting version: $startingVersion" -ForegroundColor Gray

    if (-not $SkipBuild) {
        Write-Host "`n[1/6] Building JAR under Java 17..." -ForegroundColor White
        Invoke-GradleBuild -RepoRoot $RepoRoot
    } else {
        Write-Host "`n[1/6] Skipping build (--SkipBuild)" -ForegroundColor Yellow
    }

    $version = Get-ModVersion -RepoRoot $RepoRoot
    $tag = "v$version"
    if (git tag -l $tag) {
        throw "Tag $tag already exists. Nothing new to release."
    }

    $jar = Get-BuiltJar -RepoRoot $RepoRoot -Version $version
    Write-Host "  Release version:  $version" -ForegroundColor Green
    Write-Host "  Built artifact:   $($jar.Name)" -ForegroundColor Green

    Write-Host "[2/6] Updating CHANGELOG.md..." -ForegroundColor White
    Update-ChangelogForRelease -ChangelogPath $changelogPath -Version $version -DateText $today -ReleaseNotes $releaseNotes

    Write-Host "[3/6] Updating README.md..." -ForegroundColor White
    Update-ReadmeVersionReferences -ReadmePath $readmePath -Version $version

    Write-Host "[4/6] Deploying locally..." -ForegroundColor White
    & (Join-Path $RepoRoot 'scripts\build-and-deploy-vhcctweaks.ps1') -SkipBuild
    if ($LASTEXITCODE -ne 0) {
        throw "Local deploy failed."
    }

    Write-Host "[5/6] Committing, tagging, and pushing..." -ForegroundColor White
    Invoke-Git -Arguments @('add', '-A')
    Invoke-Git -Arguments @('commit', '-m', "Release $tag")
    Invoke-Git -Arguments @('tag', $tag)
    Invoke-Git -Arguments @('push', 'origin', 'master')
    Invoke-Git -Arguments @('push', 'origin', $tag)

    if (-not $SkipGitHubRelease) {
        Write-Host "[6/6] Publishing GitHub release..." -ForegroundColor White
        $notesPath = Join-Path $env:TEMP "vhcctweaks-$version-release-notes.md"
        Set-Utf8NoBomContent -Path $notesPath -Content $releaseNotes.Trim()

        try {
            & gh release view $tag 1>$null 2>$null
            $createRelease = ($LASTEXITCODE -ne 0)

            if ($createRelease) {
                & gh release create $tag $jar.FullName --title "VH CC Tweaks $tag" --notes-file $notesPath
            } else {
                & gh release edit $tag --title "VH CC Tweaks $tag" --notes-file $notesPath
                if ($LASTEXITCODE -ne 0) {
                    throw "gh release edit $tag failed"
                }

                & gh release upload $tag $jar.FullName --clobber
            }

            if ($LASTEXITCODE -ne 0) {
                throw "GitHub release publish failed"
            }
        } finally {
            Remove-Item $notesPath -ErrorAction SilentlyContinue
        }
    } else {
        Write-Host "[6/6] Skipping GitHub release (--SkipGitHubRelease)" -ForegroundColor Yellow
    }

    Write-Host "`n=== Release $tag complete! ===" -ForegroundColor Cyan
} finally {
    Pop-Location
}
