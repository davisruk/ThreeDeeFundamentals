. .\env-jfr-work.ps1

$allocationFile = "C:\misc\cpas-test\scheduler-testing\dsp-$step-object-allocation-samples.json"

$json = Get-Content $allocationFile -Raw | ConvertFrom-Json
$samples = $json.recording.events

$targetMethods = @("evaluate", "update")

Write-Host ""
Write-Host "Object allocation analysis"
Write-Host "=========================="
Write-Host ("Total allocation samples: {0:N0}" -f $samples.Count)

foreach ($targetMethod in $targetMethods) {

    #
    # Find allocation samples whose stack contains any method
    # named evaluate() or update().
    #
    $targetSamples = @(
        $samples | Where-Object {

            $found = $false

            foreach ($frame in $_.values.stackTrace.frames) {
                if ($frame.method.name -eq $targetMethod) {
                    $found = $true
                    break
                }
            }

            $found
        }
    )

    #
    # ObjectAllocationSample is a weighted JFR event.
    # Sum the weights rather than simply counting events.
    #
    $totalWeight = (
        $targetSamples |
        ForEach-Object { [long]$_.values.weight } |
        Measure-Object -Sum
    ).Sum

    Write-Host ""
    Write-Host "$targetMethod()"
    Write-Host ("-" * 70)
    Write-Host ("Allocation samples: {0:N0}" -f $targetSamples.Count)
    Write-Host ("Sampled weight:      {0:N0} bytes" -f $totalWeight)

    #
    # REPORT 1
    #
    # Attribute each allocation sample to the application method
    # nearest to the actual allocation site.
    #
    # JFR stack frames are ordered from the currently executing
    # frame towards its callers, so the first online/davisfamily
    # frame is the nearest application-level frame.
    #
    # Each allocation sample is attributed exactly once, so these
    # values do not overlap.
    #
    $allocationCallers = foreach ($sample in $targetSamples) {

        $weight = [long]$sample.values.weight

        foreach ($frame in $sample.values.stackTrace.frames) {

            $className = $frame.method.type.name

            if ($className -like "online/davisfamily/*") {

                $shortClass = ($className -split "/")[-1]
                $methodName = $frame.method.name

                [PSCustomObject]@{
                    Method = "$shortClass.$methodName"
                    Weight = $weight
                }

                # Attribute this allocation sample to the nearest
                # application-level frame only.
                break
            }
        }
    }

    Write-Host ""
    Write-Host "Largest application-level allocation callers"
    Write-Host "--------------------------------------------"

    $allocationCallers |
        Group-Object Method |
        ForEach-Object {

            $weight = (
                $_.Group |
                Measure-Object Weight -Sum
            ).Sum

            [PSCustomObject]@{
                Method     = $_.Name
                Weight     = $weight
                Percentage = if ($totalWeight -gt 0) {
                    100.0 * $weight / $totalWeight
                }
                else {
                    0
                }
            }
        } |
        Sort-Object Weight -Descending |
        Select-Object -First 30 |
        ForEach-Object {
            "{0,15:N0} bytes  {1,6:N2}%  {2}" -f `
                $_.Weight,
                $_.Percentage,
                $_.Method
        }

    #
    # REPORT 2
    #
    # Find every application-level method present in each allocation
    # stack. This shows the call paths associated with allocation
    # pressure.
    #
    # A single allocation sample may contribute its weight to several
    # methods because several application methods can occur in the
    # same stack. Therefore these percentages overlap and must not
    # be added together.
    #
    $applicationMethods = foreach ($sample in $targetSamples) {

        $weight = [long]$sample.values.weight
        $methods = @()

        foreach ($frame in $sample.values.stackTrace.frames) {

            $className = $frame.method.type.name

            if ($className -like "online/davisfamily/*") {

                $shortClass = ($className -split "/")[-1]
                $methodName = $frame.method.name

                $methods += "$shortClass.$methodName"
            }
        }

        #
        # Count each application method at most once for this
        # allocation sample.
        #
        foreach ($method in ($methods | Select-Object -Unique)) {
            [PSCustomObject]@{
                Method = $method
                Weight = $weight
            }
        }
    }

    Write-Host ""
    Write-Host "Application methods present in allocation stacks"
    Write-Host "------------------------------------------------"

    $applicationMethods |
        Group-Object Method |
        ForEach-Object {

            $weight = (
                $_.Group |
                Measure-Object Weight -Sum
            ).Sum

            [PSCustomObject]@{
                Method     = $_.Name
                Weight     = $weight
                Percentage = if ($totalWeight -gt 0) {
                    100.0 * $weight / $totalWeight
                }
                else {
                    0
                }
            }
        } |
        Sort-Object Weight -Descending |
        Select-Object -First 30 |
        ForEach-Object {
            "{0,15:N0} bytes  {1,6:N2}%  {2}" -f `
                $_.Weight,
                $_.Percentage,
                $_.Method
        }
}