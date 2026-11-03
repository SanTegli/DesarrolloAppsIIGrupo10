// API simulada para desarrollar y probar la interfaz sin Java ni MySQL.
//   npm run mock        (escucha en http://localhost:8080)
//
// Sigue docs/api-contract.md y guarda todo en memoria: al reiniciar se pierde.
// Como el backend, usa login con sesión (cookie JSESSIONID) y CSRF (cookie XSRF-TOKEN y encabezado
// X-XSRF-TOKEN). Los siete usuarios de la semilla entran con la contraseña demo1234.
// El comportamiento de referencia es el del backend Spring; esto es solo una ayuda de desarrollo.
import { createServer } from 'node:http'
import { randomUUID } from 'node:crypto'

const PUERTO = Number(process.env.PUERTO || 8080)
const ESTRATEGIA_PRIORIDAD = process.env.ESTRATEGIA_PRIORIDAD || 'categoria' // categoria | palabras-clave
const ESTRATEGIA_ASIGNACION = process.env.ESTRATEGIA_ASIGNACION || 'jurisdiccion' // jurisdiccion | carga-trabajo
// Control de vencimientos, como TareaVencimientos en el backend.
const INTERVALO_VENCIMIENTOS_MS = Number(process.env.INTERVALO_VENCIMIENTOS_MS || 60000)
// Para probar el vencimiento sin esperar días: plazo en segundos para todos los reclamos nuevos.
const SLA_SEGUNDOS = process.env.SLA_SEGUNDOS ? Number(process.env.SLA_SEGUNDOS) : null

// Datos semilla acordados en el contrato.
const barrios = [
  { id: 1, nombre: 'Quilmes Centro', municipio: 'Quilmes' },
  { id: 2, nombre: 'Bernal', municipio: 'Quilmes' },
  { id: 3, nombre: 'Ezpeleta', municipio: 'Quilmes' },
]
const categorias = [
  { id: 1, nombre: 'Luminaria rota', descripcion: 'Farol apagado o dañado', slaHoras: 48, prioridadBase: 'MEDIA' },
  { id: 2, nombre: 'Bache', descripcion: 'Pozo en la calzada', slaHoras: 120, prioridadBase: 'BAJA' },
  { id: 3, nombre: 'Residuos', descripcion: 'Residuos en la vía pública', slaHoras: 72, prioridadBase: 'MEDIA' },
]
const areas = [
  { id: 1, nombre: 'Alumbrado', activa: true, categorias: [1], barrios: [1, 2] },
  { id: 2, nombre: 'Obras Públicas', activa: true, categorias: [2], barrios: [1, 2, 3] },
  { id: 3, nombre: 'Higiene Urbana', activa: true, categorias: [3], barrios: [1, 2] },
  { id: 4, nombre: 'Mantenimiento Vial', activa: true, categorias: [2], barrios: [2] },
]
// Solo es una ayuda de desarrollo: las contraseñas quedan en memoria y en texto. El backend guarda BCrypt.
const CONTRASENA_SEMILLA = 'demo1234'
const usuarios = [
  { id: 1, nombreCompleto: 'Ana Pérez', rol: 'CIUDADANO', areaId: null, email: 'ana@example.org', dni: '30111222' },
  { id: 2, nombreCompleto: 'Bruno Díaz', rol: 'CIUDADANO', areaId: null, email: 'bruno@example.org', dni: '31222333' },
  { id: 3, nombreCompleto: 'Carla Gómez', rol: 'AGENTE_MUNICIPAL', areaId: 1, email: 'carla@example.org', dni: '25333444' },
  { id: 4, nombreCompleto: 'Diego Sosa', rol: 'AGENTE_MUNICIPAL', areaId: 2, email: 'diego@example.org', dni: '26444555' },
  { id: 5, nombreCompleto: 'Fabián Luna', rol: 'AGENTE_MUNICIPAL', areaId: 3, email: 'fabian@example.org', dni: '27555666' },
  { id: 6, nombreCompleto: 'Gabriela Paz', rol: 'AGENTE_MUNICIPAL', areaId: 4, email: 'gabriela@example.org', dni: '28666777' },
  { id: 7, nombreCompleto: 'Elena Ruiz', rol: 'ADMINISTRADOR', areaId: null, email: 'elena@example.org', dni: '20555666' },
].map((usuario) => ({ ...usuario, contrasena: CONTRASENA_SEMILLA }))
// id de sesión -> id de usuario, como la sesión HTTP del backend.
const sesiones = new Map()
const reclamos = []
const notificaciones = []

