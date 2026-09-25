. .\env-jfr-work.ps1

$allocationFile = "C:\misc\cpas-test\scheduler-testing\dsp-$step-object-allocation-samples.json"
$json = Get-Content $allocationFile -Raw | ConvertFrom-Json
$samples = $json.recording.events

Write-Host ""
Write-Host "Classes containing evaluate()"
Write-Host "-----------------------------"

$samples |
    ForEach-Object { $_.values.stackTrace.frames } |
    Where-Object { $_.method.name -eq "evaluate" } |
    ForEach-Object { $_.method.type.name } |
    Sort-Object -Unique

Write-Host ""
Write-Host "Classes containing update()"
Write-Host "---------------------------"

$samples |
    ForEach-Object { $_.values.stackTrace.frames } |
    Where-Object { $_.method.name -eq "update" } |
    ForEach-Object { $_.method.type.name } |
    Sort-Object -Unique
	