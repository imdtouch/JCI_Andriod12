# Quick Release Script
# Usage: .\RELEASE.ps1
# This will bump version, build, and push to GitHub

# Get current version from build.gradle.kts
$gradle = Get-Content "app\build.gradle.kts" -Raw
if ($gradle -match 'versionCode = (\d+)') { $code = [int]$matches[1] + 1 }
if ($gradle -match 'versionName = "([^"]+)"') { 
    $parts = $matches[1] -split '\.'
    $parts[-1] = [int]$parts[-1] + 1
    $name = $parts -join '.'
}

Write-Host "Releasing v$name (code $code)..."

# Update version
$gradle = $gradle -replace 'versionCode = \d+', "versionCode = $code"
$gradle = $gradle -replace 'versionName = "[^"]+"', "versionName = `"$name`""
$gradle | Set-Content "app\build.gradle.kts" -NoNewline

# Build
.\gradlew assembleRelease
if ($LASTEXITCODE -ne 0) { Write-Host "Build failed"; exit 1 }

# Git
git add -A
git commit -m "v$name"
git tag "v$name"
git push origin main --tags

# GitHub Release
. .\release-config.ps1
$headers = @{ Authorization = "token $env:GITHUB_TOKEN"; Accept = "application/vnd.github.v3+json" }
$body = @{ tag_name = "v$name"; name = "v$name"; body = "v$name" } | ConvertTo-Json
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$env:GITHUB_OWNER/$env:GITHUB_REPO/releases" -Method Post -Headers $headers -Body $body -ContentType "application/json"
$apk = [System.IO.File]::ReadAllBytes("$PWD\app\build\outputs\apk\release\app-release.apk")
$headers2 = @{ Authorization = "token $env:GITHUB_TOKEN"; "Content-Type" = "application/vnd.android.package-archive" }
Invoke-RestMethod -Uri "https://uploads.github.com/repos/$env:GITHUB_OWNER/$env:GITHUB_REPO/releases/$($release.id)/assets?name=app-release.apk" -Method Post -Headers $headers2 -Body $apk | Out-Null

Write-Host "v$name released!"