// Respuestas fijas en lugar de Georef: "Belgrano" devuelve dos opciones, "caido" simula Georef sin responder
// y cualquier otra dirección, ninguna. Las coordenadas son las que devolvió Georef para Belgrano 450.
const SUGERENCIAS_BELGRANO = [
  { direccionNormalizada: 'GRL BELGRANO 450, Bernal Oeste, Quilmes, Buenos Aires', latitud: -34.73614578, longitud: -58.3228998575 },
  { direccionNormalizada: 'GRL BELGRANO 450, Quilmes, Buenos Aires', latitud: -34.75816353, longitud: -58.29744745 },
]
// Recuadro de Quilmes, igual que reclamos.geocodificacion.recuadros en application.yml.
const RECUADRO_QUILMES = { latitudMinima: -34.82, latitudMaxima: -34.66, longitudMinima: -58.38, longitudMaxima: -58.17 }

function sugerirUbicaciones(parametros) {
  const direccion = (parametros.get('direccion') ?? '').trim()
  const barrioId = Number(parametros.get('barrioId'))
  const detalles = []
  if (direccion.length < 3 || direccion.length > 200) detalles.push('direccion: entre 3 y 200 caracteres')
  if (!Number.isInteger(barrioId) || barrioId <= 0) detalles.push('barrioId: es obligatorio')
  if (detalles.length) throw invalido('Los parametros recibidos no son validos.', detalles)
  buscar(barrios, barrioId, 'barrio')
  if (/caido/i.test(direccion)) return { disponible: false, sugerencias: [] }
  return { disponible: true, sugerencias: /belgrano/i.test(direccion) ? SUGERENCIAS_BELGRANO : [] }
}

// Igual que ServicioUbicaciones.confirmar: sin elección queda pendiente; fuera del municipio, 400.
function confirmarUbicacion(ubicacion) {
  if (ubicacion === undefined || ubicacion === null) {
    return { estado: 'PENDIENTE', latitud: null, longitud: null, direccionNormalizada: null, proveedor: null }
  }
  const { latitud, longitud, direccionNormalizada } = ubicacion
  if (!Number.isFinite(latitud) || !Number.isFinite(longitud) || typeof direccionNormalizada !== 'string' || !direccionNormalizada.trim()) {
    throw invalido('Los datos recibidos no son validos.', ['ubicacion: latitud, longitud y direccionNormalizada son obligatorias'])
  }
  const r = RECUADRO_QUILMES
  if (latitud < r.latitudMinima || latitud > r.latitudMaxima || longitud < r.longitudMinima || longitud > r.longitudMaxima) {
    throw invalido('La ubicación elegida queda fuera de Quilmes.')
  }
  return { estado: 'CONFIRMADA', latitud, longitud, direccionNormalizada: direccionNormalizada.trim(), proveedor: 'georef' }
}

// Misma tabla que PoliticaTransiciones en el dominio.
const TRANSICIONES = [
  ['INGRESADO', 'ASIGNADO', ['SISTEMA', 'ADMINISTRADOR']],
  ['INGRESADO', 'RECHAZADO', ['ADMINISTRADOR']],
  ['INGRESADO', 'CANCELADO', ['CIUDADANO']],
  ['ASIGNADO', 'ASIGNADO', ['ADMINISTRADOR']],
  ['ASIGNADO', 'EN_PROCESO', ['AGENTE_MUNICIPAL']],
  ['ASIGNADO', 'CANCELADO', ['CIUDADANO']],
  ['EN_PROCESO', 'RESUELTO', ['AGENTE_MUNICIPAL']],
  ['RESUELTO', 'CERRADO', ['CIUDADANO']],
  ['RESUELTO', 'EN_PROCESO', ['CIUDADANO']],
]
const ESTADOS = ['INGRESADO', 'ASIGNADO', 'EN_PROCESO', 'RESUELTO', 'CERRADO', 'RECHAZADO', 'CANCELADO']
const PRIORIDADES = ['BAJA', 'MEDIA', 'ALTA', 'CRITICA']
const PALABRAS_CLAVE = /(?<![\p{L}\p{N}_])(urgente|peligro|riesgo|accidente)(?![\p{L}\p{N}_])/iu

