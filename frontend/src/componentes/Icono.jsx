const TRAZOS = {
  reclamos: 'M8 5H6a2 2 0 0 0-2 2v13h16V7a2 2 0 0 0-2-2h-2M8 3h8v4H8zM8 12h8M8 16h5',
  crear: 'M12 5v14M5 12h14',
  avisos: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4',
  tablero: 'M4 20V10h4v10M10 20V4h4v16M16 20v-7h4v7',
  administracion: 'M4 7h16M4 17h16M8 4v6M16 14v6',
}

export default function Icono({ nombre, className = '' }) {
  return (
    <svg className={`icono ${className}`} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path d={TRAZOS[nombre] ?? TRAZOS.reclamos} stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
