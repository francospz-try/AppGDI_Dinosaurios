# AppGDI Dinosaurios

Cliente de escritorio para el registro de comprobantes de Sermed Ingenieros.
Java 21, Swing, JDBC directo y PostgreSQL. Sin ORM ni frameworks de aplicación.

## Primer incremento

- Conexión comprobable y configuración externa.
- Búsqueda de comprobantes por serie-número, cliente o documento (hasta 500 filas).
- Consulta de cabecera, líneas y referencias; importes decimales exactos.
- Alta, consulta, modificación y baja lógica de clientes.
- Doce reportes mediante procedimientos; siete contienen JOIN.
- Operaciones JDBC fuera del hilo de eventos de la interfaz.

**Pendiente para el 70 %:** alta/edición/baja de comprobantes y sus líneas y
referencias, mantenimiento de los demás catálogos, integridad al eliminar la última
línea y pruebas de esos flujos. Los doce reportes conservan los ejemplos y periodos
del informe; no todos sus parámetros son todavía editables desde la interfaz.
La memoria, el manual Word/PDF y el empaquetado portable completo corresponden al
cierre del proyecto. Este incremento no se presenta como aplicativo al 100 %.

## Preparación en Windows

Requisitos: Git, **JDK 21 completo** (java, javac y jar en PATH), PostgreSQL 17 o
superior y psql en PATH. La primera compilación descarga pgJDBC 42.7.7 (checksum
verificado); no utiliza Maven. Desde PowerShell:

```powershell
New-Item -ItemType Directory -Force C:\Franco\2026\GDI | Out-Null
Set-Location C:\Franco\2026\GDI
git clone https://github.com/francospz-try/AppGDI_Dinosaurios.git
Set-Location AppGDI_Dinosaurios
powershell -ExecutionPolicy Bypass -File .\scripts\preparar-windows.ps1
```

El preparador conserva el aplicativo y clona **aparte** `superpowers` y
`mattpocock-skills` dentro de la carpeta GDI. Enlaza las habilidades estables de
Superpowers y las de ingeniería/productividad de Matt en `%USERPROFILE%\.agents\skills`
para Codex local. No modifica repositorios ya existentes ni instala JDK/PostgreSQL.
La habilitación de habilidades en ChatGPT es independiente de estos enlaces locales.

## Instalar la base

**Solo en una base de desarrollo nueva o descartable.** El script original 01
elimina `registro_comprobantes` si existe; haga respaldo si necesita conservarla.
El aplicativo nunca ejecuta ese script automáticamente.

```powershell
psql -X -v ON_ERROR_STOP=1 -U postgres -d postgres -f database/original/01_creacion.sql
psql -X -v ON_ERROR_STOP=1 -U postgres -d registro_comprobantes -f database/original/02_insercion.sql
psql -X -v ON_ERROR_STOP=1 -U postgres -d registro_comprobantes -f database/04_aplicativo.sql
psql -X -v ON_ERROR_STOP=1 -U postgres -d registro_comprobantes -f database/05_reportes.sql
psql -X -v ON_ERROR_STOP=1 -U postgres -d registro_comprobantes -f database/06_permisos.sql
psql -X -U postgres -d registro_comprobantes
```

En la sesión de psql, asigne la contraseña del rol sin escribirla en el script:

```text
\password gdi_app
\q
```

Para actualizar una base existente, aplique **solo** 04, 05 y 06. No vuelva a cargar
01/02: no son migraciones. La carga original produce 5328 comprobantes con datos
de clientes ficticios. El aplicativo no usa el superusuario postgres.

## Compilar y abrir

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1
.\Iniciar.cmd
```

Puede introducir los datos de conexión en pantalla, o copiar
`config/database.properties.example` como `config/database.properties` y editarlo.
La configuración real está excluida de Git. No existe un login de aplicación:
esta versión comprueba las credenciales PostgreSQL del rol autorizado.

En Linux: `bash scripts/build.sh` y `bash scripts/run.sh`.

## Pruebas

Use otra instancia o base **descartable** con la carga original y los scripts
04–06. Las pruebas crean, modifican y dan de baja un cliente ficticio aleatorio.

```powershell
$env:GDI_TEST_DATABASE = 'disposable'
powershell -ExecutionPolicy Bypass -File .\scripts\test.ps1 -ConfigFile C:\ruta\pruebas.properties
```

En Linux: `GDI_TEST_DATABASE=disposable bash scripts/test.sh /ruta/pruebas.properties`.
Prueba adicional de eventos y renderizado Swing, después de compilar las pruebas:

```powershell
java -Djava.awt.headless=true -cp 'build/classes;build/test-classes;lib/postgresql-42.7.7.jar' pe.edu.ucsm.gdi.UiSmoke C:\ruta\pruebas.properties
```

Resultados y límites: [docs/verificacion.md](docs/verificacion.md).
Requisitos del docente: [docs/requisitos.md](docs/requisitos.md).

## Estructura

| Ruta | Contenido |
|---|---|
| `src/main/java` | Configuración, procedimientos JDBC, operaciones e interfaz |
| `src/test/java` | Integración y eventos/renderizado Swing |
| `database/original` | Tres scripts originales conservados |
| `database/04–06` | Ampliación de cliente, procedimientos, reportes y permisos |
| `config` | Plantilla de configuración externa |
| `scripts` | Compilación, ejecución, pruebas y preparación Windows |
| `docs` | Requisitos, diseño, plan y evidencia verificable |

## Decisiones respecto al informe

1. Los clientes pueden darse de alta antes de registrar un comprobante. Se añade
   `cliente.activo` para baja lógica; su historial permanece consultable.
2. Se registra lo impreso: NULL no equivale a cero, ni valor unitario a precio unitario.
3. Un comprobante registrado no se interpreta como un cobro recibido.
4. El informe LaTeX seguirá siendo acumulativo; no se publican sus anexos personales
   en este repositorio público.