class ErrorHttp extends Error {
  constructor(status, codigo, mensaje, detalles = []) {
    super(mensaje)
    Object.assign(this, { status, codigo, detalles })
  }
}
const invalido = (mensaje, detalles) => new ErrorHttp(400, 'DATOS_INVALIDOS', mensaje, detalles)
const denegado = (mensaje) => new ErrorHttp(403, 'ACCESO_DENEGADO', mensaje)
const inexistente = (recurso, id) =>
  new ErrorHttp(404, 'RECURSO_NO_ENCONTRADO', `No existe ${recurso} con identificador ${id}`)
const regla = (mensaje) => new ErrorHttp(409, 'REGLA_NEGOCIO', mensaje)

function ahora() {
  const fecha = new Date()
  const local = new Date(fecha.getTime() - fecha.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 19)
}
function sumarSegundos(iso, segundos) {
  return new Date(Date.parse(`${iso}Z`) + segundos * 1000).toISOString().slice(0, 19)
}

const referencia = (objeto) => (objeto ? { id: objeto.id, nombre: objeto.nombre } : null)
const referenciaUsuario = (usuario) => (usuario ? { id: usuario.id, nombreCompleto: usuario.nombreCompleto } : null)
const buscar = (lista, id, recurso) => {
  const encontrado = lista.find((elemento) => elemento.id === id)
  if (!encontrado) throw inexistente(recurso, id)
  return encontrado
}

function usuarioDe(contexto) {
  const usuarioId = sesiones.get(contexto.cookies.JSESSIONID)
  if (!usuarioId) {
    throw new ErrorHttp(401, 'NO_AUTENTICADO', 'Hay que iniciar sesión para usar esta función.')
  }
  return buscar(usuarios, usuarioId, 'usuario')
}

const usuarioPublico = (usuario) => ({
  id: usuario.id,
  nombreCompleto: usuario.nombreCompleto,
  rol: usuario.rol,
  area: referencia(areas.find((a) => a.id === usuario.areaId)),
})

function leerCookies(peticion) {
  const cookies = {}
  for (const parte of (peticion.headers.cookie ?? '').split(';')) {
    const [nombre, ...valor] = parte.trim().split('=')
    if (nombre) cookies[nombre] = decodeURIComponent(valor.join('='))
  }
  return cookies
}

function login(contexto, cuerpo) {
  const email = String(cuerpo.email ?? '').trim().toLowerCase()
  const usuario = usuarios.find((candidato) => candidato.email === email)
  if (!usuario || usuario.contrasena !== cuerpo.contrasena) {
    throw new ErrorHttp(401, 'CREDENCIALES_INVALIDAS', 'El correo o la contraseña no son correctos.')
  }
  sesiones.delete(contexto.cookies.JSESSIONID)
  const sesion = randomUUID()
  sesiones.set(sesion, usuario.id)
  contexto.setCookies.push(`JSESSIONID=${sesion}; Path=/; HttpOnly; SameSite=Lax`)
  return usuarioPublico(usuario)
}

function registrarCuenta(cuerpo) {
  const detalles = []
  const texto = (campo) => String(cuerpo[campo] ?? '').trim()
  if (!/^\d{7,8}$/.test(texto('dni'))) detalles.push('dni: debe tener 7 u 8 dígitos')
  if (!texto('nombre')) detalles.push('nombre: no debe estar vacío')
  if (!texto('apellido')) detalles.push('apellido: no debe estar vacío')
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(texto('email'))) detalles.push('email: debe ser una dirección de correo')
  if (String(cuerpo.contrasena ?? '').length < 8) detalles.push('contrasena: debe tener al menos 8 caracteres')
  if (detalles.length) throw invalido('Los datos recibidos no son validos.', detalles)
  const email = texto('email').toLowerCase()
  if (usuarios.some((u) => u.email === email)) throw regla('Ya hay una cuenta con ese correo electrónico.')
  if (usuarios.some((u) => u.dni === texto('dni'))) throw regla('Ya hay una cuenta con ese DNI.')
  const usuario = {
    id: Math.max(...usuarios.map((u) => u.id)) + 1,
    nombreCompleto: `${texto('nombre')} ${texto('apellido')}`,
    rol: 'CIUDADANO',
    areaId: null,
    email,
    dni: texto('dni'),
    contrasena: cuerpo.contrasena,
  }
  usuarios.push(usuario)
  return usuarioPublico(usuario)
}

