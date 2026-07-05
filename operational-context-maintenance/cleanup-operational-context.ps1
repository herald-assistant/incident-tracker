[CmdletBinding()]
param(
    [string[]] $CatalogRoot = @("src/main/resources/operational-context"),
    [string] $ReportPath = (Join-Path $PSScriptRoot "cleanup-operational-context-report.md"),
    [switch] $Apply
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$yamlLegacyKeys = @(
    "responsibilities",
    "handoffHints",
    "responsibilityStatus",
    "responsibilityEvidenceType",
    "routeTo",
    "partnerTeams",
    "firstResponderTeamIds",
    "partnerTeamIds",
    "defaultRouteLabel"
)

function Resolve-CatalogRoot {
    param([string] $Path)

    if ([System.IO.Path]::IsPathRooted($Path)) {
        return [System.IO.Path]::GetFullPath($Path)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $repoRoot $Path))
}

function Indent-Length {
    param([string] $Line)

    if ($Line -match '^(\s*)') {
        return $matches[1].Length
    }

    return 0
}

function Get-Scalar {
    param(
        [string[]] $Lines,
        [string] $Key
    )

    foreach ($line in $Lines) {
        if ($line -match "^\s*$([regex]::Escape($Key)):\s*(.+?)\s*$") {
            return $matches[1].Trim().Trim('"').Trim("'")
        }
    }

    return ""
}

