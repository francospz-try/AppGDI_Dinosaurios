# AppGDI Dinosaurios

Aplicativo de escritorio para el registro de comprobantes de pago de Sermed
Ingenieros E.I.R.L.: registra, consulta, modifica y da de baja (borrado
lógico) boletas y facturas ya emitidas, con sus clientes, productos y
servicios, y ofrece doce reportes. Java 21 con Swing y JDBC directo sobre
PostgreSQL 17, sin ORM ni frameworks de aplicación.

Proyecto del curso Gestión de Datos e Información (UCSM, 2026). Esta versión
integra las dos implementaciones del equipo: la base de datos, los reportes
y el aplicativo descritos en el informe del proyecto, con el rol de conexión,
la compilación sin Maven, los scripts de Windows y las pruebas de integridad
y de interfaz de la versión anterior de este repositorio.

## Contenido

| Ruta | Contenido |
|---|---|
| `aplicativo/` | Código fuente (`sermed.comprobantes`), `pom.xml`, plantilla de configuración y [README del aplicativo](aplicativo/README.md) |
| `scripts/` | Script completo `registro_comprobantes_completo.sql`, scripts numerados `01`–`08` y migración `migrar_desde_version_anterior.sql` |
| `herramientas/` | Compilación sin Maven y pruebas (`.sh` y `.ps1`); preparación del entorno de desarrollo en Windows |
| `distribucion/nativa/` | Instaladores con PostgreSQL embebido: lanzador `InicioNativo.java`, preparación, Inno Setup y firma (`firmar.sh`) |
| `Iniciar.cmd` | Abre el aplicativo desde esta carpeta, en Windows (con un PostgreSQL ya instalado) |
| `docs/` | Memoria descriptiva, manual de usuario, requisitos y verificación |

## Instaladores para Windows y Linux