function puedeConsultar(usuario, reclamo) {
  if (usuario.rol === 'ADMINISTRADOR') return true
  if (usuario.rol === 'CIUDADANO') return reclamo.ciudadanoId === usuario.id
  return reclamo.areaId !== null && reclamo.areaId === usuario.areaId
}
function reclamoVisible(usuario, numero) {
  const reclamo = reclamos.find((candidato) => candidato.numero === numero)
  if (!reclamo) throw inexistente('reclamo', numero)
  if (!puedeConsultar(usuario, reclamo)) throw denegado('El usuario no puede consultar este reclamo.')
  return reclamo
}
function validarTransicion(origen, destino, rol) {
  const transicion = TRANSICIONES.find(([desde, hacia]) => desde === origen && hacia === destino)
  if (!transicion) throw regla(`Un reclamo en estado ${origen} no puede pasar a ${destino}.`)
  if (!transicion[2].includes(rol)) {
    throw denegado(`El rol ${rol} no puede pasar un reclamo de ${origen} a ${destino}.`)
  }
}
const accionesDisponibles = (usuario, reclamo) =>
  TRANSICIONES.filter(([desde, , roles]) => desde === reclamo.estado && roles.includes(usuario.rol)).map(
    ([, hacia]) => hacia,
  )

function resumen(reclamo) {
  return {
    numero: reclamo.numero,
    direccion: reclamo.direccion,
    estado: reclamo.estado,
    prioridad: reclamo.prioridad,
    vencido: reclamo.vencido,
    asignacionPendiente: false,
    fechaCreacion: reclamo.fechaCreacion,
    fechaLimite: reclamo.fechaLimite,
    categoria: referencia(categorias.find((c) => c.id === reclamo.categoriaId)),
    barrio: referencia(barrios.find((b) => b.id === reclamo.barrioId)),
    ciudadano: referenciaUsuario(usuarios.find((u) => u.id === reclamo.ciudadanoId)),
    area: referencia(areas.find((a) => a.id === reclamo.areaId)),
  }
}
function detalle(usuario, reclamo) {
  return {
    ...resumen(reclamo),
    descripcion: reclamo.descripcion,
    ubicacion: reclamo.ubicacion,
    origenPrioridad: reclamo.origenPrioridad,
    motivoPrioridad: reclamo.motivoPrioridad,
    agente: referenciaUsuario(usuarios.find((u) => u.id === reclamo.agenteId)),
    historial: reclamo.historial.map((cambio) => ({
      ...cambio,
      usuario: referenciaUsuario(usuarios.find((u) => u.id === cambio.usuario)),
    })),
    accionesDisponibles: accionesDisponibles(usuario, reclamo),
  }
}