function Parse-ResponsibilityItems {
    param([string[]] $BlockLines)

    $items = [System.Collections.Generic.List[hashtable]]::new()
    $current = $null

    foreach ($line in $BlockLines) {
        if ($line -match '^\s*-\s+([A-Za-z0-9_]+):\s*(.*?)\s*$') {
            if ($null -ne $current) {
                $items.Add($current)
            }
            $current = @{}
            $current[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
            continue
        }

        if ($null -ne $current -and $line -match '^\s+([A-Za-z0-9_]+):\s*(.*?)\s*$') {
            $current[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'")
        }
    }

    if ($null -ne $current) {
        $items.Add($current)
    }

    return @($items)
}

function Find-ChildBlock {
    param(
        [string[]] $Lines,
        [int] $Start,
        [int] $End,
        [string] $Key,
        [int] $Indent
    )

    for ($index = $Start; $index -lt $End; $index++) {
        if ($Lines[$index] -match "^ {$Indent}$([regex]::Escape($Key)):\s*(?:.*)$") {
            $blockEnd = $index + 1
            while ($blockEnd -lt $End) {
                $candidate = $Lines[$blockEnd]
                if ($candidate.Trim().Length -eq 0) {
                    $blockEnd++
                    continue
                }
                if ((Indent-Length $candidate) -le $Indent) {
                    break
                }
                $blockEnd++
            }

            return [pscustomobject]@{
                Start = $index
                End = $blockEnd
                Lines = @($Lines[$index..($blockEnd - 1)])
            }
        }
    }

    return $null
}

function Ownership-AllowedRoles {
    param([string] $FileName)

    if ($FileName -eq "systems.yml") {
        return @("owner", "system-owner")
    }
    if ($FileName -eq "bounded-contexts.yml") {
        return @("owner", "domain-owner", "bounded-context-owner")
    }

    return @()
}

function Ownership-SourceType {
    param([string] $FileName)

    if ($FileName -eq "systems.yml") {
        return "system"
    }
    if ($FileName -eq "bounded-contexts.yml") {
        return "bounded-context"
    }

    return ""
}

function Build-OwnershipLines {
    param(
        [string] $Indent,
        [hashtable] $Responsibility
    )

    $teamId = $Responsibility["teamId"]
    $confidence = if ($Responsibility.ContainsKey("confidence") -and $Responsibility["confidence"]) {
        $Responsibility["confidence"]
    } else {
        "medium"
    }
    $evidence = if ($Responsibility.ContainsKey("evidence") -and $Responsibility["evidence"]) {
        $Responsibility["evidence"]
    } else {
        "legacy responsibilities"
    }

    return @(
        "$($Indent)ownership:",
        "$($Indent)  ownerTeamIds:",
        "$($Indent)    - $teamId",
        "$($Indent)  ownerLabel: `"`"",
        "$($Indent)  ownershipStatus: explicit",
        "$($Indent)  confidence: $confidence",
        "$($Indent)  source: migrated-from-responsibilities",
        "$($Indent)  notes:",
        "$($Indent)    - Migrated from legacy responsibilities by cleanup-operational-context.ps1; confirm accountable ownership.",
        "$($Indent)    - Legacy evidence: $evidence"
    )
}

function Add-DeterministicOwnership {
    param(
        [string[]] $Lines,
        [string] $FileName,
        [System.Collections.Generic.List[object]] $Changes
    )

    $allowedRoles = Ownership-AllowedRoles $FileName
    if ($allowedRoles.Count -eq 0) {
        return $Lines
    }

    $entityType = Ownership-SourceType $FileName
    $entryStarts = [System.Collections.Generic.List[int]]::new()
    for ($index = 0; $index -lt $Lines.Count; $index++) {
        if ($Lines[$index] -match '^\s+-\s+id:\s*(.+?)\s*$') {
            $entryStarts.Add($index)
        }
    }

    if ($entryStarts.Count -eq 0) {
        return $Lines
    }

    $result = [System.Collections.Generic.List[string]]::new()
    $cursor = 0

    for ($entryIndex = 0; $entryIndex -lt $entryStarts.Count; $entryIndex++) {
        $start = $entryStarts[$entryIndex]
        $end = if ($entryIndex + 1 -lt $entryStarts.Count) { $entryStarts[$entryIndex + 1] } else { $Lines.Count }

        while ($cursor -lt $start) {
            $result.Add($Lines[$cursor])
            $cursor++
        }

        $entryLines = @($Lines[$start..($end - 1)])
        $entryIndentLength = Indent-Length $entryLines[0]
        $childIndentLength = $entryIndentLength + 2
        $childIndent = " " * $childIndentLength
        $entityId = ""
        if ($entryLines[0] -match '^\s+-\s+id:\s*(.+?)\s*$') {
            $entityId = $matches[1].Trim().Trim('"').Trim("'")
        }

        $hasOwnership = $false
        foreach ($line in $entryLines) {
            if ($line -match "^ {$childIndentLength}ownership:\s*") {
                $hasOwnership = $true
                break
            }
        }

        $responsibilities = Find-ChildBlock $Lines $start $end "responsibilities" $childIndentLength
        if (-not $hasOwnership -and $null -ne $responsibilities) {
            $items = Parse-ResponsibilityItems $responsibilities.Lines
            $candidates = @($items | Where-Object {
                $_.ContainsKey("teamId") -and $_["teamId"] -and
                $_.ContainsKey("targetType") -and $_["targetType"] -eq $entityType -and
                $_.ContainsKey("targetId") -and $_["targetId"] -eq $entityId -and
                $_.ContainsKey("role") -and $allowedRoles -contains $_["role"]
            })

            if ($candidates.Count -eq 1) {
                $insertAt = $responsibilities.Start - $start
                for ($local = 0; $local -lt $insertAt; $local++) {
                    $result.Add($entryLines[$local])
                }
                foreach ($ownershipLine in (Build-OwnershipLines $childIndent $candidates[0])) {
                    $result.Add($ownershipLine)
                }
                for ($local = $insertAt; $local -lt $entryLines.Count; $local++) {
                    $result.Add($entryLines[$local])
                }
                $Changes.Add([pscustomobject]@{
                    File = $FileName
                    Action = "migrate-ownership"
                    Detail = "$entityId <- $($candidates[0]["teamId"])"
                })
            } elseif ($items.Count -gt 0) {
                foreach ($line in $entryLines) {
                    $result.Add($line)
                }
                $Changes.Add([pscustomobject]@{
                    File = $FileName
                    Action = "needs-open-question"
                    Detail = "$entityId has ambiguous legacy responsibilities"
                })
            } else {
                foreach ($line in $entryLines) {
                    $result.Add($line)
                }
            }
        } else {
            foreach ($line in $entryLines) {
                $result.Add($line)
            }
        }

        $cursor = $end
    }

    while ($cursor -lt $Lines.Count) {
        $result.Add($Lines[$cursor])
        $cursor++
    }

    return @($result)
}

function Remove-YamlLegacyBlocks {
    param(
        [string[]] $Lines,
        [string] $FileName,
        [System.Collections.Generic.List[object]] $Changes
    )

    $result = [System.Collections.Generic.List[string]]::new()
    $index = 0

    while ($index -lt $Lines.Count) {
        $line = $Lines[$index]
        if ($line -match '^(\s*)([A-Za-z0-9_]+):\s*(.*)$' -and $yamlLegacyKeys -contains $matches[2]) {
            $key = $matches[2]
            $indent = $matches[1].Length
            $start = $index
            $index++

            while ($index -lt $Lines.Count) {
                $candidate = $Lines[$index]
                if ($candidate.Trim().Length -eq 0) {
                    $index++
                    continue
                }
                if ((Indent-Length $candidate) -le $indent) {
                    break
                }
                $index++
            }

            $Changes.Add([pscustomobject]@{
                File = $FileName
                Action = "remove-yaml"
                Detail = "$key at line $($start + 1)"
            })
            continue
        }

        $result.Add($line)
        $index++
    }

    return @($result)
}

function Remove-MarkdownLegacySections {
    param(
        [string[]] $Lines,
        [string] $FileName,
        [System.Collections.Generic.List[object]] $Changes
    )

    $result = [System.Collections.Generic.List[string]]::new()
    $index = 0

    while ($index -lt $Lines.Count) {
        $line = $Lines[$index]
        if ($line -match '^\*\*(Route to|Partner teams):\*\*') {
            $section = $matches[1]
            $start = $index
            $index++

            while ($index -lt $Lines.Count) {
                $candidate = $Lines[$index]
                if ($candidate -match '^#{1,6}\s+' -or $candidate -match '^\*\*[^*]+:\*\*') {
                    break
                }
                $index++
            }

            $Changes.Add([pscustomobject]@{
                File = $FileName
                Action = "remove-markdown"
                Detail = "$section at line $($start + 1)"
            })
            continue
        }

        $result.Add($line)
        $index++
    }

    return @($result)
}

function Write-Report {
    param(
        [object[]] $Changes,
        [string[]] $Roots,
        [bool] $Applied
    )

    $lines = [System.Collections.Generic.List[string]]::new()
    $mode = if ($Applied) { "apply" } else { "dry-run" }
    $lines.Add("# Operational context cleanup report")
    $lines.Add("")
    $lines.Add("Mode: ``$mode``")
    $lines.Add("")
    $lines.Add("Catalog roots:")
    foreach ($root in $Roots) {
        $lines.Add("- ``$root``")
    }
    $lines.Add("")
    $lines.Add("Changes: $($Changes.Count)")

    if ($Changes.Count -gt 0) {
        $lines.Add("")
        $lines.Add("| File | Action | Detail |")
        $lines.Add("| --- | --- | --- |")
        foreach ($change in $Changes) {
            $file = ([string] $change.File).Replace("|", "\|")
            $action = ([string] $change.Action).Replace("|", "\|")
            $detail = ([string] $change.Detail).Replace("|", "\|")
            $lines.Add("| $file | $action | $detail |")
        }
    }

    $outputDirectory = Split-Path -Parent $ReportPath
    if ($outputDirectory -and -not (Test-Path -Path $outputDirectory)) {
        New-Item -Path $outputDirectory -ItemType Directory | Out-Null
    }

    $lines | Set-Content -Path $ReportPath -Encoding UTF8
}

$resolvedRoots = @($CatalogRoot | ForEach-Object { Resolve-CatalogRoot $_ })
$allChanges = [System.Collections.Generic.List[object]]::new()

foreach ($root in $resolvedRoots) {
    if (-not (Test-Path -Path $root -PathType Container)) {
        throw "Catalog root does not exist: $root"
    }

    $files = Get-ChildItem -Path $root -File -Include *.yml,*.yaml,*.md -Recurse
    foreach ($file in $files) {
        $relativeFile = Resolve-Path -Path $file.FullName -Relative
        $fileChanges = [System.Collections.Generic.List[object]]::new()
        $original = @(Get-Content -Path $file.FullName)
        $updated = $original

        if ($file.Extension -in @(".yml", ".yaml")) {
            $updated = Add-DeterministicOwnership $updated $file.Name $fileChanges
            $updated = Remove-YamlLegacyBlocks $updated $file.Name $fileChanges
        } elseif ($file.Extension -eq ".md") {
            $updated = Remove-MarkdownLegacySections $updated $file.Name $fileChanges
        }

        if ($fileChanges.Count -gt 0) {
            foreach ($change in $fileChanges) {
                $allChanges.Add([pscustomobject]@{
                    File = $relativeFile
                    Action = $change.Action
                    Detail = $change.Detail
                })
            }

            if ($Apply) {
                $updated | Set-Content -Path $file.FullName -Encoding UTF8
            }
        }
    }
}

Write-Report -Changes @($allChanges) -Roots $resolvedRoots -Applied ([bool] $Apply)

$modeLabel = if ($Apply) { "applied" } else { "dry-run" }
Write-Output "Operational context cleanup $modeLabel. Changes: $($allChanges.Count). Report: $ReportPath"
