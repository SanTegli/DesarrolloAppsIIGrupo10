import { pedir } from './cliente.js'

// Catálogos públicos: se pueden pedir antes de iniciar sesión.

export const listarCategorias = () => pedir('/categorias')
export const listarBarrios = () => pedir('/barrios')
export const listarAreas = () => pedir('/areas')