function registrar(reclamo, anterior, usuario, observacion, fecha) {
  reclamo.historial.push({
    estadoAnterior: anterior,
    estadoNuevo: reclamo.estado,
    fecha,
    observacion: observacion ?? null,
    usuario: usuario ? usuario.id : null,
  })
}
function notificar(reclamo, destinatarioId, mensaje, fecha) {
  notificaciones.push({ numero: reclamo.numero, canal: 'INTERNO', destinatarioId, mensaje, fechaEnvio: fecha })
}
// Igual que ServicioNotificaciones.avisarAsignacion: al ciudadano y a los agentes del área.
function notificarAsignacion(reclamo, area, fecha) {
  notificar(reclamo, reclamo.ciudadanoId, `Tu reclamo ${reclamo.numero} fue asignado al area ${area.nombre}.`, fecha)
  usuarios
    .filter((usuario) => usuario.rol === 'AGENTE_MUNICIPAL' && usuario.areaId === area.id)
    .forEach((agente) =>
      notificar(reclamo, agente.id, `El reclamo ${reclamo.numero} fue asignado a tu area ${area.nombre}.`, fecha),
    )
}
// Igual que ServicioVencimientos.marcarVencidos y ServicioNotificaciones.avisarVencimiento:
// marca una sola vez, sube un nivel de prioridad y avisa al ciudadano y al agente o al área.
function marcarVencidos() {
  const fecha = ahora()
  reclamos
    .filter((r) => !r.vencido && ['INGRESADO', 'ASIGNADO', 'EN_PROCESO'].includes(r.estado) && r.fechaLimite < fecha)
    .forEach((reclamo) => {
      reclamo.vencido = true
      reclamo.prioridad = PRIORIDADES[Math.min(PRIORIDADES.indexOf(reclamo.prioridad) + 1, PRIORIDADES.length - 1)]
      notificar(
        reclamo,
        reclamo.ciudadanoId,
        `Tu reclamo ${reclamo.numero} supero su fecha limite y paso a prioridad ${reclamo.prioridad}.`,
        fecha,
      )
      const aviso = `El reclamo ${reclamo.numero} vencio sin resolverse. Prioridad ${reclamo.prioridad}.`
      if (reclamo.agenteId) {
        notificar(reclamo, reclamo.agenteId, aviso, fecha)
      } else if (reclamo.areaId) {
        usuarios
          .filter((usuario) => usuario.rol === 'AGENTE_MUNICIPAL' && usuario.areaId === reclamo.areaId)
          .forEach((agente) => notificar(reclamo, agente.id, aviso, fecha))
      }
      console.log(`Vencimiento: ${reclamo.numero} pasa a prioridad ${reclamo.prioridad}`)
    })
}
const pendientes = (areaId) =>
  reclamos.filter((r) => r.areaId === areaId && (r.estado === 'ASIGNADO' || r.estado === 'EN_PROCESO')).length

function crear(usuario, cuerpo) {
  const detalles = []
  if (!Number.isInteger(cuerpo.categoriaId) || cuerpo.categoriaId <= 0) detalles.push('categoriaId: es obligatorio')
  if (!Number.isInteger(cuerpo.barrioId) || cuerpo.barrioId <= 0) detalles.push('barrioId: es obligatorio')
  if (typeof cuerpo.descripcion !== 'string' || !cuerpo.descripcion.trim() || cuerpo.descripcion.length > 1000) {
    detalles.push('descripcion: es obligatoria, hasta 1000 caracteres')
  }
  if (typeof cuerpo.direccion !== 'string' || !cuerpo.direccion.trim() || cuerpo.direccion.length > 200) {
    detalles.push('direccion: es obligatoria, hasta 200 caracteres')
  }
  if (detalles.length) throw invalido('Los datos recibidos no son validos.', detalles)
  if (usuario.rol !== 'CIUDADANO') throw denegado('Solo un ciudadano puede crear reclamos.')
  const categoria = buscar(categorias, cuerpo.categoriaId, 'categoría')
  const barrio = buscar(barrios, cuerpo.barrioId, 'barrio')
  const ubicacion = confirmarUbicacion(cuerpo.ubicacion)

  const fecha = ahora()
  // Igual que PrioridadPorPalabrasClave: solo cambia el origen si la palabra sube la prioridad.
  let prioridad = categoria.prioridadBase
  let origenPrioridad = 'CATEGORIA'
  let motivoPrioridad = null
  const palabra = ESTRATEGIA_PRIORIDAD === 'palabras-clave' ? cuerpo.descripcion.match(PALABRAS_CLAVE) : null
  if (palabra && PRIORIDADES.indexOf('ALTA') > PRIORIDADES.indexOf(prioridad)) {
    prioridad = 'ALTA'
    origenPrioridad = 'PALABRAS_CLAVE'
    motivoPrioridad = `La descripción menciona "${palabra[1].toLowerCase()}".`
  }
  const reclamo = {
    numero: `REC-${randomUUID().slice(0, 8).toUpperCase()}`,
    descripcion: cuerpo.descripcion.trim(),
    direccion: cuerpo.direccion.trim(),
    ubicacion,
    estado: 'INGRESADO',
    prioridad,
    origenPrioridad,
    motivoPrioridad,
    vencido: false,
    fechaCreacion: fecha,
    fechaLimite: sumarSegundos(fecha, SLA_SEGUNDOS ?? categoria.slaHoras * 3600),
    categoriaId: categoria.id,
    barrioId: barrio.id,
    ciudadanoId: usuario.id,
    areaId: null,
    agenteId: null,
    historial: [],
  }
  registrar(reclamo, null, usuario, 'Reclamo ingresado', fecha)
  notificar(reclamo, usuario.id, `Tu reclamo ${reclamo.numero} fue ingresado.`, fecha)

  const candidatas = areas
    .filter((a) => a.activa && a.categorias.includes(categoria.id) && a.barrios.includes(barrio.id))
    .sort((a, b) =>
      ESTRATEGIA_ASIGNACION === 'carga-trabajo' ? pendientes(a.id) - pendientes(b.id) || a.id - b.id : a.id - b.id,
    )
  if (candidatas.length) {
    reclamo.areaId = candidatas[0].id
    reclamo.estado = 'ASIGNADO'
    registrar(reclamo, 'INGRESADO', null, 'Asignación automática', fecha)
    notificarAsignacion(reclamo, candidatas[0], fecha)
  }
  reclamos.unshift(reclamo)
  return reclamo
}

