[CmdletBinding()]
param(
    [ValidateSet(
        "Prepare",
        "OpenDeveloperOptions",
        "Status",
        "SignIn",
        "OpenUpNext",
        "SignInAndOpenUpNext",
        "Capture",
        "VerifyNoActiveUpNext",
        "SelfTest"
    )]
    [string]$Action = "Status",

    [string]$Serial = "",
    [string]$PackageId = "com.mimeo.android.debug",
    [string]$ServerUrl = "https://beh-august2015.taildacac5.ts.net",
    [string]$Username = $env:MIMEO_DEVICE_TEST_USERNAME,
    [string]$PasswordEnvironmentVariable = "MIMEO_DEVICE_TEST_PASSWORD",
    [string]$ExpectedItemTitle = "",
    [string]$ExpectedSourceLabel = "",
    [string]$EvidenceDirectory = "",
    [int]$WaitSeconds = 15
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$script:AdbPrefix = @()
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $script:AdbPrefix = @("-s", $Serial.Trim())
}

function Get-AdbCommandSummary {
    param(
        [Parameter(Mandatory)][string[]]$Arguments,
        [switch]$Sensitive
    )
    if ($Sensitive) { return "adb <sensitive arguments redacted>" }
    return "adb $($Arguments -join ' ')"
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments,
        [switch]$AllowFailure,
        [switch]$Sensitive
    )

    # Windows PowerShell 5 wraps native stderr as an ErrorRecord and can stop before
    # LASTEXITCODE is inspected. Capture it as text so pwsh and powershell.exe agree.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& adb @script:AdbPrefix @Arguments 2>&1 | ForEach-Object { [string]$_ })
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0 -and -not $AllowFailure) {
        $commandSummary = Get-AdbCommandSummary -Arguments $Arguments -Sensitive:$Sensitive
        throw "adb failed ($exitCode): $commandSummary`n$($output -join [Environment]::NewLine)"
    }
    return @($output)
}

function Assert-AdbTarget {
    if ($null -eq (Get-Command adb -ErrorAction SilentlyContinue)) {
        throw "adb is not available on PATH. Install Android platform-tools or open the repository's configured Android shell."
    }
    $deviceLines = @(& adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '^([^\s]+)\s+device$' })
    if ([string]::IsNullOrWhiteSpace($Serial)) {
        if ($deviceLines.Count -eq 0) { throw "No unlocked/authorized adb device is connected." }
        if ($deviceLines.Count -gt 1) { throw "Multiple adb devices are connected. Pass -Serial explicitly." }
    } elseif (-not ($deviceLines | Where-Object { $_ -match "^$([regex]::Escape($Serial))\s+device$" })) {
        throw "adb device '$Serial' is not connected and authorized."
    }
}

function ConvertFrom-UiBounds {
    param([Parameter(Mandatory)][string]$Bounds)

    if ($Bounds -notmatch '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$') {
        throw "Unsupported UI bounds: $Bounds"
    }
    $left = [int]$Matches[1]
    $top = [int]$Matches[2]
    $right = [int]$Matches[3]
    $bottom = [int]$Matches[4]
    [pscustomobject]@{
        Left = $left
        Top = $top
        Right = $right
        Bottom = $bottom
        X = [int](($left + $right) / 2)
        Y = [int](($top + $bottom) / 2)
        Width = $right - $left
        Height = $bottom - $top
    }
}

function Get-FallbackPoint {
    param(
        [Parameter(Mandatory)][int]$Width,
        [Parameter(Mandatory)][int]$Height,
        [Parameter(Mandatory)][double]$XRatio,
        [Parameter(Mandatory)][double]$YRatio
    )
    [pscustomobject]@{
        X = [int][Math]::Round($Width * $XRatio)
        Y = [int][Math]::Round($Height * $YRatio)
    }
}

function Assert-AdbInputSafe {
    param(
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$Label,
        [switch]$Password
    )

    $pattern = if ($Password) { '^[A-Za-z0-9]+$' } else { '^[A-Za-z0-9._@:/-]+$' }
    if ($Value -notmatch $pattern) {
        $detail = if ($Password) {
            "Use an alphanumeric disposable test password. This avoids adb shell metacharacter corruption."
        } else {
            "Only letters, numbers, dot, underscore, @, colon, slash, and hyphen are supported."
        }
        throw "$Label cannot be entered safely with adb. $detail"
    }
}

