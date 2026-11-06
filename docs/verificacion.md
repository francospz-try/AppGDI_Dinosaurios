# Verificación del avance al 70 %

Fecha: 5 de noviembre de 2026. Linux, OpenJDK/JDK 21.0.13, pgJDBC 42.7.7 y
PostgreSQL 17.11. La compilación final se hizo con javac mediante scripts/build.sh;
el script scripts/test.sh ejecutó todas las suites sobre una base descartable con
la carga original de 5328 comprobantes.

## Comprobaciones automatizadas

- 37 comprobaciones de conexión, consultas, clientes y doce reportes.
- Segunda aplicación de las migraciones sin errores ni pérdida de la carga original.
- 37 comprobaciones adicionales de CRUD de comprobantes y los cuatro catálogos auxiliares.
- Dos pruebas de integridad: no quitar la última línea, tampoco mediante borrados concurrentes.
- Swing: ordenación de cabecera heterogénea; editor conserva NULL y siete decimales;
  coma decimal y rechazo de exceso de precisión; conexión y carga real de listas.
- Duplicados y referencias inválidas revierten la transacción completa.
- Edición obsoleta rechazada; corrección de documento relacionado invalida las versiones anteriores.
- Baja lógica consultable en historial sin eliminar líneas o referencias.

Se observaron fallos antes de corregir la búsqueda con ceros impresos, la búsqueda
de CLIENTES VARIOS y la ordenación de valores de cabecera. Se verificaron después
con las mismas condiciones de reproducción.

## Ejecución gráfica real

Se abrió el aplicativo Java en el escritorio Linux y se operó con teclado y ratón:

1. Conectar a la instancia PostgreSQL de prueba con el rol gdi_app.
2. Registrar B001-90000001, total 118.00, una línea de detalle y TEST-GUI-70 como referencia.
3. Verificar su aparición en la lista y abrirlo para editar.
4. Cambiar observación a EDITADO GUI 70 y comprobar que persistió.
5. Comprobar que la referencia se conservó.
6. Dar de baja y consultar B001-90000001 con Incluir bajas: aparece inactivo.
7. Ejecutar el primer reporte desde la ventana: ocho filas de los datos de prueba.

El entorno inicial solo tenía Java sin librerías de escritorio y sin javac. Para
esta verificación se preparó un JDK con las librerías gráficas oficiales de Debian
en el entorno de pruebas; no se modificó el equipo Windows del usuario.

## Condiciones y límites

Las suites de CRUD usan identificadores reservados de prueba y **requieren restaurar
la base descartable antes de cada ejecución**. Dejan registros y bajas lógicas;
no son pruebas para ejecutar sobre una base de trabajo. Orden de las migraciones:
04 → 07 → 05 → 06. Los scripts 01/02 solo inicializan o recrean la carga de prueba.

No se ha ejecutado en el Windows del usuario ni se ha validado allí PowerShell,
el doble clic o las junctions de habilidades. El avance del 70 % corresponde al
alcance funcional probado aquí; queda la demostración en el equipo de entrega.
No se incluyen todavía manual/memoria Word/PDF ni el paquete final del 100 %.
