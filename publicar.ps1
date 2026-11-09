# Publica uma versão nova do Freezer; os telemóveis mostram "Nova versão" ao abrir a app.
# 1) Sobe versionCode e versionName em app\build.gradle.kts   2) Corre:  .\publicar.ps1
# Compila aqui (mesma chave de assinatura que a app instalada) e envia o APK para o ramo "atualizacoes" do GitHub.
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$versao = (Select-String -Path app\build.gradle.kts -Pattern 'versionName = "(.+)"').Matches[0].Groups[1].Value
$notas = Read-Host "Novidades da versão $versao (uma linha)"
Write-Host "A compilar..."
.\gradlew.bat assembleDebug --console=plain | Out-Null
$tmp = Join-Path $env:TEMP "freezer-atualizacoes"
if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
New-Item -ItemType Directory $tmp | Out-Null
Copy-Item app\build\outputs\apk\debug\app-debug.apk "$tmp\freezer.apk"
$tam = (Get-Item "$tmp\freezer.apk").Length
@{ versao = $versao; notas = $notas; apk = "freezer.apk"; tamanho = $tam } | ConvertTo-Json | Set-Content -Encoding utf8 "$tmp\versao.json"
Push-Location $tmp
git init -q -b atualizacoes
git add -A
git commit -q -m "Freezer $versao"
git push -q -f https://github.com/JoaoAntunesss/Freezer.git atualizacoes   # ramo só com a última versão (não acumula APKs)
Pop-Location
Write-Host "Publicada a versão $versao."
