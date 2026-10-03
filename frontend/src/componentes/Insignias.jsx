import { ESTADOS, PRIORIDADES } from '../dominio/etiquetas.js'

export function InsigniaEstado({ estado }) {
  return <span className={`insignia estado-${estado.toLowerCase()}`}>{ESTADOS[estado] ?? estado}</span>
}

export function InsigniaPrioridad({ prioridad }) {
  return (
    <span className={`insignia prioridad-${prioridad.toLowerCase()}`}>{PRIORIDADES[prioridad] ?? prioridad}</span>
  )
}

export function InsigniaVencido({ vencido }) {
  return vencido ? <span className="insignia vencido">Vencido</span> : null
}
