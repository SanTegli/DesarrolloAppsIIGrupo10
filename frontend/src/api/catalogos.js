import { pedir } from './cliente.js'

// Consultas de apoyo. No llevan X-Usuario-Id: el selector de usuario las necesita antes de elegir uno.

export const listarUsuarios = () => pedir('/usuarios')
export const listarCategorias = () => pedir('/categorias')
export const listarBarrios = () => pedir('/barrios')
export const listarAreas = () => pedir('/areas')
