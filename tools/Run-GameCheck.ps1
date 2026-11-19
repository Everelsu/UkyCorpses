param([switch]$Offline, [ValidateSet(8,17)][int]$JavaVersion = 8, [Alias('HbmJar')][string]$HbmDevJar, [switch]$PenaltyOnly)
$ErrorActionPreference = 'Stop'
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$initPath = Join-Path $workspace 'build/portqa.init.gradle'
$qaDirectory = "run/portqa-java$JavaVersion"
$qaTask = if ($JavaVersion -eq 8) { 'runClient' } else { 'runClient17' }
$qaPath = Join-Path $workspace $qaDirectory
New-Item -ItemType Directory -Path (Join-Path $qaPath 'mods') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $qaPath 'config') -Force | Out-Null
if ($HbmDevJar) {
    $hbmSource = (Resolve-Path -LiteralPath $HbmDevJar).Path
    Copy-Item -LiteralPath $hbmSource -Destination (Join-Path $qaPath 'mods/HBM-compat-test.jar') -Force
}

New-Item -ItemType Directory -Path (Join-Path $workspace 'build') -Force | Out-Null
@'
beforeSettings { settings -> settings.pluginManagement.repositories.maven { url = uri("https://repo.maven.apache.org/maven2/") } }
allprojects { buildscript { repositories { maven { url = uri("https://repo.maven.apache.org/maven2/") } } }; repositories { maven { url = uri("https://repo.maven.apache.org/maven2/") } } }
gradle.projectsEvaluated {
    def root = gradle.rootProject
    root.tasks.named("__QA_TASK__").configure {
        dependsOn(root.tasks.named("gameCheckJar"))
        jvmArgs("-Duky.portqa=true")
        if (System.getProperty("uky.portqa.penaltyOnly") == "true") jvmArgs("-Duky.portqa.penaltyOnly=true")
        doFirst {
            root.copy {
                from(root.layout.buildDirectory.file("gamecheck/uky-port-gamecheck.jar"))
                into(root.layout.projectDirectory.dir("__QA_DIRECTORY__/mods"))
            }
        }
    }
}
'@.Replace('__QA_TASK__', $qaTask).Replace('__QA_DIRECTORY__', $qaDirectory) | Set-Content -LiteralPath $initPath -Encoding UTF8
$env:HOTSWAP = 'false'
$qaArgs = @($qaTask, '--no-configuration-cache', "-PrunClientWorkingDirectory=$qaDirectory", '--username=PortQA', '-I', $initPath, '--console=plain')
if ($JavaVersion -eq 17) { $qaArgs += @('-x', 'setupHotswapAgent17') }
if ($Offline) { $qaArgs += '--offline' }
if ($PenaltyOnly) { $qaArgs += '-Duky.portqa.penaltyOnly=true' }
Push-Location $workspace
try {
    $started = [DateTime]::UtcNow
    & (Join-Path $workspace 'gradlew.bat') @qaArgs
    if ($LASTEXITCODE -ne 0) { throw "Game check launcher exited with code $LASTEXITCODE" }
    $resultPath = Join-Path $qaPath 'portqa-result.json'
    if ((Get-Item -LiteralPath $resultPath).LastWriteTimeUtc -lt $started) { throw 'The game did not produce a fresh check result' }
    $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if (-not $result.success) { throw "Game checks failed: $($result.failure)" }
    Write-Host "Game checks passed. Evidence: $resultPath"
} finally { Pop-Location }
