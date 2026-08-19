[CmdletBinding()]
param(
    [string]$JarPath,
    [string]$WorkspaceDirectory,
    [string]$JavaPath = "java",
    [switch]$DryRun,
    [switch]$NoMenu,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ApplicationArguments = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
}
catch {}

$scriptDirectory = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($scriptDirectory)) {
    $scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
}

if ([string]::IsNullOrWhiteSpace($WorkspaceDirectory)) {
    $WorkspaceDirectory = Join-Path $scriptDirectory "tdw-data"
}

function Get-RunnableJarCandidates {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Directory
    )

    if (-not (Test-Path -LiteralPath $Directory -PathType Container)) {
        return @()
    }

    return @(
        Get-ChildItem -LiteralPath $Directory -Filter "*.jar" -File |
            Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
            Sort-Object Name
    )
}

function Test-InteractiveConsole {
    try {
        return -not [Console]::IsInputRedirected -and -not [Console]::IsOutputRedirected
    }
    catch {
        return $false
    }
}

function Get-TdwVersion {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.FileInfo]$Jar
    )

    $version = $Jar.BaseName -replace '^(team-delivery-workspace|incident-tracker|tdw)-', ''
    if ($version -eq $Jar.BaseName) {
        return "dev"
    }

    return $version
}

function Get-TdwBanner {
    param(
        [Parameter(Mandatory = $true)]
        [System.IO.FileInfo]$Jar
    )

    $bannerText = $null
    $sourceBannerPath = Join-Path $scriptDirectory "src\main\resources\banner.txt"
    if (Test-Path -LiteralPath $sourceBannerPath -PathType Leaf) {
        $bannerText = Get-Content -LiteralPath $sourceBannerPath -Raw -Encoding UTF8
    }
    else {
        try {
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            $archive = [System.IO.Compression.ZipFile]::OpenRead($Jar.FullName)
            try {
                $entry = $archive.GetEntry("BOOT-INF/classes/banner.txt")
                if ($null -ne $entry) {
                    $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
                    try {
                        $bannerText = $reader.ReadToEnd()
                    }
                    finally {
                        $reader.Dispose()
                    }
                }
            }
            finally {
                $archive.Dispose()
            }
        }
        catch {
            $bannerText = $null
        }
    }

    if ([string]::IsNullOrWhiteSpace($bannerText)) {
        return "TEAM DELIVERY WORKSPACE"
    }

    $renderableBanner = [regex]::Replace(
        $bannerText,
        '\$\{tdw\.application\.version:\$\{application\.version:[^}]*\}\}',
        (Get-TdwVersion -Jar $Jar)
    )
    return $renderableBanner.TrimEnd()
}

function Get-BannerConsoleColor {
    param(
        [Parameter(Mandatory = $true)]
        [string]$AnsiColor
    )

    switch ($AnsiColor) {
        "BRIGHT_CYAN" { return "Cyan" }
        "BRIGHT_BLUE" { return "Blue" }
        "BRIGHT_WHITE" { return "White" }
        "BLUE" { return "DarkBlue" }
        "CYAN" { return "DarkCyan" }
        "WHITE" { return "Gray" }
        "DEFAULT" { return "Gray" }
        default { return "Gray" }
    }
}

function Write-TdwBanner {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Banner
    )

    $currentColor = "Cyan"
    foreach ($line in ($Banner -split '\r?\n')) {
        $position = 0
        $hasVisibleText = $false
        $colorTokens = [regex]::Matches($line, '\$\{AnsiColor\.([A-Z_]+)\}')

        foreach ($colorToken in $colorTokens) {
            if ($colorToken.Index -gt $position) {
                $text = $line.Substring($position, $colorToken.Index - $position)
                Write-Host $text -NoNewline -ForegroundColor $currentColor
                $hasVisibleText = $true
            }

            $currentColor = Get-BannerConsoleColor -AnsiColor $colorToken.Groups[1].Value
            $position = $colorToken.Index + $colorToken.Length
        }

        if ($position -lt $line.Length) {
            Write-Host $line.Substring($position) -NoNewline -ForegroundColor $currentColor
            $hasVisibleText = $true
        }
        if ($hasVisibleText -or $colorTokens.Count -eq 0) {
            Write-Host ""
        }
    }
}

