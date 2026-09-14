$jfr = "$env:JAVA_HOME\bin\jfr.exe"
$env:JAVA_HOME = 'C:\Java\jdk\21.0.7'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$step="step21"
$recording = "C:\misc\cpas-test\scheduler-testing\dsp-$step-hotspots.jfr"