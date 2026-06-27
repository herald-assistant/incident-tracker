[CmdletBinding()]
param(
    [string]$JarPath,
    [string]$WorkspaceDirectory,
    [string]$JavaPath = "java",
    [switch]$DryRun,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ApplicationArguments = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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
    "-jar",
    $jarFullPath,
    "--tdw.workspace.directory=$workspaceFullPath"
) + $ApplicationArguments

if ($DryRun) {
    Write-Host "Dry run: $JavaPath $($javaArguments -join ' ')"
    exit 0
}

& $JavaPath @javaArguments
exit $LASTEXITCODE
