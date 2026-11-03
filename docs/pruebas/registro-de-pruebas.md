# Registro de pruebas contra el despliegue

Pruebas hechas sobre la aplicación publicada, no sobre el código local. Las pruebas unitarias, de integración (Testcontainers) y de arquitectura (ArchUnit) corren aparte en el CI de GitHub Actions con cada push.

**Fecha:** 2 de noviembre de 2026
**Ambiente:**

| Pieza | Dónde |
| --- | --- |
| Frontend | https://jjskafee.web.app (Firebase Hosting) |
| API sin WAF | `https://jjskaffee-gateway-dcdyujod.ue.gateway.dev/api` (API Gateway) |
| API con WAF | `https://136-110-153-51.sslip.io/api` (balanceador HTTPS + Cloud Armor → API Gateway) |
| Backend | Cloud Run `jjskaffee-backend` (privado), revisión `00003` |
| Base de datos | Cloud SQL PostgreSQL 17 con RLS forzado |

**Cuenta de prueba:** `joel@prueba.com`, ADMINISTRADOR en la cafetería A (Café La Esquina) y CAJERO en la B (Café El Parque).

## 1. Pruebas automáticas

Script: [`infra/pruebas/pruebas-nube.ps1`](../../infra/pruebas/pruebas-nube.ps1). Llama a la API real con `curl.exe` e inicia sesión en Firebase con la cuenta de prueba (la contraseña se pide por consola y no se guarda).

```powershell
powershell -ExecutionPolicy Bypass -File .\infra\pruebas\pruebas-nube.ps1 -ProbarLimite
```

| Corrida | API | Resultado | Evidencia |
| --- | --- | --- | --- |
| Antes del WAF | API Gateway | 58 OK, 0 fallas, 1 omitida | [`resultados-20261102-1954.csv`](../../infra/pruebas/resultados-20261102-1954.csv) |
| Con el WAF activo | Balanceador + Cloud Armor | 66 OK, 0 fallas, 1 omitida | [`resultados-20261102-2032.csv`](../../infra/pruebas/resultados-20261102-2032.csv) |

La omitida es L13: el catálogo no le manda categorías de ingreso al cajero (no puede registrar ingresos libres), así que no hay con qué armar esa prueba.

| Grupo | Qué se prueba |
| --- | --- |
| N1–N9 Sin sesión | Gateway sin token o con token falso (401), ruta fuera del contrato, CORS solo para el frontend, Cloud Run cerrado si se llama directo, cabeceras de seguridad del frontend, `/registrarse` y redirección a HTTPS |
| A1–A7 Acceso | Rol por cafetería, falta de `X-Cafeteria-Id` (400), id inválido (400), cafetería ajena o inexistente (403) |
| C1–C2 Catálogo | Cada cafetería ve solo su menú |
| V1–V17 Venta | Total correcto, idempotencia, deshacer dentro de 60 s, cantidades 0 y 100, adición sola o que no aplica, producto de otra cafetería (RLS), el cajero vende y deshace lo suyo |
| L1–L16 Ingreso libre | Solo el administrador, validaciones de monto, descripción y comprobante, categorías de egreso, archivadas o inexistentes, idempotencia |
| M1–M8 Movimientos | Totales, paginación, fechas inválidas, el ingreso aparece en A y no en B |
| W1–W8 WAF | No se puede saltar el WAF por `*.gateway.dev` (403), Cloud Armor bloquea inyección SQL, XSS, recorrido de carpetas, ejecución remota y escáneres (403), y el límite de 100 peticiones por minuto (429) |

Durante la activación del WAF, una corrida dio 403 en todo lo que llegaba al backend porque se lanzó con la versión anterior del script, que todavía apuntaba a `*.gateway.dev`. Ese resultado confirma el bloqueo de W1, pero no se guardó como evidencia.

## 2. Pruebas manuales en el navegador

Hechas en https://jjskafee.web.app en el computador (Chrome).

### Registro (cuenta nueva `prueba1@prueba.com`)

| # | Pasos | Esperado | Resultado |
| --- | --- | --- | --- |
| R1 | Crear cuenta con los campos vacíos | Marca los campos obligatorios | OK |
| R2 | Contraseña de 4 caracteres | Pide mínimo 8 caracteres | OK |
| R3 | Contraseña y confirmación distintas | Avisa que no coinciden | OK |
| R4 | Correo que ya existe | Avisa que el correo ya está registrado, en español | OK |
| R5 | Datos válidos | Crea la cuenta y muestra el código (UID) con "Copiar código" | OK |
| R6 | Copiar código | El UID queda en el portapapeles | OK |
| R7 | "Ya me agregaron, actualizar" sin membresía | Se queda en la misma pantalla, sin error | OK |

### Venta, ingreso libre y movimientos (`joel@prueba.com`)

| # | Pasos | Esperado | Resultado |
| --- | --- | --- | --- |
| P3 | Cafetería A: carrito con varios productos y una adición, cambiar cantidades, quitar uno y registrar | El total se recalcula y la venta se registra | OK |
| P3b | Esc con el carrito lleno | Cancela o vacía el carrito | OK |
| P4 | Deshacer antes de 60 s | La venta queda anulada | OK |
| P4b | Deshacer después de 60 s | No deja deshacer y explica por qué | OK |
| P5 | Ingreso libre con montos `abc`, `0`, `-5`, `1,234` o vacío, y sin descripción | Muestra el error en cada caso | OK |
| P5b | Ingreso libre válido: Eventos privados, $50.000, "Prueba manual" | Se registra | OK |
| P6 | Cafetería B (cajero) | No aparece Ingreso libre; vende y deshace | OK |
| P7 | Movimientos: lista, filtro por fechas, "desde" posterior a "hasta", totales, B no ve lo de A | Cada caso como se describe | OK |
| P8 | Celular: iniciar sesión, vender y ver movimientos | Se ve completo y se puede usar | OK |
| P9 | Cerrar sesión, botón Atrás y abrir `/movimientos` directo | Vuelve al login | OK |

### Después de activar el WAF

| # | Pasos | Esperado | Resultado |
| --- | --- | --- | --- |
| P10 | Ctrl+F5 en la web, iniciar sesión, vender, deshacer y ver movimientos | Todo funciona entrando por `136-110-153-51.sslip.io` | OK |

## 3. Observaciones

- En la pantalla sin cafetería, el enlace "Cerrar sesión" queda corrido a la derecha respecto al botón de arriba. Es solo visual; se corrige en el frontend.
