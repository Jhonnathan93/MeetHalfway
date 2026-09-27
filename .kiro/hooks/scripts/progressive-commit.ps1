$ErrorActionPreference = 'Stop'

# Kiro runs this from the workspace. Resolve the repository from this script so
# it also works when invoked manually from another directory.
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
Set-Location $repositoryRoot

$gitDirectory = git rev-parse --git-dir 2>$null
if ($LASTEXITCODE -ne 0) {
    throw 'Progressive commit hook must run inside a Git repository.'
}

$gitName = git config --get user.name
$gitEmail = git config --get user.email
if ([string]::IsNullOrWhiteSpace($gitName) -or [string]::IsNullOrWhiteSpace($gitEmail)) {
    throw 'Configure git user.name and user.email before enabling progressive commits.'
}

# Respects .gitignore and does nothing when no versionable change exists.
git diff --check
if ($LASTEXITCODE -ne 0) {
    throw 'Whitespace errors must be fixed before creating a progressive commit.'
}

git add --all
if ($LASTEXITCODE -ne 0) {
    throw 'Could not stage changes for the progressive commit.'
}

git diff --cached --check
if ($LASTEXITCODE -ne 0) {
    throw 'Staged whitespace errors must be fixed before creating a progressive commit.'
}

git diff --cached --quiet
if ($LASTEXITCODE -eq 0) {
    exit 0
}
if ($LASTEXITCODE -ne 1) {
    throw 'Could not inspect staged changes for the progressive commit.'
}

git commit -m 'chore: checkpoint progressive changes'
if ($LASTEXITCODE -ne 0) {
    throw 'Could not create the progressive commit.'
}
