<#
Pruebas contra la nube: API Gateway -> Cloud Run -> Cloud SQL, y el frontend en Firebase Hosting.

Uso (PowerShell, desde la carpeta del backend):
  powershell -ExecutionPolicy Bypass -File .\infra\pruebas\pruebas-nube.ps1

Pide el correo y la clave de una cuenta de Firebase (correo y contraseña) que sea
ADMINISTRADOR en la cafetería A y CAJERO en la B. La clave no se muestra ni se guarda.

Deja datos reales en la base:
  - Ventas de prueba que el mismo script deshace enseguida (quedan ANULADAS).
  - UN ingreso libre de $1 con la descripción "Prueba automática" en la cafetería A
    (se omite con -SinIngresoLibre).

Con el WAF montado, además prueba que no se pueda saltar (directo a *.gateway.dev) y que Cloud Armor
bloquee ataques comunes. Con -ProbarLimite también prueba el límite por IP (deja tu IP bloqueada ~1 min).

Al final guarda los resultados en un CSV junto a este script.
#>
param(
    # Con el WAF montado (docs/despliegue/waf.md) la API entra por el balanceador; sin WAF, por el gateway
    [string]$Api = "https://136-110-153-51.sslip.io/api",
    [string]$Gateway = "https://jjskaffee-gateway-dcdyujod.ue.gateway.dev/api",
    [string]$Web = "https://jjskafee.web.app",
    [string]$CafeteriaA = "11111111-1111-1111-1111-111111111111",
    [string]$CafeteriaB = "22222222-2222-2222-2222-222222222222",
    [string]$CafeteriaC = "33333333-3333-3333-3333-333333333333",
    [string]$ApiKeyFirebase = "",
    [string]$UrlCloudRun = "",
    [switch]$SinIngresoLibre,
    # Manda 120 peticiones seguidas para ver el 429. Tu IP queda bloqueada como 1 minuto: va de último
    [switch]$ProbarLimite
)
$conWaf = $Api -ne $Gateway

