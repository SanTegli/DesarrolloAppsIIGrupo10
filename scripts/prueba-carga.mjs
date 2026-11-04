// Prueba de carga básica del backend: varios usuarios simultáneos leyendo durante un rato, y un resumen de
// tiempos de respuesta y errores por endpoint.
//
// Con el sistema levantado (docker compose up), desde la raíz del repositorio:
//   docker compose run --rm -T prueba-carga > docs/evidencias/prueba-carga.md   (sin Node en la máquina)
//   node scripts/prueba-carga.mjs
//   node scripts/prueba-carga.mjs --usuarios 50 --duracion 60 --salida docs/evidencias/prueba-carga.md
//
// Por la salida estándar solo sale el informe en Markdown; el progreso va a la salida de errores.
//
// Solo lee: no crea reclamos, así no choca con el límite diario ni ensucia la base. Inicia sesión como Ana
// (contraseña de la semilla de desarrollo) para los endpoints que piden sesión.
// Sale con código 1 si hubo errores o si el percentil 95 supera el máximo (RNF-3 en docs/requerimientos.md).
// Solo usa módulos de Node: no necesita npm install.
import { writeFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

export const POR_DEFECTO = {
  backendUrl: 'http://localhost:8080',
  usuarios: 20,
  duracionSegundos: 30,
  p95MaximoMs: 1000,
  email: 'ana@example.org',
  contrasena: 'demo1234',
  tiempoLimiteMs: 10000,
}

/** Percentil por el método del rango más cercano. */
export function percentil(valores, fraccion) {
  if (valores.length === 0) return 0
  const ordenados = [...valores].sort((a, b) => a - b)
  const posicion = Math.ceil(fraccion * ordenados.length) - 1
  return ordenados[Math.min(Math.max(posicion, 0), ordenados.length - 1)]
}

/** Lee --opcion valor de la línea de comandos y las variables de entorno. */
export function configuracion(argumentos = process.argv.slice(2), entorno = process.env) {
  const opcion = (nombre) => {
    const indice = argumentos.indexOf(`--${nombre}`)
    return indice >= 0 ? argumentos[indice + 1] : undefined
  }
  const numero = (valor, porDefecto) => {
    const convertido = Number(valor)
    return Number.isFinite(convertido) && convertido > 0 ? convertido : porDefecto
  }
  return {
    backendUrl: (opcion('url') ?? entorno.BACKEND_URL ?? POR_DEFECTO.backendUrl).replace(/\/$/, ''),
    usuarios: numero(opcion('usuarios') ?? entorno.CARGA_USUARIOS, POR_DEFECTO.usuarios),
    duracionSegundos: numero(opcion('duracion') ?? entorno.CARGA_DURACION_S, POR_DEFECTO.duracionSegundos),
    p95MaximoMs: numero(opcion('p95-maximo') ?? entorno.CARGA_P95_MAXIMO_MS, POR_DEFECTO.p95MaximoMs),
    email: POR_DEFECTO.email,
    contrasena: POR_DEFECTO.contrasena,
    tiempoLimiteMs: POR_DEFECTO.tiempoLimiteMs,
    salida: opcion('salida'),
  }
}

/** Cliente con las cookies de sesión y CSRF, como un navegador. */
function cliente(config) {
  const cookies = new Map()
  async function pedir(ruta, opciones = {}) {
    const encabezados = { Accept: 'application/json', ...(opciones.headers ?? {}) }
    if (cookies.size > 0) {
      encabezados.Cookie = [...cookies].map(([nombre, valor]) => `${nombre}=${valor}`).join('; ')
    }
    const respuesta = await fetch(config.backendUrl + ruta, {
      ...opciones,
      headers: encabezados,
      signal: AbortSignal.timeout(config.tiempoLimiteMs),
    })
    for (const cookie of respuesta.headers.getSetCookie?.() ?? []) {
      const [par] = cookie.split(';')
      const separador = par.indexOf('=')
      cookies.set(par.slice(0, separador).trim(), par.slice(separador + 1).trim())
    }
    return respuesta
  }
  return { pedir, token: () => cookies.get('XSRF-TOKEN') }
}

/** Inicia sesión y arma la lista de endpoints a recorrer. */
async function preparar(config) {
  const http = cliente(config)
  await (await http.pedir('/api/categorias')).arrayBuffer()
  const login = await http.pedir('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': http.token() ?? '' },
    body: JSON.stringify({ email: config.email, contrasena: config.contrasena }),
  })
  if (login.status !== 200) {
    throw new Error(`El login de ${config.email} respondió ${login.status}: ¿está levantado el backend con la semilla?`)
  }
  const reclamos = await (await http.pedir('/api/reclamos')).json()
  const endpoints = [
    { nombre: 'GET /api/categorias', ruta: '/api/categorias' },
    { nombre: 'GET /api/dashboard/publico', ruta: '/api/dashboard/publico' },
    { nombre: 'GET /api/reclamos', ruta: '/api/reclamos' },
    { nombre: 'GET /api/notificaciones', ruta: '/api/notificaciones' },
  ]
  if (Array.isArray(reclamos) && reclamos.length > 0) {
    endpoints.push({ nombre: 'GET /api/reclamos/{numero}', ruta: `/api/reclamos/${reclamos[0].numero}` })
  }
  return { http, endpoints }
}