function Get-TdwServerUrl {
    param(
        [string[]]$Arguments
    )

    $port = "8080"
    $contextPath = ""
    $httpsEnabled = $false

    foreach ($argument in $Arguments) {
        if ($argument -match '^--server\.port=(.+)$') {
            $port = $Matches[1]
        }
        elseif ($argument -match '^--server\.servlet\.context-path=(.+)$') {
            $contextPath = $Matches[1].TrimEnd('/')
        }
        elseif ($argument -match '^--server\.ssl\.enabled=true$') {
            $httpsEnabled = $true
        }
    }

    $scheme = if ($httpsEnabled) { "https" } else { "http" }
    return "${scheme}://localhost:${port}${contextPath}/"
}

function Get-LogTail {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [int]$LineCount = 20
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return @()
    }

    return @(Get-Content -LiteralPath $Path -Tail $LineCount -Encoding UTF8 -ErrorAction SilentlyContinue)
}

function Start-TdwServer {
    try {
        $processParameters = @{
            FilePath               = $JavaPath
            ArgumentList           = $javaArguments
            NoNewWindow            = $true
            PassThru               = $true
            RedirectStandardOutput = $standardOutputLogPath
            RedirectStandardError  = $standardErrorLogPath
        }
        $script:serverProcess = Start-Process @processParameters
        $script:serverState = "STARTING"
        $script:serverStartedAt = [DateTimeOffset]::Now
        $script:serverApplicationPid = $null
        $script:serverUrl = Get-TdwServerUrl -Arguments $ApplicationArguments
        $script:lastExitCode = $null
        $script:lastLauncherError = $null
    }
    catch {
        $script:serverProcess = $null
        $script:serverState = "FAILED"
        $script:lastLauncherError = $_.Exception.Message
    }
}

function Stop-TdwServer {
    if ($null -eq $script:serverProcess) {
        $script:serverState = "STOPPED"
        return
    }

    try {
        $script:serverProcess.Refresh()
        if (-not $script:serverProcess.HasExited) {
            $launcherProcessId = $script:serverProcess.Id
            $taskkillPath = Join-Path $env:SystemRoot "System32\taskkill.exe"
            if (Test-Path -LiteralPath $taskkillPath -PathType Leaf) {
                & $taskkillPath /PID $launcherProcessId /T /F 2>$null | Out-Null
            }

            $script:serverProcess.Refresh()
            if (-not $script:serverProcess.HasExited) {
                Stop-Process -Id $launcherProcessId -Force -ErrorAction Stop
            }
            $null = $script:serverProcess.WaitForExit(5000)
        }

        if ($null -ne $script:serverApplicationPid -and
            $script:serverApplicationPid -ne $script:serverProcess.Id) {
            $applicationProcess = Get-Process -Id $script:serverApplicationPid -ErrorAction SilentlyContinue
            if ($null -ne $applicationProcess) {
                Stop-Process -Id $script:serverApplicationPid -Force -ErrorAction Stop
                $null = $applicationProcess.WaitForExit(5000)
            }
        }
        if ($script:serverProcess.HasExited) {
            $script:lastExitCode = $script:serverProcess.ExitCode
        }
    }
    catch {
        $script:lastLauncherError = $_.Exception.Message
    }
    finally {
        $script:serverProcess = $null
        $script:serverState = "STOPPED"
        $script:serverStartedAt = $null
        $script:serverApplicationPid = $null
    }
}

function Open-TdwServerInBrowser {
    if ($script:serverState -ne "RUNNING") {
        $script:lastLauncherError = "Serwer nie jest jeszcze uruchomiony."
        return
    }

    try {
        Start-Process -FilePath $script:serverUrl
        $script:lastLauncherError = $null
    }
    catch {
        $script:lastLauncherError = "Nie mozna otworzyc przegladarki: $($_.Exception.Message)"
    }
}

