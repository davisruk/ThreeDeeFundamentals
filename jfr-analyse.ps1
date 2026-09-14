. .\env-jfr-work.ps1

& $jfr view hot-methods $recording |
    Out-File -Encoding utf8 "C:\misc\cpas-test\scheduler-testing\dsp-$step-hot-methods.txt"

& $jfr view allocation-by-class $recording |
    Out-File -Encoding utf8 "C:\misc\cpas-test\scheduler-testing\dsp-$step-allocations.txt"

& $jfr view allocation-by-site $recording |
    Out-File -Encoding utf8 "C:\misc\cpas-test\scheduler-testing\dsp-$step-allocation-by-site.txt"
	 
& $jfr view gc $recording |
    Out-File -Encoding utf8 "C:\misc\cpas-test\scheduler-testing\dsp-$step-gc.txt"
	
$sampleFile = "C:\misc\cpas-test\scheduler-testing\dsp-$step-execution-sample.txt"
& $jfr print --json --events jdk.ExecutionSample --stack-depth 16 $recording | Out-File -Encoding utf8 $sampleFile

$objectAllocationFile = "C:\misc\cpas-test\scheduler-testing\dsp-$step-object-allocation-samples.json"
& $jfr print --json `
    --events jdk.ObjectAllocationSample `
    --stack-depth 16 $recording |
    Out-File -Encoding utf8 $objectAllocationFile

$targets = @(
    "P2pBagCorrelationAssignmentSnapshot.lineFor",
    "DspOperationalReleaseSnapshot.findByPhysicalToteId"
)

$json = Get-Content $sampleFile -Raw | ConvertFrom-Json

$samples = $json.recording.events
$totalSamples = $samples.Count

foreach ($target in $targets) {

    $count = 0

    foreach ($sample in $samples) {

        $found = $false

        foreach ($frame in $sample.values.stackTrace.frames | Select-Object -First 16) {

            $method = $frame.method

            $methodName = "$($method.type.name).$($method.name)"

            if ($methodName -like "*$target") {
                $found = $true
                break
            }
        }

        if ($found) {
            $count++
        }
    }

    $percentage = if ($totalSamples -gt 0) {
        ($count / $totalSamples) * 100
    }
    else {
        0
    }

    "{0}: {1:N0} / {2:N0} = {3:N2}%" -f `
        $target, $count, $totalSamples, $percentage
}