function cambiarEstado(usuario, reclamo, cuerpo) {
  if (!ESTADOS.includes(cuerpo.estado)) throw invalido('Faltan datos obligatorios o su formato es invalido.')
  if (cuerpo.estado === 'ASIGNADO') throw invalido('Para asignar un área se usa la operación de asignación.')
  validarTransicion(reclamo.estado, cuerpo.estado, usuario.rol)
  // Igual que Reclamo.cambiarEstado: primero la transición y el rol, después la pertenencia.
  if (usuario.rol === 'CIUDADANO' && reclamo.ciudadanoId !== usuario.id) {
    throw denegado(`El reclamo ${reclamo.numero} pertenece a otro ciudadano.`)
  }
  if (usuario.rol === 'AGENTE_MUNICIPAL' && reclamo.areaId !== usuario.areaId) {
    throw denegado(`El reclamo ${reclamo.numero} está asignado a otra área.`)
  }
  const anterior = reclamo.estado
  const fecha = ahora()
  if (anterior === 'ASIGNADO' && cuerpo.estado === 'EN_PROCESO') reclamo.agenteId = usuario.id
  reclamo.estado = cuerpo.estado
  registrar(reclamo, anterior, usuario, cuerpo.observacion, fecha)
  if (cuerpo.estado === 'RESUELTO') {
    notificar(
      reclamo,
      reclamo.ciudadanoId,
      `Tu reclamo ${reclamo.numero} fue resuelto. Podes confirmar el cierre o reabrirlo.`,
      fecha,
    )
  } else {
    const mensaje = `El reclamo ${reclamo.numero} paso de ${anterior} a ${cuerpo.estado}.`
    notificar(reclamo, reclamo.ciudadanoId, mensaje, fecha)
    if (anterior === 'RESUELTO' && reclamo.agenteId) notificar(reclamo, reclamo.agenteId, mensaje, fecha)
  }
}

function asignar(usuario, reclamo, cuerpo) {
  if (!Number.isInteger(cuerpo.areaId) || cuerpo.areaId <= 0) {
    throw invalido('Los datos recibidos no son validos.', ['areaId: es obligatorio'])
  }
  const area = buscar(areas, cuerpo.areaId, 'área')
  validarTransicion(reclamo.estado, 'ASIGNADO', usuario.rol)
  if (!area.activa) throw regla(`El área ${area.nombre} no está activa.`)
  if (reclamo.areaId === area.id) throw regla(`El reclamo ya está asignado al área ${area.nombre}.`)
  const anterior = reclamo.estado
  const fecha = ahora()
  reclamo.areaId = area.id
  reclamo.agenteId = null
  reclamo.estado = 'ASIGNADO'
  registrar(reclamo, anterior, usuario, cuerpo.observacion, fecha)
  notificarAsignacion(reclamo, area, fecha)
}

