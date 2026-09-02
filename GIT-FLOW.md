# Git Flow — comandos listos para ejecutar

> Hazlo **hoy**, aunque el proyecto no este terminado. El grafo de Git es lo
> unico de este proyecto que no se puede recuperar despues: si haces un solo
> commit gigante la noche antes, se nota y no hay forma de arreglarlo.

---

## Antes de empezar

Comprueba que `config.properties` **no** vaya al repositorio. Ya esta en
`.gitignore`, pero verificalo: son tus credenciales de base de datos.

```bash
cd sica
cat .gitignore | grep config.properties
```

---

## 1. Inicializar con las dos ramas base

```bash
git init
git branch -M main

git add .gitignore README.md pom.xml
git commit -m "chore: estructura inicial del proyecto Maven"

git checkout -b develop
```

`main` es produccion y `develop` integracion. Nunca se trabaja directamente
sobre ninguna de las dos.

---

## 2. Base de datos

```bash
git checkout -b feature/base-de-datos

git add src/main/resources/db/schema.sql
git commit -m "feat(db): esquema con RBAC, estados normalizados y bitacora encadenada"

git add src/main/resources/db/data.sql
git commit -m "feat(db): datos semilla disenados para demostrar los cuatro flujos"

git checkout develop
git merge --no-ff feature/base-de-datos -m "merge: modelo de datos"
```

---

## 3. Nucleo compartido y seguridad

```bash
git checkout -b feature/nucleo-y-seguridad

git add src/main/java/com/zonaacme/sica/shared/seguridad/
git commit -m "feat(seguridad): hash de claves con PBKDF2-HMAC-SHA256"

git add src/main/java/com/zonaacme/sica/shared/dominio/ \
        src/main/java/com/zonaacme/sica/shared/aplicacion/
git commit -m "feat(core): CasoDeUso generico y decoradores de seguridad y auditoria"

git add src/main/java/com/zonaacme/sica/shared/auditoria/ \
        src/main/java/com/zonaacme/sica/shared/infraestructura/
git commit -m "feat(auditoria): bitacora encadenada por hash SHA-256 verificable"

git add src/main/java/com/zonaacme/sica/shared/eventos/
git commit -m "feat(eventos): bus en memoria para notificacion en tiempo real"

git add src/main/java/com/zonaacme/sica/usuarios/
git commit -m "feat(usuarios): autenticacion y carga de permisos desde base de datos"

git checkout develop
git merge --no-ff feature/nucleo-y-seguridad -m "merge: nucleo compartido y seguridad"
```

---

## 4. Control de acceso

```bash
git checkout -b feature/control-de-acceso

git add src/main/java/com/zonaacme/sica/acceso/dominio/
git commit -m "feat(acceso): maquina de estados de la visita con patron State"

git add src/main/java/com/zonaacme/sica/acceso/aplicacion/
git commit -m "feat(acceso): casos de uso de los cuatro flujos de porteria"

git add src/main/java/com/zonaacme/sica/acceso/infraestructura/
git commit -m "feat(acceso): adaptadores JDBC con FOR UPDATE para el invariante de ocupacion"

git checkout develop
git merge --no-ff feature/control-de-acceso -m "merge: control de acceso"
```

---

## 5. Interfaz

```bash
git checkout -b feature/interfaz

git add src/main/resources/css/ src/main/java/com/zonaacme/sica/ui/Componentes.java
git commit -m "style(ui): tema visual de consola de puesto de control"

git add src/main/java/com/zonaacme/sica/ui/VentanaLogin.java \
        src/main/java/com/zonaacme/sica/arranque/
git commit -m "feat(ui): login y enrutamiento por permiso, no por nombre de rol"

git add src/main/java/com/zonaacme/sica/ui/PanelGuarda.java
git commit -m "feat(ui): panel de porteria con franja de veredicto y tablero de ocupacion"

git add src/main/java/com/zonaacme/sica/ui/PanelFuncionario.java
git commit -m "feat(ui): bandeja de autorizaciones suscrita al bus de eventos"

git checkout develop
git merge --no-ff feature/interfaz -m "merge: interfaz de guarda y funcionario"
```

---

## 6. Migracion del modelo de datos ← **el commit que mas te sirve**

Este merece rama propia y un mensaje que explique lo que demuestra. Es la
evidencia de que la arquitectura hexagonal sirvio para algo real.

