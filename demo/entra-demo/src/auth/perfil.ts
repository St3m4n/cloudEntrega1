import { createContext, useContext } from 'react';
import type { Perfil } from '../tipos';

export type EstadoPerfil =
  | { tipo: 'sin-sesion' }
  | { tipo: 'cargando' }
  | { tipo: 'error'; mensaje: string }
  | { tipo: 'listo'; perfil: Perfil };

export interface ContextoPerfil {
  estado: EstadoPerfil;
  reintentar: () => void;
}

export const PerfilContext = createContext<ContextoPerfil>({
  estado: { tipo: 'sin-sesion' },
  reintentar: () => {},
});

export function usePerfil(): ContextoPerfil {
  return useContext(PerfilContext);
}

export function rolesDe(estado: EstadoPerfil): string[] {
  return estado.tipo === 'listo' ? estado.perfil.roles : [];
}
