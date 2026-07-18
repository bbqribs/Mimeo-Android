[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$script:SigningPropertiesName = "keystore.properties"

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

function Get-AndroidSdkRoot {
    if ($env:ANDROID_HOME) {
        return $env:ANDROID_HOME
    }
    if ($env:ANDROID_SDK_ROOT) {
        return $env:ANDROID_SDK_ROOT
    }

    $localPropertiesPath = Join-Path $script:RepoRoot "local.properties"
    if (Test-Path -LiteralPath $localPropertiesPath -PathType Leaf) {
        $sdkLine = Get-Content -LiteralPath $localPropertiesPath |
            Where-Object { $_ -match '^sdk\.dir=' } |
            Select-Object -First 1
        if ($sdkLine) {
            return $sdkLine.Substring("sdk.dir=".Length).Replace("\:", ":").Replace("\\", "\")
        }
    }

    throw "Android SDK location is unavailable; set ANDROID_HOME or local.properties before running this test."
}

function Invoke-GradleCapture {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [Parameter(Mandatory)]
        [string[]]$Tasks
    )

    Push-Location $ProjectRoot
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & (Join-Path $ProjectRoot "gradlew.bat") @Tasks --no-daemon --console=plain 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
        Pop-Location
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = ($output -join "`n")
    }
}

function Assert-NoSensitiveOutput {
    param(
        [Parameter(Mandatory)]
        [string]$Output,

        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [string[]]$SensitiveValues,

        [Parameter(Mandatory)]
        [string]$Scenario
    )

    foreach ($value in $SensitiveValues) {
        if ($value -and $Output.IndexOf($value, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
            throw "$Scenario exposed a fixture signing value or keystore path in Gradle output."
        }
    }
}

function Assert-GradleFailure {
    param(
        [Parameter(Mandatory)]
        [pscustomobject]$Result,

        [Parameter(Mandatory)]
        [string]$ExpectedMessage,

        [Parameter(Mandatory)]
        [string]$Scenario,

        [AllowEmptyCollection()]
        [string[]]$SensitiveValues = @()
    )

    Assert-Condition ($Result.ExitCode -ne 0) "$Scenario unexpectedly succeeded."
    if ($Result.Output -notmatch [regex]::Escape($ExpectedMessage)) {
        if ($SensitiveValues.Count -eq 0) {
            Write-Host $Result.Output
        }
        throw "$Scenario failed without the expected safe diagnostic."
    }
    Assert-NoSensitiveOutput -Output $Result.Output -SensitiveValues $SensitiveValues -Scenario $Scenario
    Write-Host "PASS: $Scenario fails closed without exposing signing values."
}

function Write-SigningProperties {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [Parameter(Mandatory)]
        [string]$StoreFile,

        [Parameter(Mandatory)]
        [string]$StorePassword,

        [Parameter(Mandatory)]
        [string]$KeyAlias,

        [Parameter(Mandatory)]
        [string]$KeyPassword
    )

    $storeFileValue = $StoreFile.Replace("\", "/")
    $content = @(
        "storeFile=$storeFileValue"
        "storePassword=$StorePassword"
        "keyAlias=$KeyAlias"
        "keyPassword=$KeyPassword"
    ) -join [Environment]::NewLine
    $encoding = [Text.UTF8Encoding]::new($false)
    [IO.File]::WriteAllText((Join-Path $ProjectRoot $script:SigningPropertiesName), $content, $encoding)
}

$workflowPath = Join-Path $script:RepoRoot ".github\workflows\android-release-check.yml"
$workflow = Get-Content -Raw -LiteralPath $workflowPath
Assert-Condition ($workflow -match '(?m)^\s*pull_request:') "Android Release Check must run on pull requests."
Assert-Condition ($workflow -match '(?m)^\s*push:') "Android Release Check must run on merged-main pushes."
Assert-Condition ($workflow.Contains(":app:verifyUnsignedRelease")) "Android Release Check must call the explicit unsigned Gradle lane."
Assert-Condition (-not $workflow.Contains(":app:assembleRelease")) "Android Release Check must not call the signed production assemble task."
Write-Host "PASS: pull-request and merged-main release checks select the explicit no-secrets lane."

$unsignedOutputDirectory = Join-Path $script:RepoRoot "app\build\outputs\apk\unsignedRelease"
$unsignedApks = @(Get-ChildItem -LiteralPath $unsignedOutputDirectory -Filter "*.apk" -File -ErrorAction SilentlyContinue)
Assert-Condition ($unsignedApks.Count -eq 1) "Run :app:verifyUnsignedRelease before this script; exactly one unsignedRelease APK is required."
Assert-Condition ($unsignedApks[0].Name -match 'unsigned') "Unsigned lane output filename must be visibly identified as unsigned."

$sdkRoot = Get-AndroidSdkRoot
$apksigner = Get-ChildItem -LiteralPath (Join-Path $sdkRoot "build-tools") -Filter "apksigner.bat" -File -Recurse |
    Sort-Object FullName -Descending |
    Select-Object -First 1
Assert-Condition ($null -ne $apksigner) "Android apksigner was not found under the configured SDK."
$apksignerPath = $apksigner.FullName
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$null = & $apksignerPath verify $unsignedApks[0].FullName 2>&1
$apksignerExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorActionPreference
Assert-Condition ($apksignerExitCode -ne 0) "Unsigned release verification output is signed; CI must never create a signed artifact."
Write-Host "PASS: unsignedRelease output is visibly named, has no APK signature, and is non-publishable."

$tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$fixtureRoot = [IO.Path]::GetFullPath([IO.Path]::Combine($tempBase, "mimeo-signing-lanes-" + [guid]::NewGuid().ToString("N")))
Assert-Condition ($fixtureRoot.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase)) "Fixture directory escaped the system temp directory."
$null = New-Item -ItemType Directory -Path $fixtureRoot

