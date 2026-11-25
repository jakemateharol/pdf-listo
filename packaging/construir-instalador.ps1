<#
  Construye PDF Listo para instalar en Windows (fase 10).

  Resultado en la carpeta dist\ :
    - "PDF Listo\"                       aplicacion lista para usar (con Java incluido)
    - PDFListo-<version>-portable.zip    la misma carpeta comprimida (no necesita instalar)
    - PDFListo-<version>.exe             instalador de Windows (solo si hay WiX 3)

  Uso (desde la carpeta del proyecto):
    powershell -ExecutionPolicy Bypass -File packaging\construir-instalador.ps1
    ... -SinInstalador     solo la version portable (no busca ni descarga WiX)
    ... -SinDescargas      no descarga WiX aunque falte (si no esta, se crea solo la portable)
    ... -Temporal <carpeta> carpeta para los archivos temporales (por defecto <carpeta del proyecto>\..\temp,
                           p. ej. D:\proyectopdf\temp): asi Maven, Java, jpackage y WiX no llenan el disco C:.
                           Se crea una subcarpeta por construccion y se borra al terminar (tambien si falla).

  Que hace:
    1. Compila con Maven (sin pruebas; correrlas antes con mvnw test).
    2. Junta el .jar de la app y sus bibliotecas en target\instalador\entrada.
    3. Carpeta "native": DLL de Tesseract y Leptonica (OCR) + msvcp140/vcruntime140/vcruntime140_1
       (Visual C++). La app la usa con -Djna.library.path=$APPDIR\native, asi el OCR funciona
       aunque el equipo no tenga instalado "Visual C++ Redistributable".
    4. jpackage crea la aplicacion con su propio Java (el usuario no necesita instalar Java).
    5. Comprueba la aplicacion creada (PDF Listo.exe --comprobar) y la comprime.
    6. Si hay WiX 3 (en tools\wix o en el PATH) crea el instalador .exe. Si falta, lo descarga
       una sola vez a tools\wix (unos 40 MB, desde GitHub; la carpeta tools esta en .gitignore).
#>
param(
    [switch]$SinInstalador,
    [switch]$SinDescargas,
    [string]$Temporal = ''
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

# ------------------------------------------------------------------ carpeta temporal (fuera de C:)
# Java (y por eso Maven y jpackage) y WiX toman la carpeta temporal de las variables TEMP/TMP.
# Se apuntan a una subcarpeta propia; al terminar se borra y se restauran las variables.
if (-not $Temporal) { $Temporal = Join-Path (Split-Path -Parent $raiz) 'temp' }
$temporalRun = Join-Path $Temporal ('construccion-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force $temporalRun | Out-Null
$temporalAnterior = @{ TEMP = $env:TEMP; TMP = $env:TMP }
$env:TEMP = $temporalRun
$env:TMP = $temporalRun

function Limpiar {
    $env:TEMP = $temporalAnterior.TEMP
    $env:TMP = $temporalAnterior.TMP
    if (Test-Path $temporalRun) { Remove-Item -Recurse -Force $temporalRun -ErrorAction SilentlyContinue }
}
function Terminar($codigo) { Limpiar; exit $codigo }
function Paso($texto) { Write-Host "`n==> $texto" -ForegroundColor Cyan }
function Falla($texto) { Write-Host "ERROR: $texto" -ForegroundColor Red; Terminar 1 }
trap { Write-Host "ERROR: $_" -ForegroundColor Red; Terminar 1 }

$unidad = [System.IO.Path]::GetPathRoot((Resolve-Path $temporalRun).Path)
$libre = (New-Object System.IO.DriveInfo $unidad).AvailableFreeSpace
Write-Host ("Carpeta temporal: {0} (libre en {1} {2:N1} GB)" -f $temporalRun, $unidad, ($libre / 1GB))
if ($libre -lt 1.5GB) {
    Write-Host "AVISO: quedan menos de 1,5 GB libres en $unidad; la construccion necesita alrededor de 1 GB." -ForegroundColor Yellow
}

# ------------------------------------------------------------------ datos del proyecto
[xml]$pom = Get-Content (Join-Path $raiz 'pom.xml')
$version = $pom.project.version
$artefacto = $pom.project.artifactId
$nombre = 'PDF Listo'
# Identificador fijo: permite que una version nueva reemplace a la anterior al instalar.
$uuidActualizacion = '6f3b8c1e-4d2a-4b7e-9a51-2c8d0f7e1a93'

# ------------------------------------------------------------------ Java (JDK con jpackage)
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\jpackage.exe'))) {
    $java = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($java) { $jdk = Split-Path -Parent (Split-Path -Parent $java) }
}
$jpackage = Join-Path $jdk 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) { Falla "No se encontro jpackage. Instala un JDK 17 o superior y define JAVA_HOME." }
$env:JAVA_HOME = $jdk
Write-Host "JDK: $jdk"
Write-Host "Version de PDF Listo: $version"