function listar(usuario, consulta) {
  const estado = consulta.get('estado')
  const areaId = consulta.get('areaId')
  const ciudadanoId = consulta.get('ciudadanoId')
  if (estado && !ESTADOS.includes(estado)) throw invalido('Faltan datos obligatorios o su formato es invalido.')
  if ((areaId || ciudadanoId) && usuario.rol !== 'ADMINISTRADOR') {
    throw denegado('Solo un administrador puede filtrar por área o ciudadano.')
  }
  return reclamos
    .filter((r) => puedeConsultar(usuario, r))
    .filter((r) => !estado || r.estado === estado)
    .filter((r) => !areaId || r.areaId === Number(areaId))
    .filter((r) => !ciudadanoId || r.ciudadanoId === Number(ciudadanoId))
    .map(resumen)
}

async function leerCuerpo(peticion) {
  let texto = ''
  for await (const parte of peticion) texto += parte
  try {
    const cuerpo = JSON.parse(texto)
    if (cuerpo && typeof cuerpo === 'object') return cuerpo
  } catch {
    // Cae al error de abajo.
  }
  throw invalido('Faltan datos obligatorios o su formato es invalido.')
}

async function atender(peticion, contexto) {
  const url = new URL(peticion.url, 'http://localhost')
  const partes = url.pathname.split('/').filter(Boolean) // ["api", "reclamos", numero, accion]
  const metodo = peticion.method
  if (partes[0] !== 'api') throw new ErrorHttp(404, 'RECURSO_NO_ENCONTRADO', 'El recurso solicitado no existe.')

  if (metodo === 'GET' && partes.length === 2) {
    if (partes[1] === 'categorias') return [200, categorias]
    if (partes[1] === 'barrios') return [200, barrios]
    if (partes[1] === 'areas') {
      const lista = areas.map((a) => ({
        id: a.id,
        nombre: a.nombre,
        activa: a.activa,
        categorias: a.categorias.map((id) => referencia(categorias.find((c) => c.id === id))),
        barrios: a.barrios.map((id) => referencia(barrios.find((b) => b.id === id))),
      }))
      return [200, lista]
    }
    if (partes[1] === 'usuarios') {
      if (usuarioDe(contexto).rol !== 'ADMINISTRADOR') throw denegado('Solo un administrador puede ver la lista de usuarios.')
      return [200, usuarios.map(usuarioPublico)]
    }
  }

  if (partes[1] === 'auth' && partes.length === 3) {
    if (metodo === 'POST' && partes[2] === 'login') return [200, login(contexto, await leerCuerpo(peticion))]
    if (metodo === 'POST' && partes[2] === 'registro') {
      const usuario = registrarCuenta(await leerCuerpo(peticion))
      return [201, usuario, { Location: `/api/usuarios/${usuario.id}` }]
    }
    if (metodo === 'GET' && partes[2] === 'yo') return [200, usuarioPublico(usuarioDe(contexto))]
    if (metodo === 'POST' && partes[2] === 'logout') {
      sesiones.delete(contexto.cookies.JSESSIONID)
      contexto.setCookies.push('JSESSIONID=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax')
      return [204, null]
    }
  }

  if (metodo === 'GET' && partes[1] === 'ubicaciones' && partes[2] === 'sugerencias' && partes.length === 3) {
    usuarioDe(contexto)
    return [200, sugerirUbicaciones(url.searchParams)]
  }

  // Bandeja del usuario: sus avisos, del más nuevo al más viejo.
  if (partes[1] === 'notificaciones' && partes.length === 2 && metodo === 'GET') {
    const usuario = usuarioDe(contexto)
    const lista = notificaciones
      .filter((n) => n.destinatarioId === usuario.id)
      .map((n) => ({ numeroReclamo: n.numero, canal: n.canal, mensaje: n.mensaje, fechaEnvio: n.fechaEnvio }))
      .reverse()
    return [200, lista]
  }

  if (partes[1] === 'reclamos') {
    const usuario = usuarioDe(contexto)
    if (partes.length === 2 && metodo === 'GET') return [200, listar(usuario, url.searchParams)]
    if (partes.length === 2 && metodo === 'POST') {
      const reclamo = crear(usuario, await leerCuerpo(peticion))
      return [201, detalle(usuario, reclamo), { Location: `/api/reclamos/${reclamo.numero}` }]
    }
    if (partes.length === 3 && metodo === 'GET') return [200, detalle(usuario, reclamoVisible(usuario, partes[2]))]
    if (partes.length === 4 && metodo === 'GET' && partes[3] === 'notificaciones') {
      const reclamo = reclamoVisible(usuario, partes[2])
      const lista = notificaciones
        .filter((n) => n.numero === reclamo.numero)
        .map((n) => ({
          canal: n.canal,
          destinatario: referenciaUsuario(usuarios.find((u) => u.id === n.destinatarioId)),
          mensaje: n.mensaje,
          fechaEnvio: n.fechaEnvio,
        }))
      return [200, lista]
    }
    if (partes.length === 4 && metodo === 'PATCH' && (partes[3] === 'estado' || partes[3] === 'asignacion')) {
      const cuerpo = await leerCuerpo(peticion)
      const reclamo = reclamos.find((candidato) => candidato.numero === partes[2])
      if (!reclamo) throw inexistente('reclamo', partes[2])
      if (partes[3] === 'estado') {
        cambiarEstado(usuario, reclamo, cuerpo)
      } else {
        asignar(usuario, reclamo, cuerpo)
      }
      return [200, detalle(usuario, reclamo)]
    }
  }
  throw new ErrorHttp(404, 'RECURSO_NO_ENCONTRADO', 'El recurso solicitado no existe.')
}