function Test-DeviceLocked {
    $window = (Invoke-Adb -Arguments @("shell", "dumpsys", "window") -AllowFailure) -join "`n"
    return $window -match 'mDreamingLockscreen=true|mShowingLockscreen=true|isStatusBarKeyguard=true'
}

function Prepare-Device {
    [void](Invoke-Adb -Arguments @("get-state"))
    [void](Invoke-Adb -Arguments @("shell", "input", "keyevent", "KEYCODE_WAKEUP") -AllowFailure)
    [void](Invoke-Adb -Arguments @("shell", "svc", "power", "stayon", "true") -AllowFailure)
    [void](Invoke-Adb -Arguments @("shell", "wm", "dismiss-keyguard") -AllowFailure)
    [void](Invoke-Adb -Arguments @("shell", "input", "keyevent", "KEYCODE_MENU") -AllowFailure)
    Start-Sleep -Milliseconds 700

    $model = ((Invoke-Adb -Arguments @("shell", "getprop", "ro.product.model")) -join "").Trim()
    $size = ((Invoke-Adb -Arguments @("shell", "wm", "size")) -join " ").Trim()
    $density = ((Invoke-Adb -Arguments @("shell", "wm", "density")) -join " ").Trim()
    $stayAwakeValue = ((Invoke-Adb -Arguments @("shell", "settings", "get", "global", "stay_on_while_plugged_in") -AllowFailure) -join "").Trim()
    $screenTimeoutMs = ((Invoke-Adb -Arguments @("shell", "settings", "get", "system", "screen_off_timeout") -AllowFailure) -join "").Trim()
    $stayAwakeEnabled = $stayAwakeValue -match '^[1-7]$'
    Write-Host "Device ready: model=$model; $size; $density; usbStayAwake=$stayAwakeEnabled; screenTimeoutMs=$screenTimeoutMs"
    if (-not $stayAwakeEnabled) {
        Write-Host "USB stay-awake is unavailable through adb on this firmware. Keep interacting within the timeout, or run -Action OpenDeveloperOptions and enable Stay awake manually."
    }

    if (Test-DeviceLocked) {
        throw "Device is still securely locked. Unlock the pattern manually, leave the screen on, then rerun. The helper never guesses or stores a device unlock pattern."
    }
}

function Open-DeveloperOptions {
    [void](Invoke-Adb -Arguments @("shell", "am", "start", "-W", "-a", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"))
    Write-Host "Developer Options opened. Enable 'Stay awake' (screen will never sleep while charging), then return to Mimeo."
}

function Start-MimeoApp {
    $resolved = (Invoke-Adb -Arguments @(
        "shell", "cmd", "package", "resolve-activity", "--brief",
        "-c", "android.intent.category.LAUNCHER", $PackageId
    )) | Where-Object { $_ -match '/' } | Select-Object -Last 1
    if ([string]::IsNullOrWhiteSpace($resolved)) {
        throw "No launcher activity found for $PackageId. Install the intended APK first."
    }
    [void](Invoke-Adb -Arguments @("shell", "am", "start", "-W", "-n", $resolved.Trim()))
    Start-Sleep -Seconds 2
}

function Dismiss-AutofillUiIfPresent {
    foreach ($attempt in 1..3) {
        $window = (Invoke-Adb -Arguments @("shell", "dumpsys", "window")) -join "`n"
        if ($window -notmatch 'mCurrentFocus=.*Autofill UI') { return }
        [void](Invoke-Adb -Arguments @("shell", "input", "keyevent", "KEYCODE_BACK"))
        Start-Sleep -Milliseconds 500
    }
    throw "Android Autofill UI remained visible after three safe dismiss attempts. Close the password-manager overlay manually."
}

function Get-UiDocument {
    Dismiss-AutofillUiIfPresent
    $safePackage = $PackageId -replace '[^A-Za-z0-9._-]', '_'
    $remotePath = "/sdcard/$safePackage-device-verify.xml"
    $localPath = Join-Path ([IO.Path]::GetTempPath()) "$safePackage-device-verify.xml"
    [void](Invoke-Adb -Arguments @("shell", "uiautomator", "dump", $remotePath))
    [void](Invoke-Adb -Arguments @("pull", $remotePath, $localPath))
    return [xml](Get-Content -Raw -LiteralPath $localPath)
}

function Get-UiNodes {
    param([Parameter(Mandatory)][xml]$Document)
    return @($Document.SelectNodes("//node"))
}

function Find-UiNodeByText {
    param(
        [Parameter(Mandatory)][xml]$Document,
        [Parameter(Mandatory)][string]$Text,
        [switch]$Contains,
        [switch]$Last
    )
    $matches = Get-UiNodes -Document $Document | Where-Object {
        $value = [string]$_.text
        if ($Contains) { $value -like "*$Text*" } else { $value -eq $Text }
    }
    if ($Last) { return $matches | Select-Object -Last 1 }
    return $matches | Select-Object -First 1
}

function Test-UiText {
    param(
        [Parameter(Mandatory)][xml]$Document,
        [Parameter(Mandatory)][string]$Text,
        [switch]$Contains
    )
    return $null -ne (Find-UiNodeByText -Document $Document -Text $Text -Contains:$Contains)
}

function Invoke-UiTap {
    param([Parameter(Mandatory)]$Node)
    $point = ConvertFrom-UiBounds -Bounds ([string]$Node.bounds)
    [void](Invoke-Adb -Arguments @("shell", "input", "tap", [string]$point.X, [string]$point.Y))
}

function Get-EditableFields {
    param([Parameter(Mandatory)][xml]$Document)
    return @(Get-UiNodes -Document $Document | Where-Object {
        [string]$_.class -eq "android.widget.EditText"
    } | Sort-Object { (ConvertFrom-UiBounds -Bounds ([string]$_.bounds)).Y })
}

function New-DeleteKeyArguments {
    param([Parameter(Mandatory)][ValidateRange(1, 4096)][int]$Length)
    $arguments = @("shell", "input", "keyevent")
    $arguments += (1..$Length | ForEach-Object { "KEYCODE_DEL" })
    return $arguments
}

function Clear-AndSetField {
    param(
        [Parameter(Mandatory)][int]$FieldIndex,
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$Label,
        [switch]$Password
    )

    Assert-AdbInputSafe -Value $Value -Label $Label -Password:$Password
    $cleared = $false
    foreach ($attempt in 1..5) {
        $document = Get-UiDocument
        $fields = @(Get-EditableFields -Document $document)
        if ($fields.Count -le $FieldIndex) {
            throw "$Label field disappeared while preparing input."
        }
        $node = $fields[$FieldIndex]
        $currentLength = ([string]$node.text).Length
        if ($currentLength -eq 0) {
            # Confirm a second stable empty observation. We send exactly the observed number
            # of deletes, so an empty field means no surplus delete events can reach new text.
            Start-Sleep -Milliseconds 500
            $confirmFields = @(Get-EditableFields -Document (Get-UiDocument))
            if ($confirmFields.Count -gt $FieldIndex -and ([string]$confirmFields[$FieldIndex].text).Length -eq 0) {
                $cleared = $true
                break
            }
            continue
        }

        Invoke-UiTap -Node $node
        Start-Sleep -Milliseconds 250
        Dismiss-AutofillUiIfPresent
        [void](Invoke-Adb -Arguments @("shell", "input", "keyevent", "KEYCODE_MOVE_END"))
        $deleteArguments = New-DeleteKeyArguments -Length $currentLength
        [void](Invoke-Adb -Arguments $deleteArguments)
        Start-Sleep -Milliseconds 500
    }
    if (-not $cleared) {
        throw "$Label field could not be cleared and stabilized. Submission was stopped."
    }

    $readyFields = @(Get-EditableFields -Document (Get-UiDocument))
    Invoke-UiTap -Node $readyFields[$FieldIndex]
    Start-Sleep -Milliseconds 250
    Dismiss-AutofillUiIfPresent
    [void](Invoke-Adb -Arguments @("shell", "input", "text", $Value) -Sensitive:$Password)
    Start-Sleep -Milliseconds 750

    $enteredFields = @(Get-EditableFields -Document (Get-UiDocument))
    if ($enteredFields.Count -le $FieldIndex) { throw "$Label field disappeared after input." }
    $enteredText = [string]$enteredFields[$FieldIndex].text
    $matches = if ($Password) { $enteredText.Length -eq $Value.Length } else { $enteredText -eq $Value }
    if (-not $matches) {
        $detail = if ($Password) { "length did not match" } else { "text did not match" }
        throw "$Label entry $detail the requested value. Submission was stopped."
    }
}

function Get-PlainPassword {
    $fromEnvironment = [Environment]::GetEnvironmentVariable($PasswordEnvironmentVariable)
    if (-not [string]::IsNullOrEmpty($fromEnvironment)) {
        return $fromEnvironment
    }

    $secure = Read-Host "Disposable test-account password" -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

function Assert-ServerReachableFromDevice {
    try {
        $response = Invoke-Adb -Arguments @(
            "shell", "curl", "--head", "--silent", "--show-error",
            "--connect-timeout", "5", "--max-time", "8", $ServerUrl
        )
        if (($response -join "`n") -notmatch '^HTTP/') {
            throw "No HTTP status line returned."
        }
    } catch {
        throw "Backend is not reachable from the Android device at $ServerUrl. Verify the canonical runtime and Tailscale/Wi-Fi path before entering credentials. No sign-in submission was attempted."
    }
    Write-Host "Device HTTPS preflight passed: $ServerUrl"
}

function Wait-ForUiState {
    param(
        [Parameter(Mandatory)][scriptblock]$Satisfied,
        [int]$TimeoutSeconds = $WaitSeconds
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $document = Get-UiDocument
        if (& $Satisfied $document) { return $document }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    return $document
}

function Invoke-MimeoSignIn {
    if ([string]::IsNullOrWhiteSpace($Username)) {
        $script:Username = Read-Host "Disposable test-account username"
    }
    Assert-AdbInputSafe -Value $ServerUrl -Label "Server URL"
    Assert-AdbInputSafe -Value $Username -Label "Username"

    $document = Get-UiDocument
    if (-not (Test-UiText -Document $document -Text "Sign in to Mimeo")) {
        Write-Host "Sign-in screen is not present; keeping the current authenticated session."
        return
    }
    Assert-ServerReachableFromDevice

    $editFields = @(Get-EditableFields -Document $document)
    $nonPasswordFields = @($editFields | Where-Object { [string]$_.password -ne "true" })
    $passwordField = $editFields | Where-Object { [string]$_.password -eq "true" } | Select-Object -First 1
    if ($nonPasswordFields.Count -lt 2 -or $null -eq $passwordField) {
        throw "Could not identify the server, username, and password fields from the UI hierarchy. Capture evidence before adding a device-specific fallback."
    }

    $plainPassword = Get-PlainPassword
    try {
        Clear-AndSetField -FieldIndex 0 -Value $ServerUrl -Label "Server URL"
        Clear-AndSetField -FieldIndex 1 -Value $Username -Label "Username"
        Clear-AndSetField -FieldIndex 2 -Value $plainPassword -Label "Password" -Password

        # Verify the hierarchy received the complete values before submitting. This catches
        # delayed deletes or dropped input without revealing the password itself.
        $enteredDocument = Get-UiDocument
        $enteredFields = @(Get-EditableFields -Document $enteredDocument)
        $enteredNonPassword = @($enteredFields | Where-Object { [string]$_.password -ne "true" })
        $enteredPassword = $enteredFields | Where-Object { [string]$_.password -eq "true" } | Select-Object -First 1
        if ($enteredNonPassword.Count -lt 2 -or ([string]$enteredNonPassword[0].text) -ne $ServerUrl -or
            ([string]$enteredNonPassword[1].text) -ne $Username) {
            throw "Server or username entry did not match the requested value. Submission was stopped."
        }
        if ($null -eq $enteredPassword -or ([string]$enteredPassword.text).Length -ne $plainPassword.Length) {
            throw "Password entry length did not match the supplied disposable password. Submission was stopped without logging the value."
        }
    } finally {
        $plainPassword = $null
    }

    [void](Invoke-Adb -Arguments @("shell", "input", "keyevent", "KEYCODE_BACK"))
    Start-Sleep -Milliseconds 500
    $document = Get-UiDocument
    $signIn = Find-UiNodeByText -Document $document -Text "Sign In" -Last
    if ($null -eq $signIn) { throw "Sign In action not found after entering credentials." }
    Invoke-UiTap -Node $signIn

    $document = Wait-ForUiState -Satisfied {
        param($ui)
        (Test-UiText -Document $ui -Text "Mimeo") -or
            (Test-UiText -Document $ui -Text "Later") -or
            (Test-UiText -Document $ui -Text "Couldn't reach server" -Contains) -or
            (Test-UiText -Document $ui -Text "Invalid username or password" -Contains)
    }
    if (Test-UiText -Document $document -Text "Invalid username or password" -Contains) {
        throw "Mimeo rejected the credentials. The helper did not log or persist the password."
    }
    if (Test-UiText -Document $document -Text "Couldn't reach server" -Contains) {
        throw "Mimeo could not reach the backend after credential entry. Re-run the device HTTPS preflight before retrying; the password was not logged or persisted by the helper."
    }
    if ((Test-DeviceLocked) -or -not (
        (Test-UiText -Document $document -Text "Mimeo") -or
        (Test-UiText -Document $document -Text "Later")
    )) {
        throw "Sign-in did not reach the authenticated app before timeout. Check device lock state and capture evidence before retrying."
    }

    $later = Find-UiNodeByText -Document $document -Text "Later"
    if ($null -ne $later) {
        Invoke-UiTap -Node $later
        Start-Sleep -Seconds 1
    }
    Write-Host "Sign-in completed for $Username on $ServerUrl."
}

function Open-UpNext {
    $document = Get-UiDocument
    if (Test-UiText -Document $document -Text "Sign in to Mimeo") {
        throw "The app is signed out. Run with -Action SignInAndOpenUpNext."
    }

    $hasPageMarker = (Test-UiText -Document $document -Text "No active session. Open an item to start one.") -or
        (Test-UiText -Document $document -Text "Clear upcoming") -or
        (Test-UiText -Document $document -Text "Earlier in queue")
    if (-not $hasPageMarker) {
        $menu = Find-UiNodeByText -Document $document -Text "☰"
        if ($null -ne $menu) {
            Invoke-UiTap -Node $menu
            Start-Sleep -Milliseconds 500
            $document = Get-UiDocument
        }

        $candidates = @(Get-UiNodes -Document $document | Where-Object { [string]$_.text -eq "Up Next" })
        if ($candidates.Count -eq 0) { throw "Up Next navigation target was not found." }
        # The open drawer and page header can both expose exact "Up Next" text.
        # Drawer navigation is lower on screen, including on the OnePlus 7T.
        $target = $candidates | Sort-Object {
            (ConvertFrom-UiBounds -Bounds ([string]$_.bounds)).Y
        } -Descending | Select-Object -First 1
        Invoke-UiTap -Node $target
        Start-Sleep -Seconds 2
    }
    Write-Host "Up Next opened."
}

function Resolve-EvidenceDirectory {
    if (-not [string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
        return [IO.Path]::GetFullPath($EvidenceDirectory)
    }
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    return Join-Path ([IO.Path]::GetTempPath()) "MimeoAndroidDeviceVerification\$stamp"
}

function Save-DeviceEvidence {
    $directory = Resolve-EvidenceDirectory
    [void](New-Item -ItemType Directory -Force -Path $directory)
    $remoteXml = "/sdcard/mimeo-device-verification.xml"
    $remotePng = "/sdcard/mimeo-device-verification.png"
    [void](Invoke-Adb -Arguments @("shell", "uiautomator", "dump", $remoteXml))
    [void](Invoke-Adb -Arguments @("shell", "screencap", "-p", $remotePng))
    [void](Invoke-Adb -Arguments @("pull", $remoteXml, (Join-Path $directory "ui.xml")))
    [void](Invoke-Adb -Arguments @("pull", $remotePng, (Join-Path $directory "screen.png")))
    Invoke-Adb -Arguments @("shell", "dumpsys", "window") | Set-Content -LiteralPath (Join-Path $directory "window.txt")
    Invoke-Adb -Arguments @("shell", "dumpsys", "package", $PackageId) |
        Select-String -Pattern 'versionName=|versionCode=|firstInstallTime=|lastUpdateTime=' |
        Set-Content -LiteralPath (Join-Path $directory "package.txt")
    Invoke-Adb -Arguments @("shell", "getprop", "ro.product.model") | Set-Content -LiteralPath (Join-Path $directory "model.txt")
    Invoke-Adb -Arguments @("shell", "wm", "size") | Set-Content -LiteralPath (Join-Path $directory "size.txt")
    Write-Host "Evidence saved outside the repository: $directory"
    return $directory
}

function Show-DeviceStatus {
    $model = ((Invoke-Adb -Arguments @("shell", "getprop", "ro.product.model")) -join "").Trim()
    $size = ((Invoke-Adb -Arguments @("shell", "wm", "size")) -join " ").Trim()
    $focus = (Invoke-Adb -Arguments @("shell", "dumpsys", "window")) |
        Select-String -Pattern 'mCurrentFocus=' | Select-Object -First 1
    $package = Invoke-Adb -Arguments @("shell", "dumpsys", "package", $PackageId)
    $version = $package | Select-String -Pattern 'versionName=|versionCode=' | Select-Object -First 2
    Write-Host "model=$model; $size; locked=$(Test-DeviceLocked)"
    $version | ForEach-Object { Write-Host $_.Line.Trim() }
    if ($null -ne $focus) { Write-Host $focus.Line.Trim() }
}

function Assert-NoActiveUpNext {
    Open-UpNext
    $document = Get-UiDocument
    if (Test-UiText -Document $document -Text "No active session. Open an item to start one.") {
        throw "Android has no local Up Next session. This is not the expected populated authoritative no-active state."
    }
    if (Test-UiText -Document $document -Text "Now Playing") {
        throw "A Now Playing section is visible even though the authoritative session should have no active item."
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedItemTitle) -and
        -not (Test-UiText -Document $document -Text $ExpectedItemTitle -Contains)) {
        throw "Expected Up Next item was not visible: $ExpectedItemTitle"
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedSourceLabel) -and
        -not (Test-UiText -Document $document -Text $ExpectedSourceLabel -Contains)) {
        throw "Expected source label was not visible: $ExpectedSourceLabel"
    }
    Write-Host "Verified populated Up Next with no active Now Playing pointer."
    [void](Save-DeviceEvidence)
}

function Invoke-SelfTest {
    $field = ConvertFrom-UiBounds -Bounds "[146,1057][934,1239]"
    if ($field.X -ne 540 -or $field.Y -ne 1148) { throw "Bounds center self-test failed." }

    $onePlusUsername = Get-FallbackPoint -Width 1080 -Height 2287 -XRatio 0.5 -YRatio 0.502
    $onePlusPassword = Get-FallbackPoint -Width 1080 -Height 2287 -XRatio 0.5 -YRatio 0.596
    $onePlusSignIn = Get-FallbackPoint -Width 1080 -Height 2287 -XRatio 0.5 -YRatio 0.792
    if ($onePlusUsername.X -ne 540 -or $onePlusUsername.Y -notin 1147, 1148) { throw "OnePlus username fallback self-test failed." }
    if ($onePlusPassword.Y -notin 1363, 1364) { throw "OnePlus password fallback self-test failed." }
    if ($onePlusSignIn.Y -notin 1811, 1812) { throw "OnePlus Sign In fallback self-test failed." }

    Assert-AdbInputSafe -Value "https://example.test" -Label "Server URL"
    Assert-AdbInputSafe -Value "test-user_01" -Label "Username"
    Assert-AdbInputSafe -Value "Abc123456" -Label "Password" -Password
    $unsafeRejected = $false
    try { Assert-AdbInputSafe -Value "not safe!" -Label "Password" -Password } catch { $unsafeRejected = $true }
    if (-not $unsafeRejected) { throw "Unsafe password self-test failed." }
    $secret = "NeverPrintMe123"
    $summary = Get-AdbCommandSummary -Arguments @("shell", "input", "text", $secret) -Sensitive
    if ($summary.Contains($secret) -or -not $summary.Contains("redacted")) { throw "Sensitive adb summary self-test failed." }
    $deleteArguments = New-DeleteKeyArguments -Length 12
    if ($deleteArguments.Count -ne 15 -or @($deleteArguments | Where-Object { $_ -eq "KEYCODE_DEL" }).Count -ne 12) {
        throw "Exact delete-count self-test failed."
    }
    Write-Host "Self-test passed: semantic bounds, OnePlus 7T 1080x2287 fallbacks, and safe adb input rules."
}

if ($Action -eq "SelfTest") {
    Invoke-SelfTest
    exit 0
}

Assert-AdbTarget

switch ($Action) {
    "Prepare" {
        Prepare-Device
    }
    "OpenDeveloperOptions" {
        Prepare-Device
        Open-DeveloperOptions
    }
    "Status" {
        Show-DeviceStatus
    }
    "SignIn" {
        Prepare-Device
        Start-MimeoApp
        Invoke-MimeoSignIn
    }
    "OpenUpNext" {
        Prepare-Device
        Start-MimeoApp
        Open-UpNext
    }
    "SignInAndOpenUpNext" {
        Prepare-Device
        Start-MimeoApp
        Invoke-MimeoSignIn
        Open-UpNext
        [void](Save-DeviceEvidence)
    }
    "Capture" {
        Prepare-Device
        [void](Save-DeviceEvidence)
    }
    "VerifyNoActiveUpNext" {
        Prepare-Device
        Start-MimeoApp
        Assert-NoActiveUpNext
    }
}
