# Pruebas de verificacion manual

No son pruebas unitarias con JUnit (quedaron en el roadmap v2). Son dos
programas con `main` que se ejecutan contra la base de datos real y sirven
para comprobar de un vistazo que el sistema hace lo que dice.

## PruebaFlujos

Recorre autenticacion, RBAC, los veredictos de porteria, el flujo 4 completo
y la bitacora. Comprueba explicitamente el invariante del sistema: que la
ocupacion no se duplique al regularizar una salida olvidada.

## PruebaManipulacion

Reporta si la cadena de hash de la bitacora esta intacta. Para ver la deteccion
funcionando: ejecutarla, luego editar a mano cualquier fila de
`bitacora_auditoria` en MySQL, y volver a ejecutarla.

## Como ejecutarlas

    mvn -q compile
    mvn -q exec:java -Dexec.mainClass=PruebaFlujos -Dexec.classpathScope=compile

O mas simple, desde el IDE: boton derecho sobre el archivo, Run.
