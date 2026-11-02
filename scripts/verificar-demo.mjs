// Recorre la demo de punta a punta contra el sistema levantado con Docker Compose y dice qué anda y qué no.
//
// Con Docker, sin instalar nada en la máquina:
//   docker compose run --rm verificar-demo
// Con Node 20 o posterior, desde la raíz del repositorio:
//   node scripts/verificar-demo.mjs
//
// Crea un reclamo real como Ana Pérez: el paso que lo crea deja un reclamo más en la base.
// Sale con código 1 si algún paso falla, para que el CI lo marque en rojo.
// Solo usa módulos de Node: no necesita npm install.

const VALORES_POR_DEFECTO = {
  frontendUrl: 'http://localhost:5173',
  backendUrl: 'http://localhost:8080',
  coberturaUrl: 'http://localhost:8081',
  prioridadUrl: 'http://localhost:8082',
  rabbitmqApiUrl: 'http://localhost:15672',
  rabbitmqUsuario: 'reclamos',
  rabbitmqClave: 'reclamos_dev',
  mailpitUrl: 'http://localhost:8025',
  esperaCorreoMs: 30000,
  intervaloMs: 1000,
  tiempoLimiteMs: 10000,
}

// Datos de la semilla de desarrollo (README_INSTRUCTIVO, sección 9).
export const RECLAMO_DE_PRUEBA = {
  usuarioId: 1,
  correoCiudadano: 'ana@example.org',
  categoriaId: 1, // Luminaria rota, prioridad base MEDIA
  barrioId: 2, // Bernal: lo cubre Alumbrado
  direccion: 'Belgrano 450',
  descripcion:
    'Verificación de la demo: el farol de la esquina está apagado hace tres noches y la cuadra queda a oscuras.',
}

const COLA_AVISOS = 'notificaciones.email'
const COLA_ERRORES = 'notificaciones.email.dlq'
const EXCHANGE = 'reclamos.eventos'

export const OK = 'OK'
export const AVISO = 'AVISO'
export const FALLA = 'FALLA'

export function configuracionDesdeEntorno(entorno = process.env) {
  const numero = (nombre, porDefecto) => {
    const valor = Number(entorno[nombre])
    return Number.isFinite(valor) && valor > 0 ? valor : porDefecto
  }
  return {
    frontendUrl: entorno.FRONTEND_URL || VALORES_POR_DEFECTO.frontendUrl,
    backendUrl: entorno.BACKEND_URL || VALORES_POR_DEFECTO.backendUrl,
    coberturaUrl: entorno.COBERTURA_URL || VALORES_POR_DEFECTO.coberturaUrl,
    prioridadUrl: entorno.PRIORIDAD_URL || VALORES_POR_DEFECTO.prioridadUrl,
    rabbitmqApiUrl: entorno.RABBITMQ_API_URL || VALORES_POR_DEFECTO.rabbitmqApiUrl,
    rabbitmqUsuario: entorno.RABBITMQ_USER || VALORES_POR_DEFECTO.rabbitmqUsuario,
    rabbitmqClave: entorno.RABBITMQ_PASSWORD || VALORES_POR_DEFECTO.rabbitmqClave,
    mailpitUrl: entorno.MAILPIT_URL || VALORES_POR_DEFECTO.mailpitUrl,
    esperaCorreoMs: numero('VERIFICAR_ESPERA_CORREO_MS', VALORES_POR_DEFECTO.esperaCorreoMs),
    intervaloMs: numero('VERIFICAR_INTERVALO_MS', VALORES_POR_DEFECTO.intervaloMs),
    tiempoLimiteMs: numero('VERIFICAR_TIEMPO_LIMITE_MS', VALORES_POR_DEFECTO.tiempoLimiteMs),
  }
}

/**
 * Corre todos los pasos y devuelve uno por línea: { paso, estado, detalle }.
 * Un paso que falla no corta los siguientes, salvo los que dependen del reclamo creado.
 */