# ------------------------------------------------------------------ 1. compilar
Paso "Compilando (sin pruebas)"
$mvn = Join-Path $raiz 'mvnw.cmd'
& $mvn -o -q clean package -DskipTests "-Dspring-boot.repackage.skip=true"
if ($LASTEXITCODE -ne 0) {
    Write-Host "Sin conexion fallo; se intenta descargando dependencias..."
    & $mvn -q clean package -DskipTests "-Dspring-boot.repackage.skip=true"
    if ($LASTEXITCODE -ne 0) { Falla "La compilacion fallo." }
}

# ------------------------------------------------------------------ 2. jar + bibliotecas
Paso "Juntando la aplicacion y sus bibliotecas"
$trabajo = Join-Path $raiz 'target\instalador'
$entrada = Join-Path $trabajo 'entrada'
$native = Join-Path $entrada 'native'
New-Item -ItemType Directory -Force $native | Out-Null
Copy-Item (Join-Path $raiz "target\$artefacto-$version.jar") $entrada
& $mvn -o -q dependency:copy-dependencies -DincludeScope=runtime -DexcludeArtifactIds=lombok "-DoutputDirectory=$entrada"
if ($LASTEXITCODE -ne 0) { Falla "No se pudieron copiar las bibliotecas." }

# ------------------------------------------------------------------ 3. carpeta native
Paso "Preparando la carpeta native (OCR + Visual C++)"
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($patron in 'tess4j-*.jar', 'lept4j-*.jar') {
    $jar = Get-ChildItem $entrada -Filter $patron | Select-Object -First 1
    if (-not $jar) { Falla "No se encontro $patron entre las bibliotecas." }
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        foreach ($e in $zip.Entries | Where-Object { $_.FullName -like 'win32-x86-64/*.dll' }) {
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $native $e.Name), $true)
        }
    } finally { $zip.Dispose() }
}
$sistema = Join-Path $env:SystemRoot 'System32'
foreach ($dll in 'msvcp140.dll', 'vcruntime140.dll', 'vcruntime140_1.dll') {
    $origen = @((Join-Path $jdk "bin\$dll"), (Join-Path $sistema $dll)) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $origen) { Falla "No se encontro $dll (ni en el JDK ni en System32)." }
    Copy-Item $origen $native -Force
}
Get-ChildItem $native | ForEach-Object { Write-Host ("   {0,-28} {1,8:N0} KB" -f $_.Name, ($_.Length / 1KB)) }

# ------------------------------------------------------------------ 4. jpackage (app-image)
Paso "Creando la aplicacion con Java incluido (jpackage)"
$dist = Join-Path $raiz 'dist'
$imagen = Join-Path $dist $nombre
if (Test-Path $imagen) { Remove-Item -Recurse -Force $imagen }
New-Item -ItemType Directory -Force $dist | Out-Null
$modulos = 'java.se,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.charsets,jdk.zipfs,jdk.management,jdk.accessibility'
$argsImagen = @(
    '--type', 'app-image',
    '--name', $nombre,
    '--app-version', $version,
    '--vendor', 'PDF Listo',
    '--description', 'Editor de PDF que funciona sin internet',
    '--icon', (Join-Path $PSScriptRoot 'pdflisto.ico'),
    '--input', $entrada,
    '--main-jar', "$artefacto-$version.jar",
    '--main-class', 'pe.pdflisto.HALauncher',
    '--dest', $dist,
    '--add-modules', $modulos,
    '--jlink-options', '--strip-debug --no-man-pages --no-header-files --include-locales=es,en',
    # Memoria: hasta 60 % de la RAM del equipo (PDF muy grandes); $APPDIR lo resuelve el lanzador.
    '--java-options', '-XX:MaxRAMPercentage=60',
    '--java-options', '-Djna.library.path=$APPDIR\native',
    '--java-options', '-Dfile.encoding=UTF-8',
    '--temp', (Join-Path $temporalRun 'jpackage-aplicacion')
)
& $jpackage @argsImagen
if ($LASTEXITCODE -ne 0) { Falla "jpackage no pudo crear la aplicacion." }

