import { describe, expect, it } from 'vitest'
import { describirUsuario, esAccionDefinitiva, formatearFecha, nombreDeAccion } from './etiquetas.js'

describe('etiquetas', () => {
  it('nombra cada transición como la acción que ve el usuario', () => {
    expect(nombreDeAccion('ASIGNADO', 'EN_PROCESO')).toBe('Tomar reclamo')
    expect(nombreDeAccion('RESUELTO', 'EN_PROCESO')).toBe('Reabrir')
    expect(nombreDeAccion('EN_PROCESO', 'RESUELTO')).toBe('Marcar como resuelto')
    expect(nombreDeAccion('RESUELTO', 'CERRADO')).toBe('Confirmar solución')
    expect(nombreDeAccion('INGRESADO', 'CANCELADO')).toBe('Cancelar reclamo')
    expect(nombreDeAccion('INGRESADO', 'RECHAZADO')).toBe('Rechazar')
  })

  it('marca como definitivas solo cancelar y rechazar', () => {
    expect(esAccionDefinitiva('CANCELADO')).toBe(true)
    expect(esAccionDefinitiva('RECHAZADO')).toBe(true)
    expect(esAccionDefinitiva('CERRADO')).toBe(false)
  })

  it('formatea las fechas del contrato sin aplicar zona horaria', () => {
    expect(formatearFecha('2026-10-05T10:00:00')).toBe('05/10/2026 10:00')
    expect(formatearFecha('2026-01-31T23:59:59.123')).toBe('31/01/2026 23:59')
    expect(formatearFecha(null)).toBe('')
  })

  it('describe al usuario con su rol y, si es agente, su área', () => {
    expect(describirUsuario({ nombreCompleto: 'Ana Pérez', rol: 'CIUDADANO', area: null })).toBe('Ana Pérez, Ciudadano')
    expect(describirUsuario({ nombreCompleto: 'Carla Gómez', rol: 'AGENTE_MUNICIPAL', area: { nombre: 'Alumbrado' } })).toBe(
      'Carla Gómez, Agente municipal de Alumbrado',
    )
  })
})
