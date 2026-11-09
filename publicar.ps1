# Publica uma versão nova do Freezer para os telemóveis atualizarem dentro da app.
# Uso:  .\publicar.ps1            (usa a versionName do app\build.gradle.kts)
# Precisa de um token do GitHub com acesso ao repositório (Settings > Developer settings > Fine-grained token,
# permissão "Contents: Read and write") guardado na variável de ambiente GITHUB_TOKEN.
$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$versao = (Select-String -Path app\build.gradle.kts -Pattern 'versionName = "(.+)"').Matches[0].Groups[1].Value
Write-Host "A compilar a versão $versao..."
.\gradlew.bat assembleDebug --console=plain | Out-Null
$apk = "app\build\outputs\apk\debug\app-debug.apk"
if (-not $env:GITHUB_TOKEN) { throw "Define primeiro GITHUB_TOKEN" }
$h = @{ Authorization = "Bearer $env:GITHUB_TOKEN"; Accept = "application/vnd.github+json" }
$notas = Read-Host "Novidades desta versão (uma linha)"
$rel = Invoke-RestMethod -Method Post -Uri "https://api.github.com/repos/JoaoAntunesss/Freezer/releases" -Headers $h `
  -Body (@{ tag_name = "v$versao"; name = "Freezer $versao"; body = $notas } | ConvertTo-Json) -ContentType "application/json"
Invoke-RestMethod -Method Post -Uri "https://uploads.github.com/repos/JoaoAntunesss/Freezer/releases/$($rel.id)/assets?name=freezer-$versao.apk" `
  -Headers $h -InFile $apk -ContentType "application/vnd.android.package-archive" | Out-Null
Write-Host "Publicada: $($rel.html_url). Os telemóveis vão mostrar o aviso na próxima vez que abrirem a app."