Descargue el instalador x86-64 de [GitHub Releases](https://github.com/francospz-try/AppGDI_Dinosaurios/releases).
Cada instalador incluye PostgreSQL 17.12 y un runtime Java 21: no hace falta
instalar Java, PostgreSQL ni Docker, ni activar la virtualización, ni tener
permisos de administrador.

| Variante | Comportamiento |
|---|---|
| **Embebido** (recomendada) | AppGDI arranca su propio PostgreSQL y lo detiene al cerrar. |
| **Headless** | Arranca un PostgreSQL privado en segundo plano y lo deja activo al cerrar AppGDI; se detiene con el acceso directo «Detener PostgreSQL de AppGDI» o con `--detener`. |

**Requisitos.** Windows 10 u 11 de 64 bits, o Linux x86-64 con glibc y
escritorio gráfico. Espacio libre: unos 60 MB para el instalador, 190 MB para
el programa (Windows; 146 MB en Linux) y 300 MB para la base, que ocupa unos
70 MB recién cargada y crece con el registro de transacciones. Si falta
espacio, AppGDI lo indica con un mensaje antes de crear la base.

**Instalación.**
- Windows: ejecute el `.exe`. Se instala en `%LOCALAPPDATA%\Programs` y crea en el
  menú Inicio los accesos para abrir AppGDI, detener su servidor, respaldar y
  restaurar la base, y desinstalar.
- Linux: `bash AppGDI-Dinosaurios-Embebido-Linux-x64.run`. El mismo archivo vuelve a
  abrir la aplicación y acepta `--detener`, `--respaldar <archivo.zip>` y
  `--restaurar <archivo.zip>`.

**Aviso de SmartScreen.** Los instaladores todavía no tienen firma digital, así
que Windows muestra «Windows protegió su PC». Compruebe que el archivo viene de
la página de Releases (y, si quiere, su SHA-256 en `SHA256SUMS.txt`), pulse
**Más información** y luego **Ejecutar de todas formas**.

**Primera ejecución y siguientes.** La primera vez se crea un clúster PostgreSQL
privado, se carga `scripts/registro_comprobantes_completo.sql` y se generan dos
contraseñas aleatorias (la de `gdi_app` se puede cambiar en ese momento). Las
siguientes ejecuciones reutilizan los datos. El servidor escucha solo en
`127.0.0.1`, en un puerto libre elegido en cada arranque, así que no choca con
otro PostgreSQL de la máquina (por ejemplo, en el puerto 5432). Si AppGDI se
cierra de golpe, el siguiente arranque detiene el servidor que quedó activo.

**Dónde quedan los datos.** `%LOCALAPPDATA%\AppGDI-Dinosaurios\embebido` (o
`headless`) en Windows; `$XDG_DATA_HOME/AppGDI-Dinosaurios/…` en Linux, con
`~/.local/share` por defecto. Ahí están el clúster (`postgresql/`), `config.ini`
con las contraseñas y los registros. Desinstalar no borra esta carpeta.

**Respaldo y restauración.** Con «Respaldar la base de AppGDI» se guarda un `.zip`
con el clúster detenido y `config.ini` (unos 9 MB con los datos de
demostración). Contiene las contraseñas: guárdelo en un lugar seguro. «Restaurar
un respaldo de AppGDI» reemplaza los datos actuales, que se conservan en una
carpeta `antes-de-restaurar-<fecha>`. AppGDI debe estar cerrada en ambos casos.

**Firma.** `distribucion/nativa/firmar.sh` firma los `.exe` con
[jsign](https://ebourg.github.io/jsign/) cuando haya un certificado de firma de
código; el flujo de Releases lo usa si se configuran los secretos
`FIRMA_PFX_BASE64` y `FIRMA_CLAVE` (o un servicio en la nube con `FIRMA_TIPO` y
`FIRMA_ALMACEN`).

Las instalaciones de [la versión Docker v1.0.3](https://github.com/francospz-try/AppGDI_Dinosaurios/releases/tag/v1.0.3)
conservan su volumen; los instaladores nativos no lo importan.

## Inicio rápido

Requisitos: PostgreSQL 17 con `psql` y un JDK 21 completo (`java`, `javac`,
`jar`). Maven es opcional.

### ¿Base nueva o base existente?

| Situación | Qué ejecutar |
|---|---|
| Instalación nueva, o base de pruebas descartable | `scripts/registro_comprobantes_completo.sql` y luego `\password gdi_app` |
| Ya tiene una base de la versión anterior de este repositorio (hasta el commit `b8be9b7`, scripts `database/`) con datos que quiere conservar | Respaldo con `pg_dump` y luego `scripts/migrar_desde_version_anterior.sql` |

> ⚠️ **El script completo elimina la base `registro_comprobantes` si ya existe,
> con todos sus datos.** Nunca lo ejecute sobre una base en uso: para esa
> base, use la migración.

Base nueva:

```sh
psql -U postgres -d postgres -f scripts/registro_comprobantes_completo.sql
psql -U postgres -d registro_comprobantes -c '\password gdi_app'
```

Base existente de la versión anterior (conserva los datos y la contraseña de `gdi_app`):

```sh
pg_dump -U postgres -Fc -d registro_comprobantes -f antes_de_migrar.dump
psql -U postgres -d registro_comprobantes -f scripts/migrar_desde_version_anterior.sql
```

La migración es una sola transacción: si falla, la base no cambia. Convierte
`activo` en `eliminado`; guarda en el esquema `migracion` las bajas de series,
monedas y documentos y las versiones de los comprobantes, que el esquema
actual no tiene; retira el esquema `app` (vistas y procedimientos, sin
datos); crea las rutinas actuales y aplica los permisos. Comprueba que
ninguna tabla pierda filas y, si se ejecuta otra vez, se detiene sin cambiar
nada.

Después, en ambos casos:

```sh
bash herramientas/compilar.sh            # Windows: herramientas\compilar.ps1
cd aplicativo && java -jar target/registro-comprobantes.jar   # Windows: Iniciar.cmd
```

El aplicativo pide la contraseña de `gdi_app` al abrirse; no se guarda. El
uso de cada pantalla está en el [manual de usuario](docs/manual_de_usuario.pdf).

## Base de datos

| Script | Contenido |
|---|---|
| `01_creacion.sql` | Base, dominios, ocho tablas con borrado lógico (`eliminado`), índices |
| `02_insercion.sql` | Series, moneda, 5325 comprobantes generados y los tres analizados (clientes ficticios) |
| `03_consultas.sql` | Doce consultas (no forma parte del script completo) |
| `04_procedimientos.sql` | Funciones de consulta del aplicativo |
| `05_procedimientos.sql` | Procedimientos del CRUD y disparadores de importes y líneas |
| `06_reportes.sql` | Doce reportes con parámetros (diez con JOIN); registro de series y monedas |
| `07_datos_aplicativo.sql` | 303 comprobantes más, borrados y restauraciones, registrados con los procedimientos |
| `08_permisos.sql` | Rol `gdi_app`: retira sus permisos anteriores; solo lee las tablas y ejecuta las rutinas |
| `migrar_desde_version_anterior.sql` | Lleva una base existente de la versión anterior al esquema actual, sin perder datos (no forma parte del script completo) |

`registro_comprobantes_completo.sql` une los numerados en ese orden (salvo el 03) y
se regenera con `sh scripts/generar_completo.sh`. El aplicativo nunca ejecuta
los scripts por su cuenta.

## Pruebas

Siete clases de pruebas (99 casos) sobre una base **descartable** recién
creada con el script completo (o recién migrada); las pruebas modifican los datos:

```sh
COMPROBANTES_BD_DESCARTABLE=si bash herramientas/probar.sh <config gdi_app> <config administrador>
```

Los instaladores se prueban igual sobre su propia base embebida, en una carpeta
temporal: `--verificar <carpeta>` corre las siete clases y además comprueba que
la base esté en UTF8 y que un respaldo restaurado conserve los datos y los
límites de `gdi_app` (101 comprobaciones). El flujo de Releases lo ejecuta en
Linux y Windows con rutas que tienen espacios y tildes.

Detalle y resultados: [docs/verificacion.md](docs/verificacion.md).

## Decisiones

1. Se registra lo impreso: un importe que el comprobante no imprime es NULL,
   no cero, y un comprobante registrado no acredita un cobro.
2. Borrado lógico con la columna `eliminado` en cliente, producto o servicio
   y comprobante; los eliminados se pueden restaurar.
3. El aplicativo se conecta con `gdi_app`, que no puede escribir directo en
   las tablas: toda escritura pasa por un procedimiento, que se ejecuta con
   los permisos de su dueño.
4. La búsqueda no distingue mayúsculas ni tildes y no depende de la
   configuración regional con que se creó la instancia (`fn_clave_busqueda`).
5. No se publican en este repositorio datos personales, documentos de la
   empresa ni anexos del informe.