try {
    $excludedDirectories = @(
        (Join-Path $script:RepoRoot ".git"),
        (Join-Path $script:RepoRoot ".gradle"),
        (Join-Path $script:RepoRoot ".idea"),
        (Join-Path $script:RepoRoot "build"),
        (Join-Path $script:RepoRoot "app\build")
    )
    $copyArguments = @(
        $script:RepoRoot,
        $fixtureRoot,
        "/E",
        "/XJ",
        "/NFL",
        "/NDL",
        "/NJH",
        "/NJS",
        "/NP",
        "/XD"
    ) + $excludedDirectories + @(
        "/XF",
        $script:SigningPropertiesName,
        "local.properties"
    )
    $null = & robocopy @copyArguments
    Assert-Condition ($LASTEXITCODE -le 7) "Could not create the isolated signing-lane fixture."

    $env:ANDROID_HOME = $sdkRoot
    $propertiesPath = Join-Path $fixtureRoot $script:SigningPropertiesName
    Assert-Condition (-not (Test-Path -LiteralPath $propertiesPath)) "The isolated fixture copied operator signing inputs."

    $unsignedConfiguration = Invoke-GradleCapture -ProjectRoot $fixtureRoot -Tasks @(
        ":app:verifyUnsignedReleaseConfiguration"
    )
    Assert-Condition ($unsignedConfiguration.ExitCode -eq 0) "Unsigned release configuration validation failed without signing inputs."
    Assert-Condition ($unsignedConfiguration.Output.Contains("non-production/non-publishable")) "Unsigned lane did not print its non-publishable classification."
    Write-Host "PASS: unsigned configuration succeeds without keystore.properties and declares non-publishable status."

    $missingInputs = Invoke-GradleCapture -ProjectRoot $fixtureRoot -Tasks @(
        ":app:verifySignedProductionRelease"
    )
    Assert-GradleFailure -Result $missingInputs -ExpectedMessage "Release signing requires keystore.properties" -Scenario "Absent signed-production inputs"

    [IO.File]::WriteAllText($propertiesPath, "storeFile=\u12GZ", [Text.UTF8Encoding]::new($false))
    $malformed = Invoke-GradleCapture -ProjectRoot $fixtureRoot -Tasks @(
        ":app:verifySignedProductionRelease"
    )
    Assert-GradleFailure -Result $malformed -ExpectedMessage "Release signing properties are malformed or unreadable" -Scenario "Malformed signing properties"

    $storePassword = "Store" + [guid]::NewGuid().ToString("N")
    $keyPassword = "Key" + [guid]::NewGuid().ToString("N")
    $validAlias = "valid-" + [guid]::NewGuid().ToString("N")
    $invalidAlias = "invalid-" + [guid]::NewGuid().ToString("N")
    $wrongPassword = "Wrong" + [guid]::NewGuid().ToString("N")
    $keyStorePath = Join-Path $fixtureRoot "ephemeral-signing-test.jks"
    $missingKeyStorePath = Join-Path $fixtureRoot "absent-signing-test.jks"
    $sensitiveValues = @($storePassword, $keyPassword, $validAlias, $invalidAlias, $wrongPassword, $keyStorePath)

    Write-SigningProperties -ProjectRoot $fixtureRoot -StoreFile $missingKeyStorePath -StorePassword $storePassword -KeyAlias $validAlias -KeyPassword $keyPassword
    $missingKeyStore = Invoke-GradleCapture -ProjectRoot $fixtureRoot -Tasks @(
        ":app:verifySignedProductionRelease"
    )
    Assert-GradleFailure -Result $missingKeyStore -ExpectedMessage "Release signing keystore file was not found" -Scenario "Missing signing keystore" -SensitiveValues $sensitiveValues

    $keytoolPath = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME "bin\keytool.exe"))) {
        Join-Path $env:JAVA_HOME "bin\keytool.exe"
    } else {
        (Get-Command keytool -ErrorAction Stop).Source
    }
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $null = & $keytoolPath -genkeypair -alias $validAlias -keyalg RSA -keysize 2048 -validity 1 -dname "CN=Ephemeral CI Signing Test" -storetype JKS -keystore $keyStorePath -storepass $storePassword -keypass $keyPassword -noprompt 2>&1
    $keytoolExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    Assert-Condition ($keytoolExitCode -eq 0) "Could not generate the ephemeral signing test keystore."

    Write-SigningProperties -ProjectRoot $fixtureRoot -StoreFile $keyStorePath -StorePassword $storePassword -KeyAlias $invalidAlias -KeyPassword $keyPassword
    $invalidAliasResult = Invoke-GradleCapture -ProjectRoot $fixtureRoot -Tasks @(
        ":app:verifySignedProductionRelease"
    )
    Assert-GradleFailure -Result $invalidAliasResult -ExpectedMessage "Release signing keyAlias was not found" -Scenario "Invalid signing alias" -SensitiveValues $sensitiveValues

    Write-SigningProperties -ProjectRoot $fixtureRoot -StoreFile $keyStorePath -StorePassword $storePassword -KeyAlias $validAlias -KeyPassword $wrongPassword
    $invalidCredential = Invoke-GradleCapture -ProjectRoot $fixtureRoot -Tasks @(
        ":app:verifySignedProductionRelease"
    )
    Assert-GradleFailure -Result $invalidCredential -ExpectedMessage "Release signing key could not be opened" -Scenario "Invalid signing credential" -SensitiveValues $sensitiveValues

    Write-SigningProperties -ProjectRoot $fixtureRoot -StoreFile $keyStorePath -StorePassword $storePassword -KeyAlias $validAlias -KeyPassword $keyPassword
    $invalidSigner = Invoke-GradleCapture -ProjectRoot $fixtureRoot -Tasks @(
        ":app:verifySignedProductionRelease"
    )
    Assert-GradleFailure -Result $invalidSigner -ExpectedMessage "Release signing certificate does not match" -Scenario "Invalid signer identity" -SensitiveValues $sensitiveValues

    Remove-Item -LiteralPath $propertiesPath -Force
    $signedAssemble = Invoke-GradleCapture -ProjectRoot $fixtureRoot -Tasks @(
        ":app:assembleSignedProductionRelease"
    )
    Assert-GradleFailure -Result $signedAssemble -ExpectedMessage "Release signing requires keystore.properties" -Scenario "Signed-production assemble without inputs"
} finally {
    if ($fixtureRoot.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $fixtureRoot)) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}

Write-Host "All release signing lane tests passed. No production signing input was read, copied, logged, or published."
