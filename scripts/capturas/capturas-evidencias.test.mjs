// Tests de las partes del script que no necesitan navegador. El recorrido completo se prueba corriéndolo contra
// la API simulada del frontend (npm run mock y npm run dev) con --app http://localhost:5173.
// Se corren con: node --test scripts/capturas/*.test.mjs
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { CAPTURAS, FOTO, configuracion, ejecutar, indice } from './capturas-evidencias.mjs'

test('las opciones salen de la línea de comandos o del entorno', () => {
  const porDefecto = configuracion([], {})
  assert.equal(porDefecto.app, 'http://localhost:5173')
  assert.equal(porDefecto.rabbitUsuario, 'reclamos')
  assert.match(porDefecto.salida, /docs[\\/]evidencias[\\/]sistema-real$/)
  const cambiada = configuracion(['--app', 'http://otra:8000/', '--ver'], { RABBITMQ_USER: 'admin' })
  assert.equal(cambiada.app, 'http://otra:8000')
  assert.equal(cambiada.ver, true)
  assert.equal(cambiada.rabbitUsuario, 'admin')
})

test('la foto de ejemplo existe y cada captura dice qué caso ilustra', () => {
  assert.ok(existsSync(FOTO))
  for (const [nombre, [caso, texto]] of Object.entries(CAPTURAS)) {
    assert.match(nombre, /^\d{2}-[a-z0-9-]+$/)
    assert.match(caso, /^CP\d{2}(, CP\d{2})*$/)
    assert.ok(texto.length > 10)
  }
})

test('el índice enlaza cada captura con su caso y lista lo que falló', () => {
  const texto = indice(
    [{ nombre: '01-ingreso', hora: new Date(2026, 10, 16, 10, 0, 0) }],
    [{ seccion: 'Mailpit', mensaje: 'no responde' }],
    { app: 'http://localhost:5173' },
    new Date(2026, 10, 16),
  )
  assert.match(texto, /\| \[01-ingreso\]\(01-ingreso\.png\) \| CP41 \|/)
  assert.match(texto, /- \*\*Mailpit:\*\* no responde/)
})

test('sin la interfaz levantada no abre el navegador y explica qué revisar', async () => {
  let abierto = false
  const chromium = { launch: async () => { abierto = true } }
  await assert.rejects(
    ejecutar({ ...configuracion([], {}), app: 'http://127.0.0.1:9' }, { chromium, registro: { log() {} } }),
    /no responde en http:\/\/127\.0\.0\.1:9/,
  )
  assert.equal(abierto, false)
})
