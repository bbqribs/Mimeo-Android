[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$EventName,

    [string]$Ref = "",

    [string]$BaseSha = "",

    [string]$HeadSha = "",

    [string[]]$ChangedPaths
)

$ErrorActionPreference = "Stop"

function New-Classification {
    param(
        [Parameter(Mandatory)]
        [bool]$FullMatrix,

        [Parameter(Mandatory)]
        [string]$Reason,

        [int]$ChangedPathCount = 0
    )

    return ([pscustomobject]@{
        fullMatrix = $FullMatrix
        reason = $Reason
        changedPathCount = $ChangedPathCount
    } | ConvertTo-Json -Compress)
}

function Get-SigningSensitiveReason {
    param(
        [Parameter(Mandatory)]
        [string]$Path
    )

    $normalizedPath = $Path.Trim().Replace("\", "/").ToLowerInvariant()
    if ($normalizedPath.StartsWith("./")) {
        $normalizedPath = $normalizedPath.Substring(2)
    }
    if ($normalizedPath -in @(
            "scripts/select-unsigned-release-lane.ps1",
            "scripts/test-unsigned-release-path-classifier.ps1"
        )) {
        return "path-classification logic changed"
    }
    if ($normalizedPath -in @(
            ".github/workflows/android-release-check.yml",
            ".github/workflows/android-release-check.yaml"
        )) {
        return "unsigned-release workflow changed"
    }
    if ($normalizedPath.StartsWith("scripts/")) {
        return "signing/release script changed"
    }
    if (
        $normalizedPath -match '(^|/)(signing-fixtures?|fixtures/signing)(/|$)' -or
        $normalizedPath -match 'signing' -or
        $normalizedPath -match 'unsigned[-_]?release'
    ) {
        return "signing fixture or signing-specific path changed"
    }
    if ($normalizedPath -in @(
            ".gitignore",
            "keystore.properties.example",
            "release_notes.md"
        )) {
        return "signing input safeguard or trust metadata changed"
    }
    if (
        $normalizedPath -in @("gradle.properties", "gradlew", "gradlew.bat") -or
        $normalizedPath.StartsWith("gradle/") -or
        $normalizedPath.EndsWith(".gradle") -or
        $normalizedPath.EndsWith(".gradle.kts") -or
        $normalizedPath -eq "app/proguard-rules.pro"
    ) {
        return "Gradle or release build configuration changed"
    }

    return $null
}

$normalizedEventName = $EventName.Trim().ToLowerInvariant()
switch ($normalizedEventName) {
    "schedule" {
        New-Classification -FullMatrix $true -Reason "scheduled runs always require the full signing-failure matrix"
        exit 0
    }
    "workflow_dispatch" {
        New-Classification -FullMatrix $true -Reason "manual dispatch always requires the full signing-failure matrix"
        exit 0
    }
    "push" {
        if ($Ref -eq "refs/heads/main") {
            New-Classification -FullMatrix $true -Reason "pushes to main always require the full signing-failure matrix"
        } else {
            New-Classification -FullMatrix $true -Reason "non-main push metadata is outside the reduced-lane contract"
        }
        exit 0
    }
    "pull_request" {
        # Pull requests are classified below from their complete base-to-head path set.
    }
    default {
        New-Classification -FullMatrix $true -Reason "unknown event metadata fails closed to the full signing-failure matrix"
        exit 0
    }
}

$resolvedChangedPaths = @()
if ($PSBoundParameters.ContainsKey("ChangedPaths")) {
    $resolvedChangedPaths = @($ChangedPaths)
} else {
    if (
        $BaseSha -notmatch '^[0-9a-fA-F]{7,64}$' -or
        $HeadSha -notmatch '^[0-9a-fA-F]{7,64}$'
    ) {
        New-Classification -FullMatrix $true -Reason "pull-request change metadata is missing or malformed"
        exit 0
    }

    $null = & git cat-file -e "$BaseSha`^{commit}" 2>$null
    if ($LASTEXITCODE -ne 0) {
        New-Classification -FullMatrix $true -Reason "pull-request base commit is unavailable"
        exit 0
    }
    $null = & git cat-file -e "$HeadSha`^{commit}" 2>$null
    if ($LASTEXITCODE -ne 0) {
        New-Classification -FullMatrix $true -Reason "pull-request head commit is unavailable"
        exit 0
    }

    $resolvedChangedPaths = @(& git diff --name-only --no-renames --diff-filter=ACDMRTUXB "$BaseSha...$HeadSha" -- 2>$null)
    if ($LASTEXITCODE -ne 0) {
        New-Classification -FullMatrix $true -Reason "pull-request changed paths could not be determined"
        exit 0
    }
}

$resolvedChangedPaths = @(
    $resolvedChangedPaths |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Sort-Object -Unique
)
if ($resolvedChangedPaths.Count -eq 0) {
    New-Classification -FullMatrix $true -Reason "pull-request changed-path metadata is empty or uncertain"
    exit 0
}

foreach ($path in $resolvedChangedPaths) {
    $sensitiveReason = Get-SigningSensitiveReason -Path $path
    if ($sensitiveReason) {
        New-Classification -FullMatrix $true -Reason $sensitiveReason -ChangedPathCount $resolvedChangedPaths.Count
        exit 0
    }
}

New-Classification `
    -FullMatrix $false `
    -Reason "ordinary application/documentation paths only; reduced PR lane selected" `
    -ChangedPathCount $resolvedChangedPaths.Count