# ------------------------------------------------------------------ 5. comprobar y comprimir
Paso "Comprobando la aplicacion creada"
$exe = Join-Path $imagen "$nombre.exe"
$informe = Join-Path $trabajo 'comprobacion.txt'
if (Test-Path $informe) { Remove-Item $informe }
$p = Start-Process -FilePath $exe -ArgumentList '--comprobar', "`"$informe`"" -Wait -PassThru
if (-not (Test-Path $informe)) { Falla "La aplicacion creada no arranco (no escribio la comprobacion)." }
Get-Content $informe -Encoding UTF8 | ForEach-Object { Write-Host "   $_" }
if ($p.ExitCode -ne 0) { Falla "La comprobacion encontro componentes que no funcionan (ver arriba)." }

$portable = Join-Path $dist "PDFListo-$version-portable.zip"
if (Test-Path $portable) { Remove-Item $portable }
[System.IO.Compression.ZipFile]::CreateFromDirectory($imagen, $portable, 'Optimal', $true)
Write-Host ("Portable: {0} ({1:N0} MB)" -f $portable, ((Get-Item $portable).Length / 1MB))

# ------------------------------------------------------------------ 6. instalador .exe (WiX 3)
if ($SinInstalador) { Write-Host "`nListo (sin instalador, como se pidio)."; Terminar 0 }

Paso "Buscando WiX 3 para crear el instalador"
$wix = Join-Path $raiz 'tools\wix'
if (-not (Test-Path (Join-Path $wix 'candle.exe')) -and -not (Get-Command candle.exe -ErrorAction SilentlyContinue)) {
    if ($SinDescargas) {
        Write-Host "No hay WiX 3 y se pidio no descargar: queda solo la version portable." -ForegroundColor Yellow
        Terminar 0
    }
    Write-Host "Descargando WiX 3.14 (portable, solo para construir; la app NO lo necesita)..."
    New-Item -ItemType Directory -Force $wix | Out-Null
    $zipWix = Join-Path $raiz 'tools\wix314-binaries.zip'
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' -OutFile $zipWix
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zipWix, $wix)
    } catch {
        Write-Host "No se pudo descargar WiX ($($_.Exception.Message)). Queda solo la version portable." -ForegroundColor Yellow
        Terminar 0
    }
}
if (Test-Path (Join-Path $wix 'candle.exe')) { $env:PATH = "$wix;$env:PATH" }

Paso "Creando el instalador .exe"
$argsInstalador = @(
    '--type', 'exe',
    '--app-image', $imagen,
    '--name', $nombre,
    '--app-version', $version,
    '--vendor', 'PDF Listo',
    '--description', 'Editor de PDF que funciona sin internet',
    '--dest', $dist,
    '--win-per-user-install',
    '--win-dir-chooser',          # el usuario elige la carpeta (por ejemplo en D:)
    '--win-menu', '--win-menu-group', $nombre,
    '--win-shortcut', '--win-shortcut-prompt',
    '--win-upgrade-uuid', $uuidActualizacion,
    '--file-associations', (Join-Path $PSScriptRoot 'asociacion-pdf.properties'),
    '--license-file', (Join-Path $PSScriptRoot 'LICENCIA-INSTALADOR.txt'),
    '--temp', (Join-Path $temporalRun 'jpackage-instalador')
)
& $jpackage @argsInstalador
if ($LASTEXITCODE -ne 0) { Falla "jpackage no pudo crear el instalador (la version portable si esta lista)." }
$generado = Join-Path $dist "$nombre-$version.exe"
$final = Join-Path $dist "PDFListo-$version.exe"
if (Test-Path $final) { Remove-Item $final }
Move-Item $generado $final
Write-Host ("Instalador: {0} ({1:N0} MB)" -f $final, ((Get-Item $final).Length / 1MB)) -ForegroundColor Green
Terminar 0
