-- Datos ficticios de desarrollo. Los IDs y jurisdicciones siguen docs/api-contract.md.
-- Control tecnico exclusivo de la semilla de desarrollo: se aplica una vez por base.
CREATE TABLE IF NOT EXISTS inicializaciones (
    identificador VARCHAR(80) NOT NULL PRIMARY KEY
);

INSERT INTO municipios (id, nombre, provincia)
SELECT 1, 'Quilmes', 'Buenos Aires' WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM municipios WHERE id = 1);

INSERT INTO barrios (id, nombre, municipio_id)
SELECT 1, 'Quilmes Centro', 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM barrios WHERE id = 1);
INSERT INTO barrios (id, nombre, municipio_id)
SELECT 2, 'Bernal', 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM barrios WHERE id = 2);
INSERT INTO barrios (id, nombre, municipio_id)
SELECT 3, 'Ezpeleta', 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM barrios WHERE id = 3);

INSERT INTO categorias (id, nombre, descripcion, sla_horas, prioridad_base)
SELECT 1, 'Luminaria rota', 'Farol apagado o dañado', 48, 'MEDIA' WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM categorias WHERE id = 1);
INSERT INTO categorias (id, nombre, descripcion, sla_horas, prioridad_base)
SELECT 2, 'Bache', 'Pozo en la calzada', 120, 'BAJA' WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM categorias WHERE id = 2);
INSERT INTO categorias (id, nombre, descripcion, sla_horas, prioridad_base)
SELECT 3, 'Residuos', 'Residuos en la vía pública', 72, 'MEDIA' WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM categorias WHERE id = 3);

INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 1, 'Alumbrado', 'alumbrado@example.org', true, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 1);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 2, 'Obras Públicas', 'obras@example.org', true, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 2);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 3, 'Higiene Urbana', 'higiene@example.org', true, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 3);
INSERT INTO areas_municipales (id, nombre, email_contacto, activa, municipio_id)
SELECT 4, 'Mantenimiento Vial', 'vial@example.org', true, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM areas_municipales WHERE id = 4);

INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 1, 'CIUDADANO', '30111222', 'Ana', 'Pérez', 'ana@example.org', NULL, true, NULL WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 1);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 2, 'CIUDADANO', '31222333', 'Bruno', 'Díaz', 'bruno@example.org', NULL, true, NULL WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 2);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 3, 'AGENTE_MUNICIPAL', '25333444', 'Carla', 'Gómez', 'carla@example.org', NULL, true, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 3);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 4, 'AGENTE_MUNICIPAL', '26444555', 'Diego', 'Sosa', 'diego@example.org', NULL, true, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 4);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 5, 'AGENTE_MUNICIPAL', '27555666', 'Fabián', 'Luna', 'fabian@example.org', NULL, true, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 5);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 6, 'AGENTE_MUNICIPAL', '28666777', 'Gabriela', 'Paz', 'gabriela@example.org', NULL, true, 4 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 6);
INSERT INTO usuarios (id, tipo, dni, nombre, apellido, email, telefono, activo, area_id)
SELECT 7, 'ADMINISTRADOR', '20555666', 'Elena', 'Ruiz', 'elena@example.org', NULL, true, NULL WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM usuarios WHERE id = 7);

INSERT INTO area_barrio (area_id, barrio_id)
SELECT 1, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_barrio WHERE area_id = 1 AND barrio_id = 1);
INSERT INTO area_barrio (area_id, barrio_id)
SELECT 1, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_barrio WHERE area_id = 1 AND barrio_id = 2);
INSERT INTO area_barrio (area_id, barrio_id)
SELECT 2, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_barrio WHERE area_id = 2 AND barrio_id = 1);
INSERT INTO area_barrio (area_id, barrio_id)
SELECT 2, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_barrio WHERE area_id = 2 AND barrio_id = 2);
INSERT INTO area_barrio (area_id, barrio_id)
SELECT 2, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_barrio WHERE area_id = 2 AND barrio_id = 3);
INSERT INTO area_barrio (area_id, barrio_id)
SELECT 3, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_barrio WHERE area_id = 3 AND barrio_id = 1);
INSERT INTO area_barrio (area_id, barrio_id)
SELECT 3, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_barrio WHERE area_id = 3 AND barrio_id = 2);
INSERT INTO area_barrio (area_id, barrio_id)
SELECT 4, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_barrio WHERE area_id = 4 AND barrio_id = 2);

INSERT INTO area_categoria (area_id, categoria_id)
SELECT 1, 1 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 1 AND categoria_id = 1);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 2, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 2 AND categoria_id = 2);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 3, 3 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 3 AND categoria_id = 3);
INSERT INTO area_categoria (area_id, categoria_id)
SELECT 4, 2 WHERE NOT EXISTS (SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1')
AND NOT EXISTS (SELECT 1 FROM area_categoria WHERE area_id = 4 AND categoria_id = 2);

INSERT INTO inicializaciones (identificador)
SELECT 'datos-dev-v1' WHERE NOT EXISTS (
    SELECT 1 FROM inicializaciones WHERE identificador = 'datos-dev-v1'
);

-- Login (PR 14): contraseña de desarrollo "demo1234" para los siete usuarios de la semilla, en BCrypt.
-- Va aparte del marcador datos-dev-v1 para completar también las bases creadas antes del login. Solo toca
-- los usuarios sin contraseña: no pisa una que se haya cambiado.
UPDATE usuarios SET hash_contrasena = '$2a$10$Fj7CGx6hDi2DyQSJv4Bat.Crnxp12nWpSIXthWYde6v/q.bYhjFjW'
WHERE hash_contrasena IS NULL AND id BETWEEN 1 AND 7;

-- Teléfono verificado (PR 15): Ana y Bruno ya lo tienen, así la demo funciona sin Telegram. Números ficticios.
-- Como las contraseñas, completa también las bases anteriores y no pisa un número verificado después.
UPDATE usuarios SET telefono_verificado = '+5491100000001', fecha_verificacion_telefono = '2026-01-01 00:00:00'
WHERE id = 1 AND tipo = 'CIUDADANO' AND telefono_verificado IS NULL;
UPDATE usuarios SET telefono_verificado = '+5491100000002', fecha_verificacion_telefono = '2026-01-01 00:00:00'
WHERE id = 2 AND tipo = 'CIUDADANO' AND telefono_verificado IS NULL;
