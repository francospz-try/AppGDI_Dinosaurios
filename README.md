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
| `Iniciar.cmd` | Abre el aplicativo desde esta carpeta, en Windows |
| `docs/` | Memoria descriptiva, manual de usuario, requisitos y verificación |

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
