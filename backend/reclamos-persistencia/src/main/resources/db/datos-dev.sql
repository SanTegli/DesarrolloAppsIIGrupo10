-- Datos ficticios de desarrollo. Los IDs y jurisdicciones siguen docs/api-contract.md.
-- PR 25: municipios en vez de barrios. Cada municipio tiene sus propias áreas; Avellaneda no tiene Alumbrado,
-- así una luminaria en Avellaneda queda sin área y se asigna a mano. Es una semilla nueva (datos-dev-v2): una
-- base creada antes del PR 25 hay que borrarla una vez (docker compose down -v). Las categorías del PR 26 van
-- en un bloque aparte, con su propia marca, y no hace falta borrar nada.
-- Control tecnico exclusivo de la semilla de desarrollo: se aplica una vez por base.
CREATE TABLE IF NOT EXISTS inicializaciones (
    identificador VARCHAR(80) NOT NULL PRIMARY KEY
);

INSERT INTO municipios (id, nombre, provincia)
SELECT 1, 'Quilmes', 'Buenos Aires' WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM municipios WHERE id = 1);
INSERT INTO municipios (id, nombre, provincia)
SELECT 2, 'Avellaneda', 'Buenos Aires' WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM municipios WHERE id = 2);
INSERT INTO municipios (id, nombre, provincia)
SELECT 3, 'Berazategui', 'Buenos Aires' WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM municipios WHERE id = 3);

INSERT INTO categorias (id, nombre, descripcion, sla_horas, prioridad_base, otra)
SELECT 1, 'Luminaria rota', 'Farol apagado o dañado', 48, 'MEDIA', false WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM categorias WHERE id = 1);
INSERT INTO categorias (id, nombre, descripcion, sla_horas, prioridad_base, otra)
SELECT 2, 'Bache', 'Pozo en la calzada', 120, 'BAJA', false WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM categorias WHERE id = 2);
INSERT INTO categorias (id, nombre, descripcion, sla_horas, prioridad_base, otra)
SELECT 3, 'Residuos', 'Residuos en la vía pública', 72, 'MEDIA', false WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM categorias WHERE id = 3);

INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 1, 'Alumbrado', 'alumbrado@example.org', true, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 1);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 2, 'Obras Públicas', 'obras@example.org', true, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 2);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 3, 'Higiene Urbana', 'higiene@example.org', true, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 3);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 4, 'Mantenimiento Vial', 'vial@example.org', true, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 4);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 5, 'Obras Públicas', 'obras.avellaneda@example.org', true, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 5);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 6, 'Higiene Urbana', 'higiene.avellaneda@example.org', true, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 6);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 7, 'Alumbrado', 'alumbrado.berazategui@example.org', true, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 7);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 8, 'Obras Públicas', 'obras.berazategui@example.org', true, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 8);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 9, 'Higiene Urbana', 'higiene.berazategui@example.org', true, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 9);

INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 1, 'CIUDADANO', '30111222', 'Ana', 'Pérez', 'ana@example.org', NULL, true, NULL WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 1);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 2, 'CIUDADANO', '31222333', 'Bruno', 'Díaz', 'bruno@example.org', NULL, true, NULL WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 2);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 3, 'AGENTE_MUNICIPAL', '25333444', 'Carla', 'Gómez', 'carla@example.org', NULL, true, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 3);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 4, 'AGENTE_MUNICIPAL', '26444555', 'Diego', 'Sosa', 'diego@example.org', NULL, true, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 4);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 5, 'AGENTE_MUNICIPAL', '27555666', 'Fabián', 'Luna', 'fabian@example.org', NULL, true, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 5);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 6, 'AGENTE_MUNICIPAL', '28666777', 'Gabriela', 'Paz', 'gabriela@example.org', NULL, true, 4 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 6);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 7, 'ADMINISTRADOR', '20555666', 'Elena', 'Ruiz', 'elena@example.org', NULL, true, NULL WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 7);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 8, 'AGENTE_MUNICIPAL', '29777888', 'Hugo', 'Medina', 'hugo@example.org', NULL, true, 5 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 8);