export async function verificarDemo(config, informar = () => {}) {
  const resultados = []
  const anotar = (paso, estado, detalle) => {
    const resultado = { paso, estado, detalle }
    resultados.push(resultado)
    informar(resultado)
    return resultado
  }
  const http = clienteHttp(config)

  // 1. Cada contenedor responde.
  await paso(anotar, 'Frontend (nginx)', async () => {
    const pagina = await http.texto(config.frontendUrl + '/')
    if (!pagina.includes('id="raiz"')) {
      return [FALLA, `${config.frontendUrl} no devolvió la interfaz de React`]
    }
    const categorias = await http.json(config.frontendUrl + '/api/categorias')
    return [OK, `interfaz y proxy de /api: ${categorias.length} categorías`]
  })
  await paso(anotar, 'Backend (REST 1)', async () => {
    const usuarios = await http.json(config.backendUrl + '/api/usuarios')
    await http.texto(config.backendUrl + '/v3/api-docs')
    return [OK, `${usuarios.length} usuarios en la semilla; contrato OpenAPI publicado`]
  })
  await paso(anotar, 'Servicio de cobertura (SOAP)', async () => {
    const wsdl = await http.texto(config.coberturaUrl + '/ws/cobertura.wsdl')
    if (!wsdl.includes('ConsultarCobertura')) {
      return [FALLA, 'el WSDL no tiene la operación ConsultarCobertura']
    }
    return [OK, 'WSDL con la operación ConsultarCobertura']
  })
  await paso(anotar, 'Servicio de prioridad (REST 2)', async () => {
    const contrato = await http.json(config.prioridadUrl + '/v3/api-docs')
    if (!contrato.paths?.['/api/prioridades/evaluar']) {
      return [FALLA, 'el contrato OpenAPI no tiene POST /api/prioridades/evaluar']
    }
    return [OK, 'contrato OpenAPI con POST /api/prioridades/evaluar']
  })
  await paso(anotar, 'RabbitMQ', async () => {
    const exchange = await http.json(`${config.rabbitmqApiUrl}/api/exchanges/%2F/${EXCHANGE}`, http.rabbit)
    const cola = await http.json(`${config.rabbitmqApiUrl}/api/queues/%2F/${COLA_AVISOS}`, http.rabbit)
    await http.json(`${config.rabbitmqApiUrl}/api/queues/%2F/${COLA_ERRORES}`, http.rabbit)
    if (exchange.type !== 'topic') {
      return [FALLA, `${EXCHANGE} es de tipo ${exchange.type}, no topic`]
    }
    if (!cola.consumers) {
      return [FALLA, `${COLA_AVISOS} no tiene consumidores: ¿está levantado servicio-notificaciones?`]
    }
    return [OK, `exchange ${EXCHANGE}, cola ${COLA_AVISOS} con ${cola.consumers} consumidor y cola de errores`]
  })
  await paso(anotar, 'Mailpit', async () => {
    const info = await http.json(config.mailpitUrl + '/api/v1/info')
    return [OK, `buzón web con ${info.Messages ?? 0} correos`]
  })

  // 2. Georef: sugerencias para la dirección. Sin Internet el alta sigue, con la ubicación pendiente.
  let sugerencia = null
  await paso(anotar, 'Georef (API externa)', async () => {
    const parametros = new URLSearchParams({
      direccion: RECLAMO_DE_PRUEBA.direccion,
      barrioId: String(RECLAMO_DE_PRUEBA.barrioId),
    })
    const respuesta = await http.json(`${config.backendUrl}/api/ubicaciones/sugerencias?${parametros}`)
    if (!respuesta.disponible) {
      return [AVISO, 'Georef no respondió (¿sin Internet?): el reclamo se crea con la ubicación pendiente']
    }
    if (!respuesta.sugerencias?.length) {
      return [AVISO, `Georef no encontró "${RECLAMO_DE_PRUEBA.direccion}": la ubicación queda pendiente`]
    }
    sugerencia = respuesta.sugerencias[0]
    return [OK, `${respuesta.sugerencias.length} sugerencias; se elige "${sugerencia.direccionNormalizada}"`]
  })

  // 3. Alta del reclamo: pasa por la IA, el SOAP y la outbox en una sola llamada.
  let reclamo = null
  await paso(anotar, 'Alta del reclamo', async () => {
    const cuerpo = {
      categoriaId: RECLAMO_DE_PRUEBA.categoriaId,
      barrioId: RECLAMO_DE_PRUEBA.barrioId,
      descripcion: RECLAMO_DE_PRUEBA.descripcion,
      direccion: RECLAMO_DE_PRUEBA.direccion,
    }
    if (sugerencia) {
      cuerpo.ubicacion = {
        latitud: sugerencia.latitud,
        longitud: sugerencia.longitud,
        direccionNormalizada: sugerencia.direccionNormalizada,
      }
    }
    reclamo = await http.json(config.backendUrl + '/api/reclamos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Usuario-Id': String(RECLAMO_DE_PRUEBA.usuarioId) },
      body: JSON.stringify(cuerpo),
      esperado: 201,
    })
    return [OK, `${reclamo.numero}, estado ${reclamo.estado}`]
  })

  if (!reclamo) {
    anotar('Revisión del reclamo', FALLA, 'no se pudieron revisar: el alta falló')
    return resultados
  }

  await paso(anotar, 'Prioridad con IA', async () => revisarPrioridad(reclamo))
  await paso(anotar, 'Área por SOAP', async () => revisarArea(reclamo))
  await paso(anotar, 'Ubicación', async () => revisarUbicacion(reclamo, sugerencia))

  // 4. Mensajería: la outbox publica cada 2 s y el consumidor manda el correo.
  await paso(anotar, 'Correo por RabbitMQ', async () => {
    const correos = await esperarCorreos(http, config, reclamo.numero)
    if (correos.length === 0) {
      return [FALLA, `no llegó ningún correo de ${reclamo.numero} a Mailpit en ${config.esperaCorreoMs / 1000} s`]
    }
    const alCiudadano = correos.filter((correo) =>
      (correo.To ?? []).some((destino) => destino.Address === RECLAMO_DE_PRUEBA.correoCiudadano),
    )
    if (alCiudadano.length === 0) {
      return [FALLA, `llegaron ${correos.length} correos, ninguno a ${RECLAMO_DE_PRUEBA.correoCiudadano}`]
    }
    return [OK, `${correos.length} correos; a la ciudadana: ${alCiudadano.map((c) => `"${c.Subject}"`).join(', ')}`]
  })
  await paso(anotar, 'Cola de errores', async () => {
    const cola = await http.json(`${config.rabbitmqApiUrl}/api/queues/%2F/${COLA_ERRORES}`, http.rabbit)
    const mensajes = cola.messages ?? 0
    if (mensajes > 0) {
      return [AVISO, `${COLA_ERRORES} tiene ${mensajes} mensajes: revisar los logs de servicio-notificaciones`]
    }
    return [OK, `${COLA_ERRORES} vacía`]
  })

  return resultados
}

