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

function Get-GitHubRepoSlug {
    param([string]$RemoteName = 'origin')

    $remoteUrl = (git remote get-url $RemoteName 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $remoteUrl) {
        return $null
    }

    $match = [regex]::Match($remoteUrl.Trim(), 'github\.com[:/](?<slug>[^/]+/[^/.]+?)(?:\.git)?$')
    if ($match.Success) {
        return $match.Groups['slug'].Value
    }

    return $null
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

function Test-OnlyAutoVersionBumpChange {
    $dirtyEntries = @(git status --porcelain=v1)
    if ($LASTEXITCODE -ne 0) {
        throw "git status --porcelain=v1 failed"
    }

    $dirtyEntries = @($dirtyEntries | Where-Object { $_.Trim() })
    if ($dirtyEntries.Count -ne 1 -or $dirtyEntries[0] -notmatch '^.M gradle\.properties$|^M. gradle\.properties$') {
        return $false
    }

    $diffLines = @(git diff --unified=0 -- gradle.properties)
    if ($LASTEXITCODE -ne 0) {
        throw "git diff --unified=0 -- gradle.properties failed"
    }

    $changedPropertyLines = @(
        $diffLines |
            Where-Object { $_ -match '^[+-]' -and $_ -notmatch '^\+\+\+' -and $_ -notmatch '^---' }
    )

    if (-not $changedPropertyLines) {
        return $false
    }

    foreach ($line in $changedPropertyLines) {
        if ($line -notmatch '^[+-]mod_(version|source_hash)=') {
            return $false
        }
    }

    return $true
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
        if (Test-OnlyAutoVersionBumpChange) {
            Write-Warning "Working tree contains only the build-generated gradle.properties version/hash bump; continuing with release."
        } else {
            throw "Working tree is dirty. Commit or stash changes first."
        }
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

    if ($SkipGitHubRelease) {
        Write-Warning "-SkipGitHubRelease is deprecated. Pushing a release tag now always hands off GitHub release publishing to GitHub Actions."
    }

    $startingVersion = Get-ModVersion -RepoRoot $RepoRoot
    $releaseNotes = Get-ReleaseNotes -ChangelogPath $changelogPath -InlineEntry $ChangelogEntry -EntryFile $ChangelogFile
    $javaHome = Get-Java17Home
    $repoSlug = Get-GitHubRepoSlug

    if ($DryRun) {
        Write-Host "[dry-run] Would build under Java 17 at $javaHome" -ForegroundColor Yellow
        Write-Host "[dry-run] Starting version: $startingVersion" -ForegroundColor Yellow
        Write-Host "[dry-run] Would freeze [Unreleased] into the next release section" -ForegroundColor Yellow
        Write-Host "[dry-run] Would commit, tag, push, and let GitHub Actions publish the GitHub release" -ForegroundColor Yellow
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

    Write-Host "[6/6] Handing off GitHub release publishing to GitHub Actions..." -ForegroundColor White
    if ($repoSlug) {
        Write-Host "  Workflow: https://github.com/$repoSlug/actions/workflows/release.yml" -ForegroundColor Gray
        Write-Host "  Release:  https://github.com/$repoSlug/releases/tag/$tag" -ForegroundColor Gray
    } else {
        Write-Host "  GitHub Actions 'release.yml' will create or update the GitHub release for $tag." -ForegroundColor Gray
    }

    Write-Host "`n=== Release $tag complete! ===" -ForegroundColor Cyan
} finally {
    Pop-Location
}
