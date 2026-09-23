. .\env-jfr-work.ps1


$sampleFile = "C:\misc\cpas-test\scheduler-testing\dsp-$step-execution-sample.txt"

$json = Get-Content $sampleFile -Raw | ConvertFrom-Json

$samples = $json.recording.events
$totalSamples = $samples.Count

# Find samples containing DspFullDayMetricsCollector.snapshot()

$snapshotSamples = @(
    $samples | Where-Object {
        $found = $false

        foreach ($frame in $_.values.stackTrace.frames) {
            if (
                $frame.method.type.name -like "*DspFullDayMetricsCollector" -and
                $frame.method.name -eq "snapshot"
            ) {
                $found = $true
                break
            }
        }

        $found
    }
)

Write-Host ""
Write-Host "DspFullDayMetricsCollector.snapshot() analysis"
Write-Host "------------------------------------------------"
Write-Host ("Total execution samples:       {0:N0}" -f $samples.Count)
Write-Host ("Samples containing snapshot(): {0:N0}" -f $snapshotSamples.Count)

$topFrames = foreach ($sample in $snapshotSamples) {

    $frame = $sample.values.stackTrace.frames[0]

    $className = ($frame.method.type.name -split "/")[-1]
    $methodName = $frame.method.name

    "$className.$methodName"
}

Write-Host ""
Write-Host "Top frames while snapshot() was active"
Write-Host "---------------------------------------"

$topFrames |
    Group-Object |
    Sort-Object Count -Descending |
    Select-Object -First 20 |
    ForEach-Object {
        "{0,5:N0}  {1}" -f $_.Count, $_.Name
    }
$calleeFrames = foreach ($sample in $snapshotSamples) {

    $frames = $sample.values.stackTrace.frames
    $snapshotIndex = -1

    for ($i = 0; $i -lt $frames.Count; $i++) {
        if (
            $frames[$i].method.type.name -like "*DspFullDayMetricsCollector" -and
            $frames[$i].method.name -eq "snapshot"
        ) {
            $snapshotIndex = $i
            break
        }
    }

    # Frames before snapshot() are methods executing beneath it
    # in the call stack.
    for ($i = 0; $i -lt $snapshotIndex; $i++) {

        $frame = $frames[$i]

        $className = ($frame.method.type.name -split "/")[-1]
        $methodName = $frame.method.name

        "$className.$methodName"
    }
}

Write-Host ""
Write-Host "Methods observed beneath snapshot()"
Write-Host "-----------------------------------"

$calleeFrames |
    Group-Object |
    Sort-Object Count -Descending |
    Select-Object -First 30 |
    ForEach-Object {
        "{0,5:N0}  {1}" -f $_.Count, $_.Name
    }
	
$methodsPerSample = foreach ($sample in $snapshotSamples) {

    $frames = $sample.values.stackTrace.frames
    $snapshotIndex = -1

    for ($i = 0; $i -lt $frames.Count; $i++) {
        if (
            $frames[$i].method.type.name -like "*DspFullDayMetricsCollector" -and
            $frames[$i].method.name -eq "snapshot"
        ) {
            $snapshotIndex = $i
            break
        }
    }

    $methods = @()

    for ($i = 0; $i -lt $snapshotIndex; $i++) {

        $frame = $frames[$i]

        $className = ($frame.method.type.name -split "/")[-1]
        $methodName = $frame.method.name

        $methods += "$className.$methodName"
    }

    # Each method counts at most once for this sample
    $methods | Select-Object -Unique
}

Write-Host ""
Write-Host "Methods present in snapshot() samples"
Write-Host "-------------------------------------"

$methodsPerSample |
    Group-Object |
    Sort-Object Count -Descending |
    Select-Object -First 30 |
    ForEach-Object {
        $pct = 100.0 * $_.Count / $snapshotSamples.Count

        "{0,5:N0} / {1:N0}  {2,6:N2}%  {3}" -f `
            $_.Count,
            $snapshotSamples.Count,
            $pct,
            $_.Name
    }

$runnerSamples = @(
    $snapshotSamples | Where-Object {
        $frames = $_.values.stackTrace.frames
        $snapshotIndex = -1

        for ($i = 0; $i -lt $frames.Count; $i++) {
            if (
                $frames[$i].method.type.name -like "*DspFullDayMetricsCollector" -and
                $frames[$i].method.name -eq "snapshot"
            ) {
                $snapshotIndex = $i
                break
            }
        }

        $foundRunner = $false

        if ($snapshotIndex -ge 0) {
            for ($i = $snapshotIndex + 1; $i -lt $frames.Count; $i++) {
                if ($frames[$i].method.type.name -like "*DspFullDayAnalysisRunner") {
                    $foundRunner = $true
                    break
                }
            }
        }

        $foundRunner
    }
)

Write-Host ""
Write-Host "snapshot() caller analysis"
Write-Host "--------------------------"
Write-Host ("snapshot() samples:                    {0:N0}" -f $snapshotSamples.Count)
Write-Host ("snapshot() samples containing runner:  {0:N0}" -f $runnerSamples.Count)

$pctOfSnapshot = 100.0 * $runnerSamples.Count / $snapshotSamples.Count
$pctOfAll = 100.0 * $runnerSamples.Count / $samples.Count

Write-Host ("Runner share of snapshot samples:      {0:N2}%" -f $pctOfSnapshot)
Write-Host ("Runner snapshot samples / all samples: {0:N2}%" -f $pctOfAll)

$callers = foreach ($sample in $snapshotSamples) {

    $frames = $sample.values.stackTrace.frames

    for ($i = 0; $i -lt $frames.Count; $i++) {

        if (
            $frames[$i].method.type.name -like "*DspFullDayMetricsCollector" -and
            $frames[$i].method.name -eq "snapshot"
        ) {
            # Immediate caller is the next frame
            if (($i + 1) -lt $frames.Count) {

                $caller = $frames[$i + 1]
                $className = ($caller.method.type.name -split "/")[-1]
                $methodName = $caller.method.name

                "$className.$methodName"
            }

            break
        }
    }
}

Write-Host ""
Write-Host "Immediate callers of snapshot()"
Write-Host "-------------------------------"

$callers |
    Group-Object |
    Sort-Object Count -Descending |
    ForEach-Object {
        "{0,5:N0} / {1:N0}  {2,6:N2}%  {3}" -f `
            $_.Count,
            $snapshotSamples.Count,
            (100.0 * $_.Count / $snapshotSamples.Count),
            $_.Name
    }