function Update-TdwServerState {
    if ($null -eq $script:serverProcess) {
        return
    }

    try {
        $script:serverProcess.Refresh()
        if ($script:serverProcess.HasExited) {
            $script:lastExitCode = $script:serverProcess.ExitCode
            $script:serverState = if ($script:lastExitCode -eq 0) { "STOPPED" } else { "FAILED" }
            $script:serverStartedAt = $null
            return
        }

        if ($script:serverState -eq "STARTING") {
            $recentOutput = Get-LogTail -Path $standardOutputLogPath -LineCount 100
            $joinedOutput = ($recentOutput -join "`n")

            $startingLine = $recentOutput | Where-Object { $_ -match 'with PID\s+([0-9]+)' } | Select-Object -Last 1
            if ($null -ne $startingLine -and $startingLine -match 'with PID\s+([0-9]+)') {
                $script:serverApplicationPid = [int]$Matches[1]
            }

            if ($joinedOutput -match 'Started\s+\S+\s+in\s') {
                $script:serverState = "RUNNING"
            }

            $tomcatLine = $recentOutput | Where-Object { $_ -match 'Tomcat started on port\s+([0-9]+)' } | Select-Object -Last 1
            if ($null -ne $tomcatLine -and $tomcatLine -match 'Tomcat started on port\s+([0-9]+)') {
                $script:serverUrl = $script:serverUrl -replace '(localhost):[0-9]+', "`$1:$($Matches[1])"
            }
        }
    }
    catch {
        $script:serverState = "FAILED"
        $script:lastLauncherError = $_.Exception.Message
    }
}

function Get-ServerStatusPresentation {
    switch ($script:serverState) {
        "STARTING" { return @{ Text = "URUCHAMIANIE"; Color = "Yellow" } }
        "RUNNING" { return @{ Text = "DZIALA"; Color = "Green" } }
        "FAILED" { return @{ Text = "BLAD"; Color = "Red" } }
        default { return @{ Text = "ZATRZYMANY"; Color = "DarkGray" } }
    }
}

function Get-ServerUptime {
    if ($null -eq $script:serverStartedAt) {
        return "-"
    }

    $elapsed = [DateTimeOffset]::Now - $script:serverStartedAt
    return "{0:00}:{1:00}:{2:00}" -f [Math]::Floor($elapsed.TotalHours), $elapsed.Minutes, $elapsed.Seconds
}

function Write-TdwMenu {
    try {
        Clear-Host
    }
    catch {
        # Some terminal hosts do not support clearing; the menu remains usable.
    }

    Write-TdwBanner -Banner $tdwBanner
    Write-Host ""

    $status = Get-ServerStatusPresentation
    Write-Host "  Status serwera : " -NoNewline
    Write-Host $status.Text -ForegroundColor $status.Color

    $pidText = "-"
    if ($null -ne $script:serverProcess) {
        try {
            if (-not $script:serverProcess.HasExited) {
                $pidText = if ($null -ne $script:serverApplicationPid) {
                    [string]$script:serverApplicationPid
                }
                else {
                    [string]$script:serverProcess.Id
                }
            }
        }
        catch {
            $pidText = "-"
        }
    }

    Write-Host "  PID            : $pidText"
    Write-Host "  Czas dzialania : $(Get-ServerUptime)"
    Write-Host "  Adres          : $script:serverUrl"
    Write-Host "  Log            : $standardOutputLogPath"
    if ($null -ne $script:lastExitCode) {
        Write-Host "  Kod wyjscia    : $script:lastExitCode"
    }
    if (-not [string]::IsNullOrWhiteSpace($script:lastLauncherError)) {
        Write-Host "  Blad launchera : $script:lastLauncherError" -ForegroundColor Red
    }

    Write-Host ""
    Write-Host "  [S] Zatrzymaj   [R] Uruchom ponownie   [O] Otworz w przegladarce" -ForegroundColor White
    Write-Host "  [L] Pokaz/ukryj logi                    [Q] Zabij i wyjdz" -ForegroundColor White

    if ($script:showLogs) {
        Write-Host ""
        Write-Host "  --- Ostatnie logi serwera ---" -ForegroundColor DarkCyan
        foreach ($line in (Get-LogTail -Path $standardOutputLogPath -LineCount 14)) {
            Write-Host "  $line" -ForegroundColor DarkGray
        }

        $errorLines = @(Get-LogTail -Path $standardErrorLogPath -LineCount 6)
        if ($errorLines.Count -gt 0) {
            Write-Host "  --- stderr ---" -ForegroundColor DarkRed
            foreach ($line in $errorLines) {
                Write-Host "  $line" -ForegroundColor Red
            }
        }
    }
}

