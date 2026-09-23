import { useCallback } from 'react';
import { useMsal } from '@azure/msal-react';
import type {
  AccountInfo,
  IPublicClientApplication,
} from '@azure/msal-browser';
import { obtenerToken } from './token';

export type LlamarBff = <T>(ruta: string, init?: RequestInit) => Promise<T>;

const MENSAJES: Record<number, string> = {
  401: 'Tu sesión no es válida o expiró. Cierra sesión y vuelve a entrar.',
  403: 'No tienes permisos para realizar esta operación.',
  404: 'El recurso solicitado no existe.',
  503: 'El microservicio no está disponible. Verifica que esté en ejecución.',
};

export function mensajeDe(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

async function llamarBff<T>(
  instance: IPublicClientApplication,
  cuenta: AccountInfo,
  ruta: string,
  init: RequestInit = {},
): Promise<T> {
  const base = import.meta.env.VITE_BFF_BASE_URL?.replace(/\/$/, '');
  if (!base) {
    throw new Error(
      'VITE_BFF_BASE_URL no está configurada. Revisa .env.local y reinicia Vite.',
    );
  }

  const { accessToken } = await obtenerToken(instance, cuenta);
  const headers = new Headers(init.headers);
  headers.set('Authorization', `Bearer ${accessToken}`);
  if (init.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }

  let respuesta: Response;
  try {
    respuesta = await fetch(`${base}${ruta}`, { ...init, headers });
  } catch {
    throw new Error(
      `No se pudo conectar con el BFF en ${base}. Verifica que esté en ejecución.`,
    );
  }

  if (!respuesta.ok) {
    const detalle = await respuesta.text();
    throw new Error(
      MENSAJES[respuesta.status] ??
        `Error ${respuesta.status}: ${detalle || respuesta.statusText}`,
    );
  }

  if (respuesta.status === 204) {
    return undefined as T;
  }
  return (await respuesta.json()) as T;
}

export function useCuenta(): AccountInfo | undefined {
  const { instance, accounts } = useMsal();
  return instance.getActiveAccount() ?? accounts[0];
}

export function useBff(): LlamarBff {
  const { instance, accounts } = useMsal();

  return useCallback(
    <T>(ruta: string, init?: RequestInit) => {
      const cuenta = instance.getActiveAccount() ?? accounts[0];
      if (!cuenta) {
        return Promise.reject(new Error('No hay una sesión activa.'));
      }
      return llamarBff<T>(instance, cuenta, ruta, init);
    },
    [instance, accounts],
  );
}
