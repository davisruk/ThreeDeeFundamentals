. .\env-jfr-work.ps1

$sampleFile = "C:\misc\cpas-test\scheduler-testing\dsp-$step-execution-sample.txt"

$json = Get-Content $sampleFile -Raw | ConvertFrom-Json
$samples = $json.recording.events

$targets = @(
    @{
        Class  = "DspOperationalReleaseSnapshotFactory"
        Method = "create"
    },
    @{
        Class  = "OperationalCandidateRouteAdmissionFactory"
        Method = "create"
    },
    @{
        Class  = "ThirdPartyVisitFactory"
        Method = "planFor"
    },
    @{
        Class  = "P2pBagCorrelationAssignmentSnapshot"
        Method = "compatibleWith"
    }
)

$totalSamples = $samples.Count

Write-Host ""
Write-Host "Execution sample analysis"
Write-Host "========================="
Write-Host ("Total execution samples: {0:N0}" -f $totalSamples)
Write-Host ""
Write-Host "Class-qualified execution-sample shares"
Write-Host "---------------------------------------"

foreach ($target in $targets) {

    $matchingSamples = @(
        $samples | Where-Object {

            $found = $false

            foreach ($frame in $_.values.stackTrace.frames) {

                $className = $frame.method.type.name
                $methodName = $frame.method.name
                $shortClass = ($className -split "/")[-1]

                if (
                    $shortClass -eq $target.Class -and
                    $methodName -eq $target.Method
                ) {
                    $found = $true
                    break
                }
            }

            $found
        }
    )

    $count = $matchingSamples.Count

    $percentage = if ($totalSamples -gt 0) {
        100.0 * $count / $totalSamples
    }
    else {
        0
    }

    "{0,-65} {1,6:N0} / {2,6:N0} = {3,6:N2}%" -f `
        "$($target.Class).$($target.Method)",
        $count,
        $totalSamples,
        $percentage
}