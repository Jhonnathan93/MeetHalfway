$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
Set-Location $repositoryRoot

$jsonFiles = @(
    '.kiro/settings/mcp.json',
    'frontend/package.json',
    'frontend/package-lock.json',
    'frontend/tsconfig.json',
    'frontend/tsconfig.node.json'
)

foreach ($relativePath in $jsonFiles) {
    if (-not (Test-Path -LiteralPath $relativePath)) {
        continue
    }

    & node -e "JSON.parse(require('node:fs').readFileSync(process.argv[1], 'utf8'))" $relativePath
    if ($LASTEXITCODE -ne 0) {
        throw "Invalid JSON in ${relativePath}."
    }
}
