# Pruebas manuales en un Windows limpio

Para quien prueba el instalador **Embebido** (y, al final, el Headless) en una
computadora que nunca tuvo AppGDI. Marque cada casilla y anote lo que no
coincida, con una captura y el archivo `arranque.log`.

Datos del equipo (anótelos): versión de Windows (`winver`), idioma, nombre de
usuario (¿tiene tildes o ñ?), espacio libre en C:, antivirus, si hay un
PostgreSQL instalado.

Lo ideal es un usuario estándar (sin administrador) cuyo nombre tenga tilde,
por ejemplo `José`. Si no puede crearlo, pruebe con el suyo y anótelo.

## 1. Descarga y SmartScreen

- [ ] Descargue `AppGDI-Dinosaurios-Embebido-Windows-x64.exe` y `SHA256SUMS.txt` de
      la página de Releases.
- [ ] En PowerShell: `Get-FileHash .\AppGDI-Dinosaurios-Embebido-Windows-x64.exe`. El
      valor coincide con el de `SHA256SUMS.txt`.
- [ ] Si el navegador avisa que el archivo «no se descarga con frecuencia», elija
      **Conservar** (en Edge: «…» → Conservar → Mostrar más → Conservar de todas formas).
- [ ] Al abrirlo aparece «Windows protegió su PC» con «Editor: Desconocido». Captura.
- [ ] **Más información** → **Ejecutar de todas formas** abre el asistente.
- [ ] No aparece el aviso de Control de cuentas de usuario (UAC): no pide
      administrador.

## 2. Instalación

- [ ] La carpeta propuesta es `C:\Users\<usuario>\AppData\Local\Programs\AppGDI-Dinosaurios-Embebido`
      (no `Program Files`).
- [ ] El asistente está en español y termina sin errores.
- [ ] En el menú Inicio, carpeta «AppGDI Dinosaurios (Embebido)», hay cinco accesos:
      abrir, detener PostgreSQL, respaldar, restaurar y desinstalar.
- [ ] No se instaló Docker, WSL ni Hyper-V, ni se pidió activar la virtualización.

## 3. Primera ejecución

- [ ] Al abrir aparece «Preparando la base de datos de AppGDI por primera vez». Anote
      cuántos segundos tarda hasta la ventana principal.
- [ ] Pregunta si quiere cambiar la contraseña de `gdi_app`. Pruebe «Sí» con una clave
      corta (debe rechazarla) y luego una válida de 16 o más caracteres.
