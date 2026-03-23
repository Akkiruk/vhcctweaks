param(
    [string]$Message,
    [string]$Remote = "origin",
    [string]$Branch = "master",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot

Push-Location $repoRoot
try {
    $insideWorkTree = git rev-parse --is-inside-work-tree 2>$null
    if ($LASTEXITCODE -ne 0 -or $insideWorkTree -ne "true") {
        throw "Not a git repository: $repoRoot"
    }

    $status = git status --porcelain --untracked-files=all
    if (-not $status) {
        Write-Host "No changes to commit." -ForegroundColor DarkGray
        return
    }

    if (-not $Message) {
        $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        $Message = "Codex update $timestamp"
    }

    $currentBranch = git rev-parse --abbrev-ref HEAD
    Write-Host "Repo:    $repoRoot"
    Write-Host "Branch:  $currentBranch -> $Remote/$Branch"
    Write-Host "Message: $Message"

    if ($DryRun) {
        Write-Host "[dry-run] Would run: git add -A" -ForegroundColor Yellow
        Write-Host "[dry-run] Would run: git commit -m `"$Message`"" -ForegroundColor Yellow
        Write-Host "[dry-run] Would run: git push $Remote HEAD:$Branch" -ForegroundColor Yellow
        return
    }

    git add -A
    if ($LASTEXITCODE -ne 0) {
        throw "git add failed"
    }

    git commit -m $Message
    if ($LASTEXITCODE -ne 0) {
        throw "git commit failed"
    }

    git push $Remote "HEAD:$Branch"
    if ($LASTEXITCODE -ne 0) {
        throw "git push failed"
    }
} finally {
    Pop-Location
}