createServer(async (peticion, respuesta) => {
  let status
  let cuerpo
  let encabezados = {}
  const contexto = { cookies: leerCookies(peticion), setCookies: [] }
  // CSRF como en el backend: la cookie sale en cualquier respuesta y los POST y PATCH la devuelven en el encabezado.
  const tokenCsrf = contexto.cookies['XSRF-TOKEN']
  if (!tokenCsrf) {
    contexto.setCookies.push(`XSRF-TOKEN=${randomUUID()}; Path=/; SameSite=Lax`)
  }
  try {
    if (!['GET', 'HEAD'].includes(peticion.method) && (!tokenCsrf || peticion.headers['x-xsrf-token'] !== tokenCsrf)) {
      throw new ErrorHttp(403, 'CSRF_INVALIDO', 'Falta el token de seguridad o venció. Recargá la página e intentá de nuevo.')
    }
    ;[status, cuerpo, encabezados = {}] = await atender(peticion, contexto)
  } catch (error) {
    status = error.status ?? 500
    cuerpo = {
      status,
      codigo: error.codigo ?? 'ERROR_INTERNO',
      mensaje: error.status ? error.message : 'Ocurrio un error interno. Intentalo nuevamente.',
      detalles: error.detalles ?? [],
      ruta: peticion.url.split('?')[0],
      fecha: ahora(),
    }
    if (!error.status) console.error(error)
  }
  if (contexto.setCookies.length) {
    encabezados = { ...encabezados, 'Set-Cookie': contexto.setCookies }
  }
  if (status === 204) {
    respuesta.writeHead(status, encabezados)
    respuesta.end()
  } else {
    respuesta.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', ...encabezados })
    respuesta.end(JSON.stringify(cuerpo))
  }
  console.log(`${peticion.method} ${peticion.url} -> ${status}`)
}).listen(PUERTO, () => {
  console.log(`API simulada en http://localhost:${PUERTO}/api`)
  console.log(`Usuarios de la semilla, contraseña ${CONTRASENA_SEMILLA}: ${usuarios.map((u) => u.email).join(', ')}`)
  console.log(`Prioridad: ${ESTRATEGIA_PRIORIDAD}. Asignación: ${ESTRATEGIA_ASIGNACION}.`)
  console.log(`Vencimientos: control cada ${INTERVALO_VENCIMIENTOS_MS} ms.`)
})
setInterval(marcarVencidos, INTERVALO_VENCIMIENTOS_MS)
