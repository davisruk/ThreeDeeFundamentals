$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$jfr = "$env:JAVA_HOME\bin\jfr.exe"
$step="performance"
$jfr_sample_duration="90s"
$recording = "C:\misc\cpas-test\scheduler-testing\dsp-$step-hotspots.jfr"