$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$startTime = Get-Date

& java -cp "app\build\install\app\lib\*" `
    online.davisfamily.warehouse.sim.dsp.analysis.DspFullDayAnalysisMain `
    --config="C:\misc\cpas-test\scheduler-testing\config\scheduler_conf.json"

$elapsed = (Get-Date) - $startTime

Write-Host ""
Write-Host ("Execution time: {0:hh\:mm\:ss\.fff}" -f $elapsed)