INSERT INTO area_categoria (area_id, categoria_id)
SELECT 1, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 1 AND categoria_id = 1);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 2, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 2 AND categoria_id = 2);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 3, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 3 AND categoria_id = 3);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 4, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 4 AND categoria_id = 2);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 5, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 5 AND categoria_id = 2);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 6, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 6 AND categoria_id = 3);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 7, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 7 AND categoria_id = 1);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 8, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 8 AND categoria_id = 2);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 9, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 9 AND categoria_id = 3);

INSERT INTO inicializaciones (identificador)
SELECT 'datos-dev-v2' WHERE NOT EXISTS (
    SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v2'
);

-- PR 26: cinco categorías nuevas, con su propia marca para que una base de antes del PR 26 las reciba sin
-- borrarse. "Otro problema" (8) no tiene áreas: el reclamo espera que un administrador lo recategorice con la
-- sugerencia de la IA. Avellaneda tampoco tiene semáforos (no tiene Alumbrado).
INSERT INTO categorias (id, nombre, descripcion, sla_horas, prioridad_base, otra)
SELECT 4, 'Semáforo averiado', 'Semáforo apagado, trabado o con una luz quemada', 24, 'ALTA', false WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM categorias WHERE id = 4);
INSERT INTO categorias (id, nombre, descripcion, sla_horas, prioridad_base, otra)
SELECT 5, 'Árbol caído o con riesgo', 'Árbol o rama caída, o que amenaza con caerse', 48, 'ALTA', false WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM categorias WHERE id = 5);
INSERT INTO categorias (id, nombre, descripcion, sla_horas, prioridad_base, otra)
SELECT 6, 'Pérdida de agua', 'Agua que sale de la vereda o la calzada', 48, 'ALTA', false WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM categorias WHERE id = 6);
INSERT INTO categorias (id, nombre, descripcion, sla_horas, prioridad_base, otra)
SELECT 7, 'Vereda rota', 'Baldosas sueltas, levantadas o faltantes', 168, 'BAJA', false WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM categorias WHERE id = 7);
INSERT INTO categorias (id, nombre, descripcion, sla_horas, prioridad_base, otra)
SELECT 8, 'Otro problema', 'Ninguna categoría corresponde: el vecino escribe qué tipo de problema es', 72, 'MEDIA', true WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM categorias WHERE id = 8);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 1, 4 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 1 AND categoria_id = 4);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 7, 4 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 7 AND categoria_id = 4);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 3, 5 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 3 AND categoria_id = 5);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 6, 5 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 6 AND categoria_id = 5);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 9, 5 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 9 AND categoria_id = 5);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 2, 6 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 2 AND categoria_id = 6);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 5, 6 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 5 AND categoria_id = 6);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 8, 6 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 8 AND categoria_id = 6);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 2, 7 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 2 AND categoria_id = 7);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 4, 7 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 4 AND categoria_id = 7);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 5, 7 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 5 AND categoria_id = 7);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 8, 7 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 8 AND categoria_id = 7);

INSERT INTO inicializaciones (identificador)
SELECT 'categorias-pr26' WHERE NOT EXISTS (
    SELECT 1 FROM inicializaciones WHERE identificador = 'categorias-pr26'
);

-- Jurisdicciones adicionales de AMBA. La marca permite ampliar instalaciones existentes sin borrar datos.
INSERT INTO municipios (id, nombre, provincia)
SELECT 4, 'Comuna 13', 'Ciudad Autónoma de Buenos Aires' WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM municipios WHERE id = 4);
INSERT INTO municipios (id, nombre, provincia)
SELECT 5, 'Comuna 14', 'Ciudad Autónoma de Buenos Aires' WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM municipios WHERE id = 5);
INSERT INTO municipios (id, nombre, provincia)
SELECT 6, 'San Isidro', 'Buenos Aires' WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM municipios WHERE id = 6);

INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 10, 'Alumbrado', 'alumbrado.comuna13@example.org', true, 4 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 10);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 11, 'Obras Públicas', 'obras.comuna13@example.org', true, 4 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 11);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 12, 'Higiene Urbana', 'higiene.comuna13@example.org', true, 4 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 12);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 13, 'Alumbrado', 'alumbrado.comuna14@example.org', true, 5 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 13);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 14, 'Obras Públicas', 'obras.comuna14@example.org', true, 5 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 14);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 15, 'Higiene Urbana', 'higiene.comuna14@example.org', true, 5 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 15);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 16, 'Alumbrado', 'alumbrado.sanisidro@example.org', true, 6 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 16);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 17, 'Obras Públicas', 'obras.sanisidro@example.org', true, 6 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 17);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 18, 'Higiene Urbana', 'higiene.sanisidro@example.org', true, 6 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 18);

INSERT INTO area_categoria (area_id, categoria_id)
SELECT 10, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 10 AND categoria_id = 1);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 10, 4 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 10 AND categoria_id = 4);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 11, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 11 AND categoria_id = 2);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 11, 6 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 11 AND categoria_id = 6);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 11, 7 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 11 AND categoria_id = 7);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 12, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 12 AND categoria_id = 3);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 12, 5 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 12 AND categoria_id = 5);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 13, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 13 AND categoria_id = 1);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 13, 4 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 13 AND categoria_id = 4);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 14, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 14 AND categoria_id = 2);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 14, 6 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 14 AND categoria_id = 6);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 14, 7 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 14 AND categoria_id = 7);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 15, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 15 AND categoria_id = 3);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 15, 5 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 15 AND categoria_id = 5);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 16, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 16 AND categoria_id = 1);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 16, 4 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 16 AND categoria_id = 4);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 17, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 17 AND categoria_id = 2);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 17, 6 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 17 AND categoria_id = 6);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 17, 7 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 17 AND categoria_id = 7);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 18, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 18 AND categoria_id = 3);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 18, 5 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 18 AND categoria_id = 5);

INSERT INTO inicializaciones (identificador)
SELECT 'jurisdicciones-amba' WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'jurisdicciones-amba');

-- Login (PR 14): contraseña de desarrollo "demo1234" para los ocho usuarios de la semilla, en BCrypt. Solo toca
-- los usuarios sin contraseña: no pisa una que se haya cambiado.
UPDATE usuarios SET hash_contrasena = '$2a$10$Fj7CGx6hDi2DyQSJv4Bat.Crnxp12nWpSIXthWYde6v/q.bYhjFjW'
WHERE hash_contrasena IS NULL AND id BETWEEN 1 AND 8;

-- Teléfono verificado (PR 15): Ana y Bruno ya lo tienen, así la demo funciona sin Telegram. Números ficticios.
-- No pisa un número verificado después.
UPDATE usuarios SET telefono_verificado = '+5491100000001', fecha_verificacion_telefono = '2026-01-01 00:00:00'
WHERE id = 1 AND tipo = 'CIUDADANO' AND telefono_verificado IS NULL;
UPDATE usuarios SET telefono_verificado = '+5491100000002', fecha_verificacion_telefono = '2026-01-01 00:00:00'
WHERE id = 2 AND tipo = 'CIUDADANO' AND telefono_verificado IS NULL;

-- PR 24: el origen de la prioridad suma PENDIENTE_IA. En MySQL, Hibernate crea los enums como ENUM(...) y
-- ddl-auto update no agrega valores a una columna que ya existe: como texto entra cualquier origen sin migrar.
-- Repetirlo en cada arranque no cambia nada.
ALTER TABLE reclamos MODIFY COLUMN origen_prioridad VARCHAR(16) NULL;