export function revisarPrioridad(reclamo) {
  const detalle = `${reclamo.prioridad}, origen ${reclamo.origenPrioridad}: ${reclamo.motivoPrioridad ?? 'sin motivo'}`
  switch (reclamo.origenPrioridad) {
    case 'IA':
    case 'CATEGORIA':
      // CATEGORIA: la IA contestó con una prioridad menor y se mantuvo la de la categoría.
      return [OK, detalle]
    case 'RESPALDO':
      return [
        FALLA,
        `${detalle}. El servicio de IA no respondió: revisar GEMINI_API_KEY en .env o usar ` +
          'PRIORIDAD_IA_PROVEEDOR=simulado',
      ]
    default:
      return [FALLA, `${detalle}. El backend no está usando la estrategia ia`]
  }
}

export function revisarArea(reclamo) {
  if (reclamo.asignacionPendiente) {
    return [FALLA, 'asignación pendiente: el servicio de cobertura no respondió']
  }
  if (!reclamo.area) {
    return [FALLA, `estado ${reclamo.estado} sin área: la cobertura no devolvió ninguna para Luminaria en Bernal`]
  }
  return [OK, `${reclamo.estado}, área ${reclamo.area.nombre}`]
}

export function revisarUbicacion(reclamo, sugerencia) {
  const ubicacion = reclamo.ubicacion ?? {}
  if (!sugerencia) {
    return ubicacion.estado === 'PENDIENTE'
      ? [AVISO, 'pendiente, porque Georef no dio sugerencias']
      : [FALLA, `estado ${ubicacion.estado} sin haber elegido una sugerencia`]
  }
  if (ubicacion.estado !== 'CONFIRMADA') {
    return [FALLA, `se eligió una sugerencia y quedó ${ubicacion.estado}`]
  }
  return [OK, `confirmada: ${ubicacion.direccionNormalizada} (${ubicacion.latitud}, ${ubicacion.longitud})`]
}