if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $targetDirectory = Join-Path $scriptDirectory "target"
    $candidateJars = @(Get-RunnableJarCandidates -Directory $scriptDirectory)
    if ($candidateJars.Count -eq 0) {
        $candidateJars = @(Get-RunnableJarCandidates -Directory $targetDirectory)
    }

    if ($candidateJars.Count -eq 0) {
        throw "No runnable JAR found next to run-tdw.ps1 or in target/. Build it with 'mvn -q -DskipTests package' or pass -JarPath explicitly."
    }

    $preferredJar = $candidateJars |
        Where-Object { $_.Name -like "incident-tracker*.jar" -or $_.Name -like "tdw*.jar" } |
        Select-Object -First 1

    if ($null -eq $preferredJar) {
        if ($candidateJars.Count -gt 1) {
            $names = ($candidateJars | ForEach-Object { $_.Name }) -join ", "
            throw "More than one JAR found next to run-tdw.ps1. Pass -JarPath explicitly. Found: $names"
        }
        $preferredJar = $candidateJars[0]
    }

    $JarPath = $preferredJar.FullName
}

$jar = Get-Item -LiteralPath $JarPath
New-Item -ItemType Directory -Path $WorkspaceDirectory -Force | Out-Null

$workspaceFullPath = (Resolve-Path -LiteralPath $WorkspaceDirectory).Path
$jarFullPath = $jar.FullName

Write-Host "TDW JAR: $jarFullPath"
Write-Host "TDW workspace: $workspaceFullPath"

$javaArguments = @(
    "-Dfile.encoding=UTF-8",
    "-jar",
    $jarFullPath,
    "--tdw.workspace.directory=$workspaceFullPath"
) + $ApplicationArguments

if ($DryRun) {
    Write-Host "Dry run: $JavaPath $($javaArguments -join ' ')"
    exit 0
}

if ($NoMenu -or -not (Test-InteractiveConsole)) {
    & $JavaPath @javaArguments
    exit $LASTEXITCODE
}

$logsDirectory = Join-Path $workspaceFullPath "logs"
New-Item -ItemType Directory -Path $logsDirectory -Force | Out-Null
$standardOutputLogPath = Join-Path $logsDirectory "tdw-server.log"
$standardErrorLogPath = Join-Path $logsDirectory "tdw-server-error.log"

$tdwBanner = Get-TdwBanner -Jar $jar
$script:serverUrl = Get-TdwServerUrl -Arguments $ApplicationArguments
$script:serverProcess = $null
$script:serverStartedAt = $null
$script:serverApplicationPid = $null
$script:serverState = "STOPPED"
$script:lastExitCode = $null
$script:lastLauncherError = $null
$script:showLogs = $false
$exitRequested = $false
$lastState = $null

try {
    Start-TdwServer

    while (-not $exitRequested) {
        Update-TdwServerState

        if ($script:serverState -ne $lastState) {
            Write-TdwMenu
            $lastState = $script:serverState
        }

        if ([Console]::KeyAvailable) {
            $key = [Console]::ReadKey($true).Key
            switch ($key) {
                "S" {
                    Stop-TdwServer
                    $lastState = $null
                }
                "R" {
                    Stop-TdwServer
                    Start-TdwServer
                    $lastState = $null
                }
                "L" {
                    $script:showLogs = -not $script:showLogs
                    $lastState = $null
                }
                "O" {
                    Open-TdwServerInBrowser
                    $lastState = $null
                }
                "Q" {
                    Stop-TdwServer
                    $exitRequested = $true
                }
            }
        }
        else {
            Start-Sleep -Milliseconds 200
        }
    }
}
finally {
    Stop-TdwServer
}

exit 0