/**
 * Corre la carga: {@code usuarios} bucles simultáneos que recorren los endpoints hasta que pasa la duración.
 * Devuelve las métricas por endpoint y en total.
 */
export async function ejecutarCarga(config) {
  const { http, endpoints } = await preparar(config)
  const medidas = new Map(endpoints.map((endpoint) => [endpoint.nombre, { tiempos: [], errores: 0, detalleErrores: new Map() }]))
  const fin = Date.now() + config.duracionSegundos * 1000
  const inicio = Date.now()

  async function usuarioVirtual(desplazamiento) {
    let indice = desplazamiento
    while (Date.now() < fin) {
      const endpoint = endpoints[indice % endpoints.length]
      indice += 1
      const medida = medidas.get(endpoint.nombre)
      const antes = performance.now()
      try {
        const respuesta = await http.pedir(endpoint.ruta)
        await respuesta.arrayBuffer()
        medida.tiempos.push(performance.now() - antes)
        if (respuesta.status >= 400) {
          medida.errores += 1
          medida.detalleErrores.set(respuesta.status, (medida.detalleErrores.get(respuesta.status) ?? 0) + 1)
        }
      } catch (error) {
        medida.tiempos.push(performance.now() - antes)
        medida.errores += 1
        const causa = error.name === 'TimeoutError' ? 'tiempo agotado' : (error.cause?.code ?? error.name)
        medida.detalleErrores.set(causa, (medida.detalleErrores.get(causa) ?? 0) + 1)
      }
    }
  }

  await Promise.all(Array.from({ length: config.usuarios }, (_, numero) => usuarioVirtual(numero)))
  const segundos = (Date.now() - inicio) / 1000
  const filas = [...medidas].map(([nombre, medida]) => resumirFila(nombre, medida.tiempos, medida.errores, medida.detalleErrores))
  const todos = [...medidas.values()].flatMap((medida) => medida.tiempos)
  const errores = filas.reduce((total, fila) => total + fila.errores, 0)
  const total = resumirFila('Total', todos, errores, new Map())
  return {
    config,
    segundos,
    porSegundo: Math.round((todos.length / segundos) * 10) / 10,
    filas,
    total,
    cumple: errores === 0 && total.p95 <= config.p95MaximoMs && todos.length > 0,
  }
}

function resumirFila(nombre, tiempos, errores, detalleErrores) {
  const redondear = (valor) => Math.round(valor)
  return {
    nombre,
    pedidos: tiempos.length,
    errores,
    detalleErrores: Object.fromEntries(detalleErrores),
    p50: redondear(percentil(tiempos, 0.5)),
    p95: redondear(percentil(tiempos, 0.95)),
    maximo: redondear(tiempos.length ? Math.max(...tiempos) : 0),
  }
}

/** El resumen como tabla Markdown, para la consola y para guardarlo como evidencia. */
export function informe(resultado, fecha = new Date()) {
  const { config } = resultado
  const lineas = [
    '# Prueba de carga básica',
    '',
    `Corrida el ${fecha.toLocaleString('es-AR')} contra \`${config.backendUrl}\`: ${config.usuarios} usuarios simultáneos ` +
      `durante ${config.duracionSegundos} s, solo lecturas, con la sesión de ${config.email}.`,
    '',
    '| Endpoint | Pedidos | Errores | p50 (ms) | p95 (ms) | Máximo (ms) |',
    '| --- | --- | --- | --- | --- | --- |',
    ...[...resultado.filas, resultado.total].map(
      (fila) =>
        `| ${fila.nombre === 'Total' ? '**Total**' : `\`${fila.nombre}\``} | ${fila.pedidos} | ${fila.errores} | ${fila.p50} | ${fila.p95} | ${fila.maximo} |`,
    ),
    '',
    `Rendimiento: ${resultado.porSegundo} pedidos por segundo.`,
    '',
    `Meta (RNF-3): ningún error y percentil 95 ≤ ${config.p95MaximoMs} ms. Resultado: ` +
      (resultado.cumple ? '**cumple**.' : '**no cumple**.'),
  ]
  const conErrores = resultado.filas.filter((fila) => fila.errores > 0)
  if (conErrores.length > 0) {
    lineas.push('', 'Errores por causa:', '')
    for (const fila of conErrores) {
      lineas.push(`- \`${fila.nombre}\`: ${Object.entries(fila.detalleErrores).map(([causa, cantidad]) => `${causa} × ${cantidad}`).join(', ')}`)
    }
  }
  return lineas.join('\n') + '\n'
}

async function principal() {
  const config = configuracion()
  console.error(`Prueba de carga: ${config.usuarios} usuarios durante ${config.duracionSegundos} s contra ${config.backendUrl}…`)
  const resultado = await ejecutarCarga(config)
  const texto = informe(resultado)
  console.log(texto)
  if (config.salida) {
    writeFileSync(config.salida, texto, 'utf8')
    console.error(`Guardado en ${config.salida}`)
  }
  process.exitCode = resultado.cumple ? 0 : 1
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  principal().catch((error) => {
    console.error(`La prueba de carga no pudo empezar: ${error.message}`)
    process.exitCode = 1
  })
}
