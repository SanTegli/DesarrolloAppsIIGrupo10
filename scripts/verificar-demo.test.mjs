// Tests del script de verificación contra un sistema simulado: un servidor HTTP local que responde como
// el frontend, el backend, los servicios, RabbitMQ y Mailpit. Se corren con: node --test scripts/
import { after, before, describe, test } from 'node:test'
import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import {
  AVISO,
  FALLA,
  OK,
  RECLAMO_DE_PRUEBA,
  resumen,
  revisarArea,
  revisarPrioridad,
  verificarDemo,
} from './verificar-demo.mjs'

const NUMERO = 'REC-0A1B2C3D'

function escenarioNormal() {
  return {
    georef: {
      disponible: true,
      sugerencias: [
        { direccionNormalizada: 'BELGRANO 450, Bernal, Quilmes', latitud: -34.7086, longitud: -58.2797 },
      ],
    },
    origenPrioridad: 'IA',
    correos: [
      { Subject: `Reclamo ${NUMERO}: lo recibimos`, To: [{ Address: 'ana@example.org' }] },
      { Subject: `Reclamo ${NUMERO}: lo asignamos`, To: [{ Address: 'ana@example.org' }] },
    ],
    consumidores: 1,
    mensajesEnErrores: 0,
    altas: [],
  }
}

let escenario
let servidor
let base

function responder(res, estado, cuerpo, tipo = 'application/json') {
  res.writeHead(estado, { 'Content-Type': tipo })
  res.end(typeof cuerpo === 'string' ? cuerpo : JSON.stringify(cuerpo))
}

async function leer(req) {
  let texto = ''
  for await (const parte of req) texto += parte
  return texto
}

before(async () => {
  servidor = createServer(async (req, res) => {
    const url = new URL(req.url, 'http://local')
    const ruta = url.pathname
    if (ruta === '/frontend/') return responder(res, 200, '<div id="raiz"></div>', 'text/html')
    if (ruta === '/frontend/api/categorias') return responder(res, 200, [{ id: 1 }, { id: 2 }, { id: 3 }])
    if (ruta === '/backend/api/usuarios') return responder(res, 200, Array.from({ length: 7 }, (_, i) => ({ id: i })))
    if (ruta === '/backend/v3/api-docs') return responder(res, 200, { openapi: '3.1.0' })
    if (ruta === '/backend/api/ubicaciones/sugerencias') return responder(res, 200, escenario.georef)
    if (ruta === '/backend/api/reclamos' && req.method === 'POST') {
      const cuerpo = JSON.parse(await leer(req))
      escenario.altas.push({ cuerpo, usuario: req.headers['x-usuario-id'] })
      return responder(res, 201, {
        numero: NUMERO,
        estado: 'ASIGNADO',
        prioridad: 'MEDIA',
        origenPrioridad: escenario.origenPrioridad,
        motivoPrioridad: 'Luminaria apagada en la vía pública',
        asignacionPendiente: false,
        area: { id: 1, nombre: 'Alumbrado' },
        ubicacion: cuerpo.ubicacion
          ? { estado: 'CONFIRMADA', ...cuerpo.ubicacion, proveedor: 'georef' }
          : { estado: 'PENDIENTE' },
      })
    }
    if (ruta === '/cobertura/ws/cobertura.wsdl') {
      return responder(res, 200, '<wsdl:operation name="ConsultarCobertura"/>', 'text/xml')
    }
    if (ruta === '/prioridad/v3/api-docs') {
      return responder(res, 200, { paths: { '/api/prioridades/evaluar': { post: {} } } })
    }
    if (ruta.startsWith('/rabbit/api/')) {
      const esperado = 'Basic ' + Buffer.from('reclamos:reclamos_dev').toString('base64')
      if (req.headers.authorization !== esperado) return responder(res, 401, { error: 'not_authorised' })
      if (ruta.endsWith('/reclamos.eventos')) return responder(res, 200, { type: 'topic' })
      if (ruta.endsWith('/notificaciones.email')) return responder(res, 200, { consumers: escenario.consumidores })
      if (ruta.endsWith('/notificaciones.email.dlq')) {
        return responder(res, 200, { messages: escenario.mensajesEnErrores })
      }
    }
    if (ruta === '/mailpit/api/v1/info') return responder(res, 200, { Messages: escenario.correos.length })
    if (ruta === '/mailpit/api/v1/messages') return responder(res, 200, { messages: escenario.correos })
    return responder(res, 404, { error: ruta })
  })
  await new Promise((listo) => servidor.listen(0, '127.0.0.1', listo))
  base = `http://127.0.0.1:${servidor.address().port}`
})

after(() => servidor.close())

