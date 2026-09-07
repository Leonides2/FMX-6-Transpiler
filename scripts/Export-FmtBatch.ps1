<#
.SYNOPSIS
  Convierte en lote todos los .fmb/.mmb/.pll (formularios/menus/librerias) de una carpeta
  (y sus subcarpetas) a su version de texto .fmt/.mmt/.pld, usando el compilador de
  Oracle Forms 6 (ifcmp60) con script=YES, preservando la estructura de subcarpetas en el destino.

.PARAMETER SourceFolder
  Carpeta raiz donde estan los .fmb/.mmb/.pll (se busca recursivamente).

.PARAMETER OutputFolder
  Carpeta donde se dejan los .fmt/.mmt/.pld resultantes (se crea si no existe, y se
  replica ahi la misma estructura de subcarpetas que en SourceFolder).

.PARAMETER Credentials
  Cadena de conexion "usuario/clave@basededatos" que pide ifcmp60. Si no tienes una a
  mano, prueba primero con UN solo archivo (ver ejemplo abajo) para confirmar si tu
  instalacion realmente la exige o si acepta seguir sin validar contra la base.

.PARAMETER IfcmpPath
  Ruta completa a ifcmp60.exe si no esta en el PATH (ej: "C:\orant\bin\ifcmp60.exe").

.EXAMPLE
  # Probar con UN archivo primero (recomendado antes de correr el lote completo)
  .\Export-FmtBatch.ps1 -SourceFolder "C:\ruta\Formularios" -OutputFolder "C:\ruta\FmtExport" -Credentials "usuario/clave@basedatos" -WhatIf

.EXAMPLE
  .\Export-FmtBatch.ps1 -SourceFolder "C:\ruta\Formularios" -OutputFolder "C:\ruta\FmtExport" -Credentials "usuario/clave@basedatos"

.NOTES
  - ifcmp60 (o el ejecutable equivalente de tu version exacta de Forms) tipicamente deja el
    .fmt junto al archivo fuente, con el mismo nombre base. Este script lo mueve despues al
    OutputFolder respetando la subcarpeta relativa de origen.
  - Si tu Oracle Forms Developer no trae ifcmp60.exe en el PATH ni en su carpeta bin, revisa
    si el menu de Forms Builder (File > Administration > Convert...) hace la misma conversion
    a mano por archivo - ese metodo ya te funciono para los dos .fmt que ya tienes.
  - -WhatIf muestra que haria el script sin ejecutar nada (usa el soporte nativo de PowerShell).
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [string]$SourceFolder,

    [Parameter(Mandatory = $true)]
    [string]$OutputFolder,

    [Parameter(Mandatory = $true)]
    [string]$Credentials,

    [string]$IfcmpPath = "ifcmp60.exe"
)

if (-not (Test-Path $SourceFolder)) {
    throw "No existe la carpeta origen: $SourceFolder"
}

New-Item -ItemType Directory -Force -Path $OutputFolder | Out-Null
$sourceFull = (Resolve-Path $SourceFolder).Path

$extensionMap = @{ ".fmb" = ".fmt"; ".mmb" = ".mmt"; ".pll" = ".pld" }
$files = Get-ChildItem -Path $sourceFull -Recurse -File | Where-Object { $extensionMap.ContainsKey($_.Extension.ToLower()) }

Write-Host "Encontrados $($files.Count) archivo(s) para convertir." -ForegroundColor Cyan

$ok = 0
$fail = 0
$skipped = 0

foreach ($file in $files) {
    $ext = $file.Extension.ToLower()
    $targetExt = $extensionMap[$ext]

    $relativeDir = Split-Path $file.FullName.Substring($sourceFull.Length).TrimStart('\', '/') -Parent
    $destDir = Join-Path $OutputFolder $relativeDir
    $destFile = Join-Path $destDir ($file.BaseName + $targetExt)

    if ((Test-Path $destFile) -and (Get-Item $destFile).LastWriteTime -ge $file.LastWriteTime) {
        Write-Host "Sin cambios, se omite: $($file.Name)" -ForegroundColor DarkGray
        $skipped++
        continue
    }

    if (-not $PSCmdlet.ShouldProcess($file.FullName, "Convertir a $targetExt")) {
        continue
    }

    New-Item -ItemType Directory -Force -Path $destDir | Out-Null

    Write-Host "Convirtiendo $($file.Name)..." -ForegroundColor Yellow
    & $IfcmpPath "module=$($file.FullName)" "script=YES" "batch=YES" "userid=$Credentials" 2>&1 | Out-Null
    $exitCode = $LASTEXITCODE

    $producedFile = Join-Path $file.DirectoryName ($file.BaseName + $targetExt)
    if (Test-Path $producedFile) {
        Move-Item -Force $producedFile $destFile
        Write-Host "  -> OK: $destFile" -ForegroundColor Green
        $ok++
    } else {
        Write-Warning "  -> Fallo (exit=$exitCode), no se genero $targetExt para $($file.Name)"
        $fail++
    }
}

Write-Host ""
Write-Host "Listo. Convertidos: $ok | Sin cambios (omitidos): $skipped | Fallidos: $fail" -ForegroundColor Cyan
