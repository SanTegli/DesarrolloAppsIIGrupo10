import { vi } from 'vitest'

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
        usuarioId: opciones.headers?.['X-Usuario-Id'],
        cuerpo: opciones.body ? JSON.parse(opciones.body) : undefined,
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

export const USUARIOS = [
  { id: 1, nombreCompleto: 'Ana Pérez', rol: 'CIUDADANO', area: null },
  { id: 3, nombreCompleto: 'Carla Gómez', rol: 'AGENTE_MUNICIPAL', area: { id: 1, nombre: 'Alumbrado' } },
  { id: 7, nombreCompleto: 'Elena Ruiz', rol: 'ADMINISTRADOR', area: null },
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

export function reclamo(cambios = {}) {
  return {
    numero: 'REC-4F2A91BC',
    descripcion: 'Farol apagado frente a la plaza',
    direccion: 'Belgrano 450',
    estado: 'ASIGNADO',
    prioridad: 'MEDIA',
    vencido: false,
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