$ErrorActionPreference = "Stop"
$utf8 = New-Object System.Text.UTF8Encoding $false
$tmp = Join-Path ([IO.Path]::GetTempPath()) ("jjs-pruebas-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tmp | Out-Null
$resultados = New-Object System.Collections.Generic.List[object]

# ---------------------------------------------------------------- utilidades

function Llamar {
    param(
        [string]$Metodo = "GET",
        [string]$Url,
        [string]$Token,
        [string]$Cafeteria,
        $Cuerpo = $null,
        [string]$Clave,
        [hashtable]$Cabeceras
    )
    $salida = Join-Path $tmp "respuesta.txt"
    $encab = Join-Path $tmp "encabezados.txt"
    $envio = Join-Path $tmp "envio.json"
    Remove-Item $salida, $encab, $envio -ErrorAction SilentlyContinue

    $a = @("-s", "-X", $Metodo, "-o", $salida, "-D", $encab, "-w", "%{http_code}", "--max-time", "40")
    if ($Token) { $a += @("-H", "Authorization: Bearer $Token") }
    if ($Cafeteria) { $a += @("-H", "X-Cafeteria-Id: $Cafeteria") }
    if ($Clave) { $a += @("-H", "Idempotency-Key: $Clave") }
    if ($Cabeceras) { foreach ($k in $Cabeceras.Keys) { $a += @("-H", "${k}: $($Cabeceras[$k])") } }
    if ($null -ne $Cuerpo) {
        $json = if ($Cuerpo -is [string]) { $Cuerpo } else { $Cuerpo | ConvertTo-Json -Depth 10 -Compress }
        [IO.File]::WriteAllText($envio, $json, $utf8)
        $a += @("-H", "Content-Type: application/json", "--data-binary", "@$envio")
    } elseif ($Metodo -eq "POST") {
        # Los front ends de Google piden Content-Length en un POST sin cuerpo (si no, 411)
        $a += @("-H", "Content-Length: 0")
    }
    $a += $Url

    $codigo = & curl.exe @a
    Remove-Item $envio -ErrorAction SilentlyContinue
    $texto = if (Test-Path $salida) { [IO.File]::ReadAllText($salida, $utf8) } else { "" }
    $cabs = if (Test-Path $encab) { [IO.File]::ReadAllText($encab, $utf8) } else { "" }
    $obj = $null
    if ($texto -and ($texto.TrimStart().StartsWith("{") -or $texto.TrimStart().StartsWith("["))) {
        try { $obj = $texto | ConvertFrom-Json } catch { $obj = $null }
    }
    [pscustomobject]@{ Estado = [int]$codigo; Texto = $texto; Json = $obj; Cabeceras = $cabs }
}

function Cabecera($r, [string]$nombre) {
    foreach ($linea in ($r.Cabeceras -split "`r?`n")) {
        if ($linea -match "^\s*$([regex]::Escape($nombre))\s*:\s*(.*)$") { return $Matches[1].Trim() }
    }
    return $null
}

function Detalle($r) {
    if ($r.Json -and $r.Json.detail) { return "$($r.Estado) - $($r.Json.detail)" }
    $t = $r.Texto -replace "\s+", " "
    if ($t.Length -gt 120) { $t = $t.Substring(0, 120) + "..." }
    return "$($r.Estado) $t"
}

function Anotar([string]$Id, [string]$Que, [string]$Esperado, [bool]$Ok, [string]$Obtenido, [switch]$Omitida) {
    $estado = if ($Omitida) { "OMITIDA" } elseif ($Ok) { "OK" } else { "FALLA" }
    $color = if ($Omitida) { "DarkYellow" } elseif ($Ok) { "Green" } else { "Red" }
    Write-Host ("[{0,-7}] {1,-4} {2}" -f $estado, $Id, $Que) -ForegroundColor $color
    if (-not $Ok -or $Omitida) { Write-Host ("            esperado: {0} | obtenido: {1}" -f $Esperado, $Obtenido) -ForegroundColor DarkGray }
    $resultados.Add([pscustomobject]@{
        Id = $Id; Prueba = $Que; Esperado = $Esperado; Obtenido = $Obtenido; Resultado = $estado
    })
}

function Esperar([string]$Id, [string]$Que, $r, [int[]]$Codigos, [scriptblock]$Ademas = $null, [string]$QueAdemas = "") {
    $ok = $Codigos -contains $r.Estado
    if ($ok -and $Ademas) { $ok = [bool](& $Ademas $r) }
    $esperado = ($Codigos -join " o ")
    if ($QueAdemas) { $esperado += " y $QueAdemas" }
    Anotar $Id $Que $esperado $ok (Detalle $r)
}

function Titulo([string]$t) { Write-Host ""; Write-Host "== $t" -ForegroundColor Cyan }

try {
    # ------------------------------------------------------------ sin sesión
    Titulo "Sin sesión: gateway, Cloud Run y frontend"

    $r = Llamar -Url "$Api/acceso/cafeterias"
    Esperar "N1" "Gateway sin token responde 401" $r @(401)

    $r = Llamar -Url "$Api/acceso/cafeterias" -Token "esto.no.es-un-token"
    Esperar "N2" "Gateway con token falso responde 401" $r @(401)

    $r = Llamar -Url "$Api/ruta-que-no-existe"
    Esperar "N3" "Ruta que no está en el contrato del gateway no llega al backend" $r @(401, 404)

    $pre = @{
        "Origin"                         = $Web
        "Access-Control-Request-Method"  = "POST"
        "Access-Control-Request-Headers" = "authorization,content-type,x-cafeteria-id,idempotency-key"
    }
    $r = Llamar -Metodo "OPTIONS" -Url "$Api/ingresos" -Cabeceras $pre
    Esperar "N4" "CORS: el preflight desde $Web se permite" $r @(200, 204) `
        { param($x) (Cabecera $x "Access-Control-Allow-Origin") -eq $Web } "Access-Control-Allow-Origin = $Web"

    $pre["Origin"] = "https://sitio-malicioso.example"
    $r = Llamar -Metodo "OPTIONS" -Url "$Api/ingresos" -Cabeceras $pre
    $acao = Cabecera $r "Access-Control-Allow-Origin"
    Anotar "N5" "CORS: un origen ajeno no recibe permiso" "sin Access-Control-Allow-Origin para ese origen" `
        ($acao -ne "https://sitio-malicioso.example" -and $acao -ne "*") "$($r.Estado) ACAO=$acao"

    if (-not $UrlCloudRun -and (Get-Command gcloud -ErrorAction SilentlyContinue)) {
        try {
            $UrlCloudRun = (& gcloud run services describe jjskaffee-backend --region us-east1 --project jjskafee --format "value(status.url)" 2>$null)
        } catch { $UrlCloudRun = "" }
    }
    if ($UrlCloudRun) {
        $r = Llamar -Url "$UrlCloudRun/api/acceso/cafeterias"
        Esperar "N6" "Cloud Run directo (sin pasar por el gateway) está cerrado" $r @(401, 403)
    } else {
        Anotar "N6" "Cloud Run directo está cerrado" "403" $true "no se encontró la URL (usa -UrlCloudRun)" -Omitida
    }

    $r = Llamar -Url "$Web/"
    Esperar "N7" "Frontend responde y trae cabeceras de seguridad" $r @(200) {
        param($x)
        ((Cabecera $x "X-Frame-Options") -eq "DENY") -and
        ((Cabecera $x "X-Content-Type-Options") -eq "nosniff") -and
        [bool](Cabecera $x "Referrer-Policy")
    } "X-Frame-Options=DENY, nosniff y Referrer-Policy"

    $r = Llamar -Url "$Web/registrarse"
    Esperar "N8" "Frontend: /registrarse abre (rewrite de la SPA)" $r @(200)

    $httpWeb = $Web -replace "^https://", "http://"
    $r = Llamar -Url "$httpWeb/"
    Esperar "N9" "Frontend: http redirige a https" $r @(301, 302, 307, 308) `
        { param($x) (Cabecera $x "Location") -like "https://*" } "Location https"

    # ------------------------------------------------------------ inicio de sesión
    Titulo "Inicio de sesión con Firebase"

    if (-not $ApiKeyFirebase) {
        $envFront = Join-Path $PSScriptRoot "..\..\..\jjskaffee-frontend\.env.production.local"
        if (Test-Path $envFront) {
            $linea = Get-Content $envFront | Where-Object { $_ -match "^VITE_FIREBASE_API_KEY=(.+)$" } | Select-Object -First 1
            if ($linea -match "^VITE_FIREBASE_API_KEY=(.+)$") { $ApiKeyFirebase = $Matches[1].Trim() }
        }
    }
    if (-not $ApiKeyFirebase) { $ApiKeyFirebase = Read-Host "API key web de Firebase (VITE_FIREBASE_API_KEY)" }

    $correo = Read-Host "Correo de la cuenta (ADMINISTRADOR en A y CAJERO en B)"
    $segura = Read-Host "Contraseña" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura)
    $plano = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    $r = Llamar -Metodo "POST" -Url "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$ApiKeyFirebase" `
        -Cuerpo @{ email = $correo; password = $plano; returnSecureToken = $true } -Cabeceras @{ Referer = "$Web/" }
    $plano = $null
    if ($r.Estado -ne 200 -or -not $r.Json.idToken) {
        $msg = if ($r.Json.error.message) { $r.Json.error.message } else { $r.Estado }
        Write-Host "No se pudo iniciar sesión en Firebase: $msg" -ForegroundColor Red
        Write-Host "Se corrieron solo las pruebas sin sesión." -ForegroundColor Red
        throw "login"
    }
    $T = $r.Json.idToken
    Write-Host "Sesión iniciada como $correo" -ForegroundColor Green

    # ------------------------------------------------------------ acceso
    Titulo "Acceso y multi-cafetería"

    $r = Llamar -Url "$Api/acceso/cafeterias" -Token $T
    $mias = @($r.Json)
    Esperar "A1" "Mis cafeterías: ADMINISTRADOR en A y CAJERO en B" $r @(200) {
        param($x)
        $a = $mias | Where-Object { $_.idCafeteria -eq $CafeteriaA }
        $b = $mias | Where-Object { $_.idCafeteria -eq $CafeteriaB }
        ($a.rol -eq "ADMINISTRADOR") -and ($b.rol -eq "CAJERO")
    } "A=ADMINISTRADOR, B=CAJERO"

    $r = Llamar -Url "$Api/acceso/yo" -Token $T
    Esperar "A2" "Sin X-Cafeteria-Id responde 400" $r @(400)

    $r = Llamar -Url "$Api/acceso/yo" -Token $T -Cafeteria "no-es-un-uuid"
    Esperar "A3" "X-Cafeteria-Id que no es UUID responde 400" $r @(400)

    $r = Llamar -Url "$Api/acceso/yo" -Token $T -Cafeteria $CafeteriaC
    Esperar "A4" "Cafetería a la que no pertenece responde 403" $r @(403)

    $r = Llamar -Url "$Api/acceso/yo" -Token $T -Cafeteria ([guid]::NewGuid())
    Esperar "A5" "Cafetería que no existe responde 403 (no revela si existe)" $r @(403)

    $r = Llamar -Url "$Api/acceso/yo" -Token $T -Cafeteria $CafeteriaA
    Esperar "A6" "En A es ADMINISTRADOR" $r @(200) { param($x) $x.Json.rol -eq "ADMINISTRADOR" } "rol ADMINISTRADOR"

    $r = Llamar -Url "$Api/acceso/yo" -Token $T -Cafeteria $CafeteriaB
    Esperar "A7" "En B es CAJERO" $r @(200) { param($x) $x.Json.rol -eq "CAJERO" } "rol CAJERO"

    # ------------------------------------------------------------ catálogo
    Titulo "Catálogo de venta"

    $r = Llamar -Url "$Api/ingresos/catalogo" -Token $T -Cafeteria $CafeteriaA
    $catA = $r.Json
    Esperar "C1" "Catálogo de A tiene productos" $r @(200) { param($x) @($x.Json.productos).Count -gt 0 } "al menos 1 producto"

    $r = Llamar -Url "$Api/ingresos/catalogo" -Token $T -Cafeteria $CafeteriaB
    $catB = $r.Json
    $idsA = @($catA.productos | ForEach-Object { $_.idProducto })
    Esperar "C2" "Catálogo de B no trae productos de A (aislamiento por cafetería)" $r @(200) {
        param($x) -not (@($x.Json.productos | Where-Object { $idsA -contains $_.idProducto }).Count)
    } "ningún id de A"

    # ------------------------------------------------------------ ventas
    Titulo "Registrar ingreso: venta"

    $base = @($catA.productos | Where-Object { @($_.adiciones).Count -gt 0 }) | Select-Object -First 1
    if (-not $base) { $base = @($catA.productos)[0] }
    $ad = @($base.adiciones) | Select-Object -First 1
    $lineaVenta = @{ idProducto = $base.idProducto; cantidad = 1 }
    $totalEsperado = [decimal]$base.precio
    if ($ad) {
        $lineaVenta.adiciones = @(@{ idProducto = $ad.idProducto; cantidad = 1 })
        $totalEsperado += [decimal]$ad.precio
    }
    $venta = @{ items = @($lineaVenta) }
    $k1 = [guid]::NewGuid().ToString()

    $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaA -Cuerpo $venta -Clave $k1
    $idVenta = $r.Json.idVenta
    Esperar "V1" "Venta válida ($($base.nombre)$(if ($ad) { ' + ' + $ad.nombre })) se registra con el total correcto" $r @(201) {
        param($x) [decimal]$x.Json.total -eq $totalEsperado
    } "total $totalEsperado"

    $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaA -Cuerpo $venta -Clave $k1
    Esperar "V2" "Reintento con la misma Idempotency-Key devuelve la misma venta" $r @(200, 201) {
        param($x) $x.Json.idVenta -eq $idVenta
    } "mismo idVenta"

    $otra = @{ items = @(@{ idProducto = $base.idProducto; cantidad = 2 }) }
    $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaA -Cuerpo $otra -Clave $k1
    Esperar "V3" "Misma Idempotency-Key con otros productos responde 422" $r @(422)

    if ($idVenta) {
        $r = Llamar -Metodo POST -Url "$Api/ingresos/$idVenta/deshacer" -Token $T -Cafeteria $CafeteriaA
        Esperar "V4" "Deshacer la venta dentro de los 60 s" $r @(204)
        $r = Llamar -Metodo POST -Url "$Api/ingresos/$idVenta/deshacer" -Token $T -Cafeteria $CafeteriaA
        Esperar "V5" "Deshacer otra vez responde 422 (ya estaba anulada)" $r @(422)
    } else {
        Anotar "V4" "Deshacer la venta" "204" $false "no hubo venta en V1" -Omitida
        Anotar "V5" "Deshacer otra vez" "422" $false "no hubo venta en V1" -Omitida
    }

    $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaA -Cuerpo '{"items":[]}'
    Esperar "V6" "Venta sin productos responde 400" $r @(400)

    $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaA `
        -Cuerpo @{ items = @(@{ idProducto = $base.idProducto; cantidad = 0 }) }
    Esperar "V7" "Cantidad 0 responde 400" $r @(400)

    $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaA `
        -Cuerpo @{ items = @(@{ idProducto = $base.idProducto; cantidad = 100 }) }
    Esperar "V8" "Cantidad 100 responde 400 (máximo 99)" $r @(400)

    if ($ad) {
        $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaA `
            -Cuerpo @{ items = @(@{ idProducto = $ad.idProducto; cantidad = 1 }) }
        Esperar "V9" "Una adición sola, sin producto base, responde 422" $r @(422)

        $todasAdiciones = @($catA.productos | ForEach-Object { @($_.adiciones) } | ForEach-Object { $_.idProducto }) | Sort-Object -Unique
        $permitidas = @($base.adiciones | ForEach-Object { $_.idProducto })
        $ajena = $todasAdiciones | Where-Object { $permitidas -notcontains $_ } | Select-Object -First 1
        if ($ajena) {
            $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaA `
                -Cuerpo @{ items = @(@{ idProducto = $base.idProducto; cantidad = 1; adiciones = @(@{ idProducto = $ajena; cantidad = 1 }) }) }
            Esperar "V10" "Adición que no aplica a ese producto responde 422" $r @(422)
        } else {
            Anotar "V10" "Adición que no aplica a ese producto" "422" $true "no hay una adición ajena en el catálogo" -Omitida
        }
    } else {
        Anotar "V9" "Adición sola" "422" $true "el catálogo no tiene adiciones" -Omitida
        Anotar "V10" "Adición que no aplica" "422" $true "el catálogo no tiene adiciones" -Omitida
    }

    $prodB = @($catB.productos)[0]
    if ($prodB) {
        $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaA `
            -Cuerpo @{ items = @(@{ idProducto = $prodB.idProducto; cantidad = 1 }) }
        Esperar "V11" "Vender en A un producto de B responde 404 (RLS)" $r @(404)
    } else {
        Anotar "V11" "Vender en A un producto de B" "404" $true "B no tiene productos" -Omitida
    }

    $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaA `
        -Cuerpo @{ items = @(@{ idProducto = [guid]::NewGuid().ToString(); cantidad = 1 }) }
    Esperar "V12" "Producto que no existe responde 404" $r @(404)

    $r = Llamar -Metodo POST -Url "$Api/ingresos/$([guid]::NewGuid())/deshacer" -Token $T -Cafeteria $CafeteriaA
    Esperar "V13" "Deshacer una venta que no existe responde 404" $r @(404)

    $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaA -Cuerpo $venta -Clave "no-es-uuid"
    Esperar "V14" "Idempotency-Key que no es UUID responde 400" $r @(400)

    if ($prodB) {
        $r = Llamar -Metodo POST -Url "$Api/ingresos" -Token $T -Cafeteria $CafeteriaB `
            -Cuerpo @{ items = @(@{ idProducto = $prodB.idProducto; cantidad = 1 }) } -Clave ([guid]::NewGuid().ToString())
        $idVentaB = $r.Json.idVenta
        Esperar "V15" "El CAJERO (en B) puede vender" $r @(201)
        if ($idVentaB) {
            $r = Llamar -Metodo POST -Url "$Api/ingresos/$idVentaB/deshacer" -Token $T -Cafeteria $CafeteriaB
            Esperar "V16" "El CAJERO deshace su propia venta" $r @(204)
            $r = Llamar -Metodo POST -Url "$Api/ingresos/$idVentaB/deshacer" -Token $T -Cafeteria $CafeteriaA
            Esperar "V17" "Una venta de B no se puede deshacer desde A (404)" $r @(404, 422)
        }
    }

    # ------------------------------------------------------------ ingreso libre
    Titulo "Registrar ingreso: ingreso libre"

    $catIngresoA = "c0000000-0000-0000-0000-000000000051"
    $libre = @{ idCategoria = $catIngresoA; monto = 1; descripcion = "Prueba automática" }

    $r = Llamar -Metodo POST -Url "$Api/ingresos/libres" -Token $T -Cafeteria $CafeteriaB `
        -Cuerpo @{ idCategoria = [guid]::NewGuid().ToString(); monto = 1; descripcion = "Prueba" }
    Esperar "L1" "El CAJERO no puede registrar ingresos libres (403)" $r @(403)

    $casos = @(
        @{ id = "L2"; que = "Monto 0 responde 400"; cuerpo = @{ idCategoria = $catIngresoA; monto = 0; descripcion = "x" } },
        @{ id = "L3"; que = "Monto negativo responde 400"; cuerpo = @{ idCategoria = $catIngresoA; monto = -5; descripcion = "x" } },
        @{ id = "L4"; que = "Monto con 11 dígitos enteros responde 400"; cuerpo = '{"idCategoria":"' + $catIngresoA + '","monto":12345678901,"descripcion":"x"}' },
        @{ id = "L5"; que = "Monto con 3 decimales responde 400"; cuerpo = '{"idCategoria":"' + $catIngresoA + '","monto":10.123,"descripcion":"x"}' },
        @{ id = "L6"; que = "Descripción vacía responde 400"; cuerpo = @{ idCategoria = $catIngresoA; monto = 1; descripcion = "   " } },
        @{ id = "L7"; que = "Descripción de 201 caracteres responde 400"; cuerpo = @{ idCategoria = $catIngresoA; monto = 1; descripcion = ("a" * 201) } },
        @{ id = "L8"; que = "Comprobante de 51 caracteres responde 400"; cuerpo = @{ idCategoria = $catIngresoA; monto = 1; descripcion = "x"; comprobante = ("c" * 51) } },
        @{ id = "L9"; que = "Sin categoría responde 400"; cuerpo = @{ monto = 1; descripcion = "x" } }
    )
    foreach ($c in $casos) {
        $r = Llamar -Metodo POST -Url "$Api/ingresos/libres" -Token $T -Cafeteria $CafeteriaA -Cuerpo $c.cuerpo
        Esperar $c.id $c.que $r @(400)
    }

    $r = Llamar -Metodo POST -Url "$Api/ingresos/libres" -Token $T -Cafeteria $CafeteriaA `
        -Cuerpo @{ idCategoria = "c0000000-0000-0000-0000-000000000011"; monto = 1; descripcion = "x" }
    Esperar "L10" "Categoría de EGRESO responde 422" $r @(422)

    $r = Llamar -Metodo POST -Url "$Api/ingresos/libres" -Token $T -Cafeteria $CafeteriaA `
        -Cuerpo @{ idCategoria = "c0000000-0000-0000-0000-000000000055"; monto = 1; descripcion = "x" }
    Esperar "L11" "Categoría archivada responde 422" $r @(422)

    $r = Llamar -Metodo POST -Url "$Api/ingresos/libres" -Token $T -Cafeteria $CafeteriaA `
        -Cuerpo @{ idCategoria = [guid]::NewGuid().ToString(); monto = 1; descripcion = "x" }
    Esperar "L12" "Categoría que no existe responde 404" $r @(404)

    $catIngresoB = @($catB.categoriasIngreso)[0]
    if ($catIngresoB) {
        $r = Llamar -Metodo POST -Url "$Api/ingresos/libres" -Token $T -Cafeteria $CafeteriaA `
            -Cuerpo @{ idCategoria = $catIngresoB.idCategoria; monto = 1; descripcion = "x" }
        Esperar "L13" "Usar en A una categoría de B responde 404 (RLS)" $r @(404)
    } else {
        Anotar "L13" "Usar en A una categoría de B" "404" $true "el catálogo de B no trae categorías de ingreso" -Omitida
    }

    $idIngreso = $null
    if (-not $SinIngresoLibre) {
        $k2 = [guid]::NewGuid().ToString()
        $r = Llamar -Metodo POST -Url "$Api/ingresos/libres" -Token $T -Cafeteria $CafeteriaA -Cuerpo $libre -Clave $k2
        $idIngreso = $r.Json.idIngreso
        Esperar "L14" "Ingreso libre válido de `$1 se registra" $r @(201) {
            param($x) [decimal]$x.Json.monto -eq 1 -and $x.Json.descripcion -eq "Prueba automática"
        } "monto 1 y la misma descripción (con tilde)"

        $r = Llamar -Metodo POST -Url "$Api/ingresos/libres" -Token $T -Cafeteria $CafeteriaA -Cuerpo $libre -Clave $k2
        Esperar "L15" "Reintento con la misma Idempotency-Key devuelve el mismo ingreso" $r @(200, 201) {
            param($x) $x.Json.idIngreso -eq $idIngreso
        } "mismo idIngreso"

        $r = Llamar -Metodo POST -Url "$Api/ingresos/libres" -Token $T -Cafeteria $CafeteriaA `
            -Cuerpo @{ idCategoria = $catIngresoA; monto = 2; descripcion = "Prueba automática" } -Clave $k2
        Esperar "L16" "Misma Idempotency-Key con otro monto responde 422" $r @(422)
    } else {
        Anotar "L14" "Ingreso libre válido" "201" $true "-SinIngresoLibre" -Omitida
    }

    # ------------------------------------------------------------ movimientos
    Titulo "Movimientos"

    $r = Llamar -Url "$Api/movimientos" -Token $T -Cafeteria $CafeteriaA
    Esperar "M1" "Movimientos de A con totales" $r @(200) {
        param($x) $null -ne $x.Json.totales -and $null -ne $x.Json.totales.balance
    } "trae totales"

    foreach ($c in @(
            @{ id = "M2"; q = "tamano=51"; que = "Tamaño de página 51 responde 400" },
            @{ id = "M3"; q = "tamano=0"; que = "Tamaño de página 0 responde 400" },
            @{ id = "M4"; q = "pagina=-1"; que = "Página -1 responde 400" },
            @{ id = "M5"; q = "desde=2026-13-40"; que = "Fecha inválida responde 400" })) {
        $r = Llamar -Url "$Api/movimientos?$($c.q)" -Token $T -Cafeteria $CafeteriaA
        Esperar $c.id $c.que $r @(400)
    }

    $r = Llamar -Url "$Api/movimientos?desde=2026-11-10&hasta=2026-11-01" -Token $T -Cafeteria $CafeteriaA
    Esperar "M6" "'desde' posterior a 'hasta' responde 422" $r @(422)

    if ($idIngreso) {
        $hoy = (Get-Date).ToString("yyyy-MM-dd")
        $r = Llamar -Url "$Api/movimientos?desde=$hoy&hasta=$hoy&tamano=50" -Token $T -Cafeteria $CafeteriaA
        Esperar "M7" "El ingreso libre de hoy aparece en los movimientos de A (hora de Colombia)" $r @(200) {
            param($x) @($x.Json.movimientos | Where-Object { $_.idMovimiento -eq $idIngreso }).Count -eq 1
        } "aparece una vez"

        $r = Llamar -Url "$Api/movimientos?desde=$hoy&hasta=$hoy&tamano=50" -Token $T -Cafeteria $CafeteriaB
        Esperar "M8" "Ese ingreso NO aparece en los movimientos de B (RLS)" $r @(200) {
            param($x) @($x.Json.movimientos | Where-Object { $_.idMovimiento -eq $idIngreso }).Count -eq 0
        } "no aparece"
    }

    # ------------------------------------------------------------ WAF
    if ($conWaf) {
        Titulo "WAF (Cloud Armor delante del gateway)"

        $r = Llamar -Url "$Gateway/acceso/cafeterias" -Token $T
        Esperar "W1" "Saltarse el WAF (directo a *.gateway.dev con token) responde 403 del backend" $r @(403)

        $ataques = @(
            @{ id = "W2"; que = "Inyección SQL en la URL la bloquea Cloud Armor"; q = "q=%27%20OR%20%271%27%3D%271" },
            @{ id = "W3"; que = "XSS en la URL lo bloquea Cloud Armor"; q = "q=%3Cscript%3Ealert(1)%3C%2Fscript%3E" },
            @{ id = "W4"; que = "Recorrido de carpetas (../../etc/passwd) lo bloquea Cloud Armor"; q = "archivo=..%2F..%2F..%2Fetc%2Fpasswd" },
            @{ id = "W5"; que = "Ejecución remota (;cat /etc/passwd) la bloquea Cloud Armor"; q = "cmd=%3Bcat%20%2Fetc%2Fpasswd" }
        )
        foreach ($c in $ataques) {
            $r = Llamar -Url "$Api/movimientos?$($c.q)" -Token $T -Cafeteria $CafeteriaA
            Esperar $c.id $c.que $r @(403) { param($x) -not $x.Json.detail } "403 de Cloud Armor (no del backend)"
        }

        $r = Llamar -Url "$Api/acceso/cafeterias" -Cabeceras @{ "User-Agent" = "sqlmap/1.7.2#stable (https://sqlmap.org)" }
        Esperar "W6" "Un escáner conocido (User-Agent de sqlmap) lo bloquea Cloud Armor" $r @(403)

        $r = Llamar -Metodo POST -Url "$Api/ingresos/libres" -Token $T -Cafeteria $CafeteriaA `
            -Cuerpo @{ idCategoria = $catIngresoA; monto = 0; descripcion = "x' OR '1'='1" }   # monto 0: si pasa, no se guarda
        Esperar "W7" "Inyección SQL en el cuerpo JSON: la frena el WAF o la rechaza el backend" $r @(400, 403, 422)

        if ($ProbarLimite) {
            Write-Host "    Mandando 120 peticiones (unos segundos)..." -ForegroundColor DarkGray
            $codigos = 1..120 | ForEach-Object { & curl.exe -s -o NUL -w "%{http_code}" --max-time 20 "$Api/acceso/cafeterias" }
            $bloqueadas = @($codigos | Where-Object { $_ -eq "429" }).Count
            Anotar "W8" "Límite por IP: después de unas 100 por minuto responde 429" "al menos una 429" `
                ($bloqueadas -gt 0) "$bloqueadas de 120 con 429"
        } else {
            Anotar "W8" "Límite por IP (429)" "429" $true "córrelo con -ProbarLimite" -Omitida
        }
    } else {
        Anotar "W1" "Pruebas del WAF" "-" $true "la API no entra por el WAF (-Api igual a -Gateway)" -Omitida
    }
}
catch {
    if ($_.Exception.Message -ne "login") { Write-Host "Error inesperado del script: $($_.Exception.Message)" -ForegroundColor Red }
}
finally {
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    $T = $null

    $ok = @($resultados | Where-Object { $_.Resultado -eq "OK" }).Count
    $mal = @($resultados | Where-Object { $_.Resultado -eq "FALLA" }).Count
    $omi = @($resultados | Where-Object { $_.Resultado -eq "OMITIDA" }).Count
    Write-Host ""
    Write-Host ("Resultado: {0} OK, {1} FALLA, {2} omitidas (de {3})" -f $ok, $mal, $omi, $resultados.Count) `
        -ForegroundColor $(if ($mal -eq 0) { "Green" } else { "Red" })

    if ($resultados.Count -gt 0) {
        $csv = Join-Path $PSScriptRoot ("resultados-{0}.csv" -f (Get-Date -Format "yyyyMMdd-HHmm"))
        $resultados | Export-Csv -Path $csv -NoTypeInformation -Encoding UTF8
        Write-Host "Resultados guardados en $csv"
    }
}