function configuracion(cambios = {}) {
  return {
    frontendUrl: `${base}/frontend`,
    backendUrl: `${base}/backend`,
    coberturaUrl: `${base}/cobertura`,
    prioridadUrl: `${base}/prioridad`,
    rabbitmqApiUrl: `${base}/rabbit`,
    rabbitmqUsuario: 'reclamos',
    rabbitmqClave: 'reclamos_dev',
    mailpitUrl: `${base}/mailpit`,
    esperaCorreoMs: 300,
    intervaloMs: 50,
    tiempoLimiteMs: 2000,
    ...cambios,
  }
}

const estadoDe = (resultados, paso) => resultados.find((resultado) => resultado.paso === paso)?.estado

describe('verificarDemo', () => {
  test('con todo andando, todos los pasos dan bien y el alta lleva la ubicación elegida', async () => {
    escenario = escenarioNormal()
    const resultados = await verificarDemo(configuracion())

    assert.deepEqual(resumen(resultados), { ok: resultados.length, avisos: 0, fallas: 0 })
    assert.equal(escenario.altas.length, 1)
    const [alta] = escenario.altas
    assert.equal(alta.usuario, String(RECLAMO_DE_PRUEBA.usuarioId))
    assert.equal(alta.cuerpo.ubicacion.direccionNormalizada, 'BELGRANO 450, Bernal, Quilmes')
    assert.match(resultados.find((r) => r.paso === 'Correo por RabbitMQ').detalle, /lo recibimos/)
  })

  test('sin Georef el alta sigue sin ubicación y queda como aviso, no como falla', async () => {
    escenario = { ...escenarioNormal(), georef: { disponible: false, sugerencias: [] } }
    const resultados = await verificarDemo(configuracion())

    assert.equal(estadoDe(resultados, 'Georef (API externa)'), AVISO)
    assert.equal(estadoDe(resultados, 'Ubicación'), AVISO)
    assert.equal(escenario.altas[0].cuerpo.ubicacion, undefined)
    assert.equal(resumen(resultados).fallas, 0)
  })

  test('si la prioridad salió del respaldo, el paso de la IA falla y explica qué revisar', async () => {
    escenario = { ...escenarioNormal(), origenPrioridad: 'RESPALDO' }
    const resultados = await verificarDemo(configuracion())

    const ia = resultados.find((r) => r.paso === 'Prioridad con IA')
    assert.equal(ia.estado, FALLA)
    assert.match(ia.detalle, /GEMINI_API_KEY/)
  })

  test('si el correo no llega a tiempo, falla el paso de la mensajería', async () => {
    escenario = { ...escenarioNormal(), correos: [] }
    const resultados = await verificarDemo(configuracion())

    assert.equal(estadoDe(resultados, 'Correo por RabbitMQ'), FALLA)
  })

  test('sin consumidor en la cola o con mensajes en la de errores, lo señala', async () => {
    escenario = { ...escenarioNormal(), consumidores: 0, mensajesEnErrores: 2 }
    const resultados = await verificarDemo(configuracion())

    assert.equal(estadoDe(resultados, 'RabbitMQ'), FALLA)
    assert.equal(estadoDe(resultados, 'Cola de errores'), AVISO)
  })

  test('con credenciales de RabbitMQ equivocadas, falla ese paso con el código HTTP', async () => {
    escenario = escenarioNormal()
    const resultados = await verificarDemo(configuracion({ rabbitmqClave: 'otra' }))

    const rabbit = resultados.find((r) => r.paso === 'RabbitMQ')
    assert.equal(rabbit.estado, FALLA)
    assert.match(rabbit.detalle, /401/)
  })

  test('con el backend apagado no se corta: marca las fallas y no intenta revisar el reclamo', async () => {
    escenario = escenarioNormal()
    const resultados = await verificarDemo(configuracion({ backendUrl: 'http://127.0.0.1:9' }))

    assert.equal(estadoDe(resultados, 'Backend (REST 1)'), FALLA)
    assert.equal(estadoDe(resultados, 'Alta del reclamo'), FALLA)
    assert.equal(estadoDe(resultados, 'Revisión del reclamo'), FALLA)
    assert.match(resultados.find((r) => r.paso === 'Backend (REST 1)').detalle, /no responde/)
  })
})

describe('revisiones del reclamo', () => {
  test('la prioridad de la categoría cuenta como respuesta de la IA', () => {
    assert.equal(revisarPrioridad({ origenPrioridad: 'CATEGORIA', prioridad: 'MEDIA' })[0], OK)
    assert.equal(revisarPrioridad({ origenPrioridad: 'PALABRAS_CLAVE', prioridad: 'ALTA' })[0], FALLA)
  })

  test('una asignación pendiente es una falla del SOAP', () => {
    const [estado, detalle] = revisarArea({ asignacionPendiente: true, estado: 'INGRESADO', area: null })
    assert.equal(estado, FALLA)
    assert.match(detalle, /cobertura/)
  })
})
