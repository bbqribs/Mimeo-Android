[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$selectorPath = Join-Path $PSScriptRoot "select-unsigned-release-lane.ps1"

function Assert-Condition {
    param(
        [Parameter(Mandatory)]
        [bool]$Condition,

        [Parameter(Mandatory)]
        [string]$Message
    )

    if (-not $Condition) {
        throw $Message
    }
}

function Get-Classification {
    param(
        [Parameter(Mandatory)]
        [string]$EventName,

        [string]$Ref = "",

        [string[]]$ChangedPaths,

        [switch]$OmitChangeMetadata
    )

    $arguments = @{
        EventName = $EventName
        Ref = $Ref
    }
    if (-not $OmitChangeMetadata) {
        $arguments.ChangedPaths = $ChangedPaths
    }

    $json = & $selectorPath @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Classifier exited with code $LASTEXITCODE."
    }
    return ($json | ConvertFrom-Json)
}

$ordinary = Get-Classification -EventName "pull_request" -ChangedPaths @(
    "app/src/main/java/com/mimeo/android/MainActivity.kt",
    "docs/ANDROID_ITEM_ACTIONS_SPEC.md"
)
Assert-Condition (-not $ordinary.fullMatrix) "Ordinary application/documentation paths must select the reduced PR lane."
Write-Host "PASS: ordinary application/documentation paths select the reduced PR lane."

$sensitiveCases = @(
    [pscustomobject]@{ Name = "Gradle/release configuration"; Path = "app/build.gradle.kts" },
    [pscustomobject]@{ Name = "signing/release scripts"; Path = "scripts/publish-household-release.ps1" },
    [pscustomobject]@{ Name = "unsigned-release workflow"; Path = ".github/workflows/android-release-check.yml" },
    [pscustomobject]@{ Name = "signing fixtures"; Path = "signing-fixtures/invalid/keystore.properties" },
    [pscustomobject]@{ Name = "classifier logic"; Path = "scripts/select-unsigned-release-lane.ps1" },
    [pscustomobject]@{ Name = "signing trust metadata"; Path = "RELEASE_NOTES.md" }
)
foreach ($case in $sensitiveCases) {
    $classification = Get-Classification -EventName "pull_request" -ChangedPaths @($case.Path)
    Assert-Condition $classification.fullMatrix "$($case.Name) must select the full signing-failure matrix."
    Write-Host "PASS: $($case.Name) selects the full signing-failure matrix."
}

$missingMetadata = Get-Classification -EventName "pull_request" -OmitChangeMetadata
Assert-Condition $missingMetadata.fullMatrix "Missing pull-request change metadata must fail closed to the full matrix."
Write-Host "PASS: missing pull-request change metadata selects the full signing-failure matrix."

$mainPush = Get-Classification -EventName "push" -Ref "refs/heads/main" -OmitChangeMetadata
Assert-Condition $mainPush.fullMatrix "Pushes to main must select the full signing-failure matrix."
Write-Host "PASS: pushes to main select the full signing-failure matrix."

$scheduled = Get-Classification -EventName "schedule" -OmitChangeMetadata
Assert-Condition $scheduled.fullMatrix "Scheduled runs must select the full signing-failure matrix."
Write-Host "PASS: scheduled runs select the full signing-failure matrix."

$manual = Get-Classification -EventName "workflow_dispatch" -OmitChangeMetadata
Assert-Condition $manual.fullMatrix "Manual dispatches must select the full signing-failure matrix."
Write-Host "PASS: manual dispatches select the full signing-failure matrix."

Write-Host "All unsigned-release path-classifier tests passed."
