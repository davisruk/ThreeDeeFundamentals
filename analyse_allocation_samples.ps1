. .\env-jfr-work.ps1

$allocationFile = "C:\misc\cpas-test\scheduler-testing\dsp-$step-object-allocation-samples.json"

& $jfr print --json `
    --events jdk.ObjectAllocationSample `
    --stack-depth 16 $recording |
    Out-File -Encoding utf8 $allocationFile

$json = Get-Content $allocationFile -Raw | ConvertFrom-Json
$samples = $json.recording.events

$targets = @(
    "LinkedHashMap.newNode",
    "HashMap.resize",
	"StreamSupport.stream"
)

foreach ($target in $targets) {

    $targetClass, $targetMethod = $target.Split(".")

    $matchingSamples = foreach ($sample in $samples) {

        $frames = $sample.values.stackTrace.frames

        $targetFrameIndex = -1

        for ($i = 0; $i -lt $frames.Count; $i++) {

            $className = $frames[$i].method.type.name
            $methodName = $frames[$i].method.name

            if ($className -like "*$targetClass" -and
                $methodName -eq $targetMethod) {

                $targetFrameIndex = $i
                break
            }
        }

        if ($targetFrameIndex -ge 0) {

            $relatedToSnapshot = $false

            # Examine frames beneath the allocation/map operation
            for ($i = $targetFrameIndex + 1; $i -lt $frames.Count; $i++) {

                $className = $frames[$i].method.type.name
                $methodName = $frames[$i].method.name

                if ($className -like "*DspOperationalReleaseSnapshot*") {
                    $relatedToSnapshot = $true
                    break
                }
            }

            [PSCustomObject]@{
                Target            = $target
                Weight            = [long]$sample.values.weight
                SnapshotRelated   = $relatedToSnapshot
            }
        }
    }

    $totalWeight = ($matchingSamples |
        Measure-Object Weight -Sum).Sum

    $snapshotWeight = ($matchingSamples |
        Where-Object SnapshotRelated |
        Measure-Object Weight -Sum).Sum

    $otherWeight = $totalWeight - $snapshotWeight

    $snapshotPct = if ($totalWeight -gt 0) {
        100.0 * $snapshotWeight / $totalWeight
    } else {
        0
    }

    $otherPct = if ($totalWeight -gt 0) {
        100.0 * $otherWeight / $totalWeight
    } else {
        0
    }

    Write-Host ""
    Write-Host $target
    Write-Host ("  DspOperationalReleaseSnapshot: {0:N0} bytes ({1:N2}%)" -f `
        $snapshotWeight, $snapshotPct)

    Write-Host ("  Other maps:                    {0:N0} bytes ({1:N2}%)" -f `
        $otherWeight, $otherPct)

    Write-Host ("  Total:                         {0:N0} bytes" -f `
        $totalWeight)
}

foreach ($target in $targets) {

    $targetClass, $targetMethod = $target.Split(".")

    $callers = foreach ($sample in $samples) {

        $frames = $sample.values.stackTrace.frames

        for ($i = 0; $i -lt $frames.Count; $i++) {

            if ($frames[$i].method.type.name -like "*$targetClass" -and
                $frames[$i].method.name -eq $targetMethod) {

                # Find first non-JDK frame below the target
                for ($j = $i + 1; $j -lt $frames.Count; $j++) {

                    $className = $frames[$j].method.type.name

                    if ($className -like "online/davisfamily/*") {

                        $shortClass = ($className -split "/")[-1]
                        $methodName = $frames[$j].method.name

                        [PSCustomObject]@{
                            Caller = "$shortClass.$methodName"
                            Weight = [long]$sample.values.weight
                        }

                        break
                    }
                }

                break
            }
        }
    }

    Write-Host ""
    Write-Host "Top callers beneath $target"

    $callers |
        Group-Object Caller |
        ForEach-Object {
            [PSCustomObject]@{
                Caller = $_.Name
                Weight = ($_.Group | Measure-Object Weight -Sum).Sum
            }
        } |
        Sort-Object Weight -Descending |
        Select-Object -First 20 |
        Format-Table `
            Caller,
            @{Label="Weight"; Expression={"{0:N0}" -f $_.Weight}} `
            -AutoSize
}