async function esperarCorreos(http, config, numero) {
  const limite = Date.now() + config.esperaCorreoMs
  for (;;) {
    const buzon = await http.json(`${config.mailpitUrl}/api/v1/messages?limit=200`)
    const correos = (buzon.messages ?? []).filter((correo) => (correo.Subject ?? '').includes(numero))
    if (correos.length > 0 || Date.now() >= limite) {
      return correos
    }
    await new Promise((listo) => setTimeout(listo, config.intervaloMs))
  }
}

async function paso(anotar, nombre, verificar) {
  try {
    const [estado, detalle] = await verificar()
    return anotar(nombre, estado, detalle)
  } catch (error) {
    return anotar(nombre, FALLA, error.message)
  }
}

function clienteHttp(config) {
  const rabbit = {
    headers: {
      Authorization:
        'Basic ' + Buffer.from(`${config.rabbitmqUsuario}:${config.rabbitmqClave}`).toString('base64'),
    },
  }

  async function pedir(url, opciones = {}) {
    const { esperado = 200, ...resto } = opciones
    let respuesta
    try {
      respuesta = await fetch(url, { ...resto, signal: AbortSignal.timeout(config.tiempoLimiteMs) })
    } catch (error) {
      const causa = error.cause?.code ?? error.name
      throw new Error(`${url} no responde (${causa})`)
    }
    if (respuesta.status !== esperado) {
      const cuerpo = (await respuesta.text()).slice(0, 200)
      throw new Error(`${url} respondió ${respuesta.status}${cuerpo ? `: ${cuerpo}` : ''}`)
    }
    return respuesta
  }

  return {
    rabbit,
    texto: async (url, opciones) => (await pedir(url, opciones)).text(),
    json: async (url, opciones) => (await pedir(url, opciones)).json(),
  }
}

export function resumen(resultados) {
  const cuenta = (estado) => resultados.filter((resultado) => resultado.estado === estado).length
  return { ok: cuenta(OK), avisos: cuenta(AVISO), fallas: cuenta(FALLA) }
}

function imprimir({ paso: nombre, estado, detalle }) {
  console.log(`${estado.padEnd(5)}  ${nombre.padEnd(30)}  ${detalle}`)
}

async function principal() {
  const config = configuracionDesdeEntorno()
  console.log('Verificación de la demo: sistema de reclamos con sus cinco integraciones\n')
  const resultados = await verificarDemo(config, imprimir)
  const { ok, avisos, fallas } = resumen(resultados)
  console.log(`\n${ok} pasos bien, ${avisos} avisos, ${fallas} fallas.`)
  if (fallas > 0) {
    console.log('Los logs de cada contenedor se ven con: docker compose logs <servicio>')
    process.exitCode = 1
  }
}

// Se ejecuta solo cuando se lo llama directamente; los tests lo importan sin correrlo.
if (process.argv[1]?.endsWith('verificar-demo.mjs')) {
  await principal()
}
