import { vi } from 'vitest'

/** Lee un Blob como texto; jsdom no siempre trae Blob.text(). */
function leerTexto(blob) {
  if (typeof blob.text === 'function') {
    return blob.text()
  }
  return new Promise((listo, fallo) => {
    const lector = new FileReader()
    lector.onload = () => listo(lector.result)
    lector.onerror = () => fallo(lector.error)
    lector.readAsText(blob)
  })
}

/**
 * El alta multipart queda como un cuerpo plano: los campos de la parte `reclamo` y, en `foto`, el nombre, el
 * tipo y el tamaño del archivo. Así los tests comparan el contrato sin leer el formulario.
 */
async function leerFormulario(formulario) {
  const cuerpo = { multipart: true }
  const reclamo = formulario.get('reclamo')
  if (reclamo) {
    Object.assign(cuerpo, JSON.parse(await leerTexto(reclamo)), { tipoReclamo: reclamo.type })
  }
  const foto = formulario.get('foto')
  if (foto) {
    cuerpo.foto = { nombre: foto.name, tipo: foto.type, tamano: foto.size }
  }
  return cuerpo
}

/**
 * Reemplaza fetch por una tabla de rutas. Cada clave es "METODO /ruta" y el valor es el cuerpo,
 * o una función (peticion) => cuerpo | { status, cuerpo }. Devuelve la lista de llamadas hechas.
 */
export function simularApi(rutas) {
  const llamadas = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url, opciones = {}) => {
      const metodo = opciones.method ?? 'GET'
      const { pathname, searchParams } = new URL(url, 'http://localhost')
      const peticion = {
        metodo,
        ruta: pathname,
        consulta: Object.fromEntries(searchParams),
        csrf: opciones.headers?.['X-XSRF-TOKEN'],
        credenciales: opciones.credentials,
        cuerpo:
          opciones.body instanceof FormData
            ? await leerFormulario(opciones.body)
            : opciones.body
              ? JSON.parse(opciones.body)
              : undefined,
      }
      llamadas.push(peticion)
      const manejador = rutas[`${metodo} ${pathname}`]
      if (manejador === undefined) {
        throw new Error(`Llamada no prevista en el test: ${metodo} ${pathname}`)
      }
      const resultado = typeof manejador === 'function' ? manejador(peticion) : manejador
      const status = resultado?.status ?? 200
      const cuerpo = resultado?.status ? resultado.cuerpo : resultado
      return { ok: status < 400, status, json: async () => cuerpo }
    }),
  )
  return llamadas
}

/** Valor de la cookie XSRF-TOKEN que deja el setup de los tests. */
export const TOKEN_CSRF = 'token-de-prueba'

/** Respuesta 401 del backend cuando no hay sesión. */
export const SIN_SESION = {
  status: 401,
  cuerpo: { status: 401, codigo: 'NO_AUTENTICADO', mensaje: 'Hay que iniciar sesión para usar esta función.', detalles: [] },
}

export const USUARIOS = [
  { id: 1, nombreCompleto: 'Ana Pérez', rol: 'CIUDADANO', area: null, telefonoVerificado: '+54 •••••••0001', bloqueado: false },
  { id: 3, nombreCompleto: 'Carla Gómez', rol: 'AGENTE_MUNICIPAL', area: { id: 1, nombre: 'Alumbrado' }, telefonoVerificado: null, bloqueado: false },
  { id: 7, nombreCompleto: 'Elena Ruiz', rol: 'ADMINISTRADOR', area: null, telefonoVerificado: null, bloqueado: false },
]

export const AREAS = [
  { id: 1, nombre: 'Alumbrado', activa: true, categorias: [{ id: 1, nombre: 'Luminaria rota' }], barrios: [{ id: 2, nombre: 'Bernal' }] },
  { id: 2, nombre: 'Obras Públicas', activa: true, categorias: [{ id: 2, nombre: 'Bache' }], barrios: [{ id: 2, nombre: 'Bernal' }] },
  { id: 3, nombre: 'Higiene Urbana', activa: false, categorias: [{ id: 3, nombre: 'Residuos' }], barrios: [{ id: 2, nombre: 'Bernal' }] },
]

export const CATEGORIAS = [
  { id: 1, nombre: 'Luminaria rota', descripcion: 'Farol apagado o dañado', slaHoras: 48, prioridadBase: 'MEDIA' },
]

export const BARRIOS = [{ id: 2, nombre: 'Bernal', municipio: 'Quilmes' }]

/** Un archivo de imagen para subir en los tests (el contenido no importa: la interfaz no lo decodifica). */
export function archivoFoto({ nombre = 'farol.jpg', tipo = 'image/jpeg', tamano = 2048 } = {}) {
  const archivo = new File([new Uint8Array(Math.min(tamano, 16))], nombre, { type: tipo })
  if (tamano > 16) {
    Object.defineProperty(archivo, 'size', { value: tamano })
  }
  return archivo
}

export function reclamo(cambios = {}) {
  return {
    numero: 'REC-4F2A91BC',
    descripcion: 'Farol apagado frente a la plaza',
    direccion: 'Belgrano 450',
    ubicacion: { estado: 'PENDIENTE', latitud: null, longitud: null, direccionNormalizada: null, proveedor: null },
    foto: '/api/reclamos/REC-4F2A91BC/foto',
    estado: 'ASIGNADO',
    prioridad: 'MEDIA',
    origenPrioridad: 'CATEGORIA',
    motivoPrioridad: null,
    vencido: false,
    asignacionPendiente: false,
    fechaCreacion: '2026-10-05T10:00:00',
    fechaLimite: '2026-10-07T10:00:00',
    categoria: { id: 1, nombre: 'Luminaria rota' },
    barrio: { id: 2, nombre: 'Bernal' },
    ciudadano: { id: 1, nombreCompleto: 'Ana Pérez' },
    area: { id: 1, nombre: 'Alumbrado' },
    agente: null,
    historial: [
      { estadoAnterior: null, estadoNuevo: 'INGRESADO', fecha: '2026-10-05T10:00:00', observacion: 'Reclamo ingresado', usuario: { id: 1, nombreCompleto: 'Ana Pérez' } },
      { estadoAnterior: 'INGRESADO', estadoNuevo: 'ASIGNADO', fecha: '2026-10-05T10:00:00', observacion: 'Asignación automática', usuario: null },
    ],
    accionesDisponibles: [],
    ...cambios,
  }
}