- [ ] Se abre la ventana principal **sin** una ventana negra de consola detrás.
- [ ] Existe `%LOCALAPPDATA%\AppGDI-Dinosaurios\embebido` con `postgresql\`,
      `config.ini`, `base-lista`, `arranque.log` y `postgresql.log`. Anote el tamaño
      de `postgresql\` (unos 70 MB).
- [ ] En el Administrador de tareas (Detalles) hay varios `postgres.exe` del usuario.
- [ ] Busque un cliente con «clinica» (sin tilde): aparecen los que dicen «CLÍNICA».
- [ ] Registre un cliente de prueba y anote su nombre.

## 4. Cierre y siguientes ejecuciones

- [ ] Cierre AppGDI con la X. En unos segundos no queda ningún `postgres.exe` ni
      `javaw.exe`, y no existe `postgresql\postmaster.pid`.
- [ ] Ábrala otra vez: no muestra el aviso de primera vez, abre en pocos segundos y el
      cliente de prueba sigue ahí.
- [ ] Con AppGDI abierta, ábrala de nuevo desde el menú: aparece «AppGDI ya está
      abierta» y no se abre una segunda ventana.

## 5. Cierre brusco

- [ ] Con AppGDI abierta, finalice `javaw.exe` en el Administrador de tareas.
      Los `postgres.exe` siguen activos (es lo esperado).
- [ ] Abra AppGDI: arranca normalmente, sin errores de bloqueo, y el cliente de prueba
      sigue. Al cerrarla, no queda ningún `postgres.exe`.
- [ ] Repita finalizando también los `postgres.exe` («Finalizar árbol de procesos»)
      antes de volver a abrir. Arranca y los datos siguen.
- [ ] Opcional: con AppGDI abierta, reinicie Windows. Después del reinicio abre
      normalmente.

## 6. Otro PostgreSQL en el puerto 5432

Si el equipo ya tiene PostgreSQL (o instale el de EDB para esta prueba):

- [ ] Con ese servidor activo en 5432 (`netstat -ano | findstr :5432`), AppGDI abre y
      funciona. `db.puerto` en `config.ini` **no** es 5432.
- [ ] El otro PostgreSQL sigue funcionando y sus bases no cambiaron.
- [ ] Defina la variable de usuario `PGPORT=5432` (y, si quiere, `PGDATA`), cierre
      sesión y vuelva a entrar: AppGDI sigue abriendo.

## 7. Espacio en disco

- [ ] Anote el espacio libre antes y después de instalar y de la primera ejecución.
- [ ] Opcional, en una máquina virtual o una unidad USB: con menos de 300 MB libres y
      sin datos previos (borre `%LOCALAPPDATA%\AppGDI-Dinosaurios\embebido`), AppGDI
      muestra «No hay espacio suficiente…» con los MB libres y los necesarios, en vez
      de un error de PostgreSQL.

## 8. Permisos del rol del aplicativo

- [ ] Abra `config.ini`: `db.usuario` es `gdi_app`.
- [ ] En la pestaña de conexión del aplicativo, la conexión es con `gdi_app`.
- [ ] Opcional, con `psql` de otra instalación: conectado como `gdi_app` al puerto de
      `config.ini`, `DELETE FROM comprobante;` da «permission denied».

## 9. Respaldo y restauración

- [ ] Con AppGDI cerrada, «Respaldar la base de AppGDI» propone
      `AppGDI-respaldo-<fecha>.zip` en su carpeta de usuario; guárdelo en Documentos.
      Aparece «Respaldo guardado…» y el `.zip` pesa unos 9 MB.
- [ ] Con AppGDI abierta, el respaldo se niega con «AppGDI ya está abierta».
- [ ] Abra AppGDI, dé de baja el cliente de prueba y ciérrela.
- [ ] «Restaurar un respaldo de AppGDI», elija el `.zip` y confirme. Aparece la carpeta
      `antes-de-restaurar-<fecha>`. Al abrir AppGDI, el cliente de prueba está activo
      otra vez.
- [ ] Copie el `.zip` a otra computadora con AppGDI y restáurelo allí: abre con los
      mismos datos.

## 10. Rutas con espacios y tildes

- [ ] Si el usuario no tiene tildes: reinstale eligiendo la carpeta
      `C:\Users\<usuario>\Documents\Programas José\AppGDI` y repita 3 y 4.

## 11. Actualizar y desinstalar

- [ ] Con AppGDI cerrada, ejecute otra vez el instalador (o uno más nuevo): termina sin
      «archivo en uso» y los datos siguen.
- [ ] Desinstale desde el menú Inicio o Configuración → Aplicaciones: no quedan
      `postgres.exe` activos ni la carpeta del programa. La carpeta
      `%LOCALAPPDATA%\AppGDI-Dinosaurios\embebido` **sí** queda (los datos no se borran).

## 12. Variante Headless (resumen)

- [ ] Instale `AppGDI-Dinosaurios-Headless-Windows-x64.exe`. Usa
      `%LOCALAPPDATA%\AppGDI-Dinosaurios\headless`, aparte de la Embebida.
- [ ] Al cerrar AppGDI, `postgres.exe` **sigue** activo; al abrirla de nuevo, arranca
      enseguida.
- [ ] «Detener PostgreSQL de AppGDI» lo detiene.

## Qué enviar

Las casillas marcadas, los tiempos y tamaños anotados, capturas de SmartScreen y de
cualquier error, y `arranque.log` y `postgresql.log` de la carpeta de datos.
