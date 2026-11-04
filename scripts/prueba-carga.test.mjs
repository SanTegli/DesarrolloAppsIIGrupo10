// Tests de la prueba de carga contra un backend simulado: un servidor HTTP local que responde como el
// backend (login con CSRF y sesión, y los endpoints de lectura). Se corren con: node --test scripts/*.test.mjs
import { after, before, describe, test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { configuracion, ejecutarCarga, informe, percentil } from './prueba-carga.mjs'

const TOKEN = 'token-csrf'
const SESION = 'sesion-carga'
let servidor
let base
let escenario

function responder(res, estado, cuerpo, cookies = []) {
  res.writeHead(estado, { 'Content-Type': 'application/json', ...(cookies.length ? { 'Set-Cookie': cookies } : {}) })
  res.end(JSON.stringify(cuerpo))
}

before(async () => {
  servidor = createServer((req, res) => {
    const ruta = new URL(req.url, 'http://local').pathname
    const conSesion = (req.headers.cookie ?? '').includes(`JSESSIONID=${SESION}`)
    escenario.pedidos.push(ruta)
    if (ruta === '/api/categorias') return responder(res, 200, [], [`XSRF-TOKEN=${TOKEN}; Path=/`])
    if (ruta === '/api/auth/login') {
      if (req.headers['x-xsrf-token'] !== TOKEN) return responder(res, 403, { codigo: 'CSRF_INVALIDO' })
      return escenario.loginValido
        ? responder(res, 200, { id: 1 }, [`JSESSIONID=${SESION}; Path=/; HttpOnly`])
        : responder(res, 401, { codigo: 'CREDENCIALES_INVALIDAS' })
    }
    if (ruta === '/api/dashboard/publico') return responder(res, 200, { creadosEnPeriodo: 1 })
    if (!conSesion) return responder(res, 401, { codigo: 'NO_AUTENTICADO' })
    if (ruta === '/api/reclamos') return responder(res, 200, [{ numero: 'REC-0A1B2C3D' }])
    if (ruta === '/api/notificaciones') {
      return escenario.notificacionesFallan ? responder(res, 500, { codigo: 'ERROR_INTERNO' }) : responder(res, 200, [])
    }
    if (ruta === '/api/reclamos/REC-0A1B2C3D') return responder(res, 200, { numero: 'REC-0A1B2C3D' })
    return responder(res, 404, { codigo: 'RECURSO_NO_ENCONTRADO' })
  })
  await new Promise((listo) => servidor.listen(0, '127.0.0.1', listo))
  base = `http://127.0.0.1:${servidor.address().port}`
})

after(() => servidor.close())

const config = (cambios = {}) => ({
  ...configuracion([], {}),
  backendUrl: base,
  usuarios: 4,
  duracionSegundos: 0.4,
  ...cambios,
})

describe('ejecutarCarga', () => {
  test('con el backend sano recorre los cinco endpoints, sin errores, y cumple la meta', async () => {
    escenario = { pedidos: [], loginValido: true, notificacionesFallan: false }
    const resultado = await ejecutarCarga(config())

    assert.equal(resultado.cumple, true)
    assert.deepEqual(
      resultado.filas.map((fila) => fila.nombre),
      ['GET /api/categorias', 'GET /api/dashboard/publico', 'GET /api/reclamos', 'GET /api/notificaciones', 'GET /api/reclamos/{numero}'],
    )
    assert.ok(resultado.filas.every((fila) => fila.pedidos > 0 && fila.errores === 0))
    assert.equal(resultado.total.pedidos, resultado.filas.reduce((suma, fila) => suma + fila.pedidos, 0))
    assert.ok(!escenario.pedidos.some((ruta) => ruta.endsWith('/apoyo')), 'solo lee')
  })

  test('si un endpoint responde con error, lo cuenta por causa y no cumple', async () => {
    escenario = { pedidos: [], loginValido: true, notificacionesFallan: true }
    const resultado = await ejecutarCarga(config())

    const notificaciones = resultado.filas.find((fila) => fila.nombre === 'GET /api/notificaciones')
    assert.ok(notificaciones.errores > 0)
    assert.equal(notificaciones.detalleErrores[500], notificaciones.errores)
    assert.equal(resultado.cumple, false)
    assert.match(informe(resultado), /\*\*no cumple\*\*/)
    assert.match(informe(resultado), /500 × \d+/)
  })

  test('si el percentil 95 supera el máximo, no cumple aunque no haya errores', async () => {
    escenario = { pedidos: [], loginValido: true, notificacionesFallan: false }
    const resultado = await ejecutarCarga(config({ p95MaximoMs: 0.0001 }))

    assert.equal(resultado.total.errores, 0)
    assert.equal(resultado.cumple, false)
  })

  test('sin login válido no empieza y explica qué revisar', async () => {
    escenario = { pedidos: [], loginValido: false, notificacionesFallan: false }

    await assert.rejects(ejecutarCarga(config()), /login de ana@example.org respondió 401/)
  })
})

describe('auxiliares', () => {
  test('percentil por rango más cercano', () => {
    assert.equal(percentil([], 0.95), 0)
    assert.equal(percentil([5], 0.95), 5)
    assert.equal(percentil([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 0.5), 5)
    assert.equal(percentil([10, 1, 9, 2, 8, 3, 7, 4, 6, 5], 0.95), 10)
  })

  test('las opciones salen de la línea de comandos o del entorno', () => {
    const desdeArgumentos = configuracion(['--usuarios', '50', '--duracion', '60', '--url', 'http://otro:9090/', '--salida', 'x.md'], {})
    assert.equal(desdeArgumentos.usuarios, 50)
    assert.equal(desdeArgumentos.duracionSegundos, 60)
    assert.equal(desdeArgumentos.backendUrl, 'http://otro:9090')
    assert.equal(desdeArgumentos.salida, 'x.md')
    const desdeEntorno = configuracion([], { BACKEND_URL: 'http://backend:8080', CARGA_USUARIOS: '5', CARGA_P95_MAXIMO_MS: '1500' })
    assert.equal(desdeEntorno.backendUrl, 'http://backend:8080')
    assert.equal(desdeEntorno.usuarios, 5)
    assert.equal(desdeEntorno.p95MaximoMs, 1500)
    assert.equal(configuracion([], {}).usuarios, 20)
  })
})
