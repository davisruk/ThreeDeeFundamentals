. .\env-jfr-work.ps1

$javaPid = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" |
    Where-Object CommandLine -match 'DspFullDayAnalysisMain' |
    Select-Object -ExpandProperty ProcessId
	
& "$env:JAVA_HOME\bin\jcmd.exe" $javaPid JFR.start `
    name=$step `
    settings=profile `
    duration=45s `
    disk=true `
    filename=$recording
	