```bash
git checkout -b refactor/modelo-de-datos-del-docente

git add src/main/resources/db/
git commit -m "refactor(db): adopta el modelo de datos entregado por el docente

Se conservan sus nombres de tablas, columnas y relaciones, incluida la
normalizacion de estados en tablas de consulta. Las columnas anadidas van
marcadas con [+] y su justificacion: sin tipo_visita, empresa_destino_id y
motivo los flujos 2 y 3 no son implementables, y sin usuario_intento se
pierde el dato del login fallido."

git add src/main/java/com/zonaacme/sica/shared/infraestructura/CatalogoEstados.java
git commit -m "feat(infra): traductor entre los enums del dominio y las tablas de estados"

git add src/main/java/com/zonaacme/sica/
git commit -m "refactor(infra): migra los adaptadores JDBC al nuevo modelo

El dominio y la capa de aplicacion no cambian ni una linea. Las reglas de
acceso, la maquina de estados, los decoradores y los cuatro casos de uso
siguen compilando y pasando sus pruebas sin modificacion.

Es la propiedad que se buscaba al elegir arquitectura hexagonal."

git checkout develop
git merge --no-ff refactor/modelo-de-datos-del-docente -m "merge: migracion al modelo del docente"
```

**En la sustentacion, abre este merge.** Cuando te pidan demostrar que la
arquitectura sirve, muestra que solo cambio `infraestructura`.

---

## 7. Incidentes y reportes

```bash
git checkout -b feature/incidentes-y-reportes

git add src/main/java/com/zonaacme/sica/incidentes/
git commit -m "feat(incidentes): registro y cierre con sugerencia de bloqueo por gravedad"

git add src/main/java/com/zonaacme/sica/reportes/
git commit -m "feat(reportes): agregaciones con Stream API y exportacion a CSV

Seis cortes distintos sobre el mismo conjunto de filas. Resolverlos con
GROUP BY serian seis consultas sobre los mismos datos."

git add src/main/java/com/zonaacme/sica/ui/PanelSupervisor.java \
        src/main/java/com/zonaacme/sica/ui/VentanaLogin.java \
        src/main/java/com/zonaacme/sica/arranque/
git commit -m "feat(ui): panel de supervision con reportes, incidentes y auditoria"

git checkout develop
git merge --no-ff feature/incidentes-y-reportes -m "merge: incidentes y reportes"
```

---

## 8. Documentacion y pruebas

```bash
git checkout -b docs/documentacion

git add pruebas/
git commit -m "test: programas de verificacion de los flujos contra base de datos real"

git add README.md SUSTENTACION.md GIT-FLOW.md
git commit -m "docs: README con modelo ER, decisiones de diseno y guia de instalacion"

git checkout develop
git merge --no-ff docs/documentacion -m "merge: documentacion"
```

---

## 9. Release

```bash
git checkout -b release/1.0
git commit --allow-empty -m "chore(release): preparacion de la version 1.0"

git checkout main
git merge --no-ff release/1.0 -m "release: version 1.0"
git tag -a v1.0.0 -m "SICA 1.0 — Sistema Integrado de Control de Acceso"

git checkout develop
git merge --no-ff release/1.0 -m "merge: sincroniza develop con el release"
```

---

## 10. Subir a GitHub

Crea el repositorio en GitHub **en privado**, sin README ni .gitignore (ya los
tienes).

```bash
git remote add origin https://github.com/TU_USUARIO/sica.git
git push -u origin main
git push origin develop
git push origin --tags
```

Y agrega a tu trainer como colaborador: **Settings → Collaborators → Add people**.
Es un entregable explicito; si se te olvida, el repositorio privado es
invisible para quien te califica.

---

## Comprobar que el grafo quedo bien

```bash
git log --graph --oneline --all --decorate
```

Debes ver las ramas abriendose y cerrandose. Si ves una linea recta, los merges
se hicieron sin `--no-ff` y el grafo perdio la forma del flujo.

---

## Sobre Conventional Commits

El formato es `tipo(alcance): descripcion en imperativo`.

| Tipo | Cuando |
|---|---|
| `feat` | funcionalidad nueva |
| `fix` | correccion de un error |
| `refactor` | cambio interno sin alterar el comportamiento |
| `docs` | documentacion |
| `test` | pruebas |
| `style` | formato o presentacion, sin logica |
| `chore` | tareas de mantenimiento |

Dos reglas que se incumplen a menudo: la descripcion va en **imperativo**
("agrega", no "agregado"), y **sin punto final**.
