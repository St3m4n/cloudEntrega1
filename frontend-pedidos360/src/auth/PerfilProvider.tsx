import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useMsal } from '@azure/msal-react';
import { InteractionStatus } from '@azure/msal-browser';
import { mensajeDe, useBff, useCuenta } from '../bff';
import type { Perfil } from '../tipos';
import { PerfilContext } from './perfil';
import type { EstadoPerfil } from './perfil';

interface Resultado {
  clave: string;
  intento: number;
  perfil?: Perfil;
  error?: string;
}

// Los roles vienen del access token y los expone el BFF en /api/me,
// asi el frontend no necesita decodificar el token.
export function PerfilProvider({ children }: { children: ReactNode }) {
  const { inProgress } = useMsal();
  const clave = useCuenta()?.homeAccountId;
  const llamar = useBff();
  const [intento, setIntento] = useState(0);
  const [resultado, setResultado] = useState<Resultado | null>(null);

  useEffect(() => {
    if (!clave || inProgress !== InteractionStatus.None) return;

    let cancelado = false;
    llamar<Perfil>('/api/me').then(
      (perfil) => {
        if (!cancelado) setResultado({ clave, intento, perfil });
      },
      (error: unknown) => {
        if (!cancelado) setResultado({ clave, intento, error: mensajeDe(error) });
      },
    );
    return () => {
      cancelado = true;
    };
  }, [clave, inProgress, llamar, intento]);

  const reintentar = useCallback(() => setIntento((n) => n + 1), []);

  let estado: EstadoPerfil;
  if (!clave) {
    estado = { tipo: 'sin-sesion' };
  } else if (!resultado || resultado.clave !== clave || resultado.intento !== intento) {
    estado = { tipo: 'cargando' };
  } else if (resultado.perfil) {
    estado = { tipo: 'listo', perfil: resultado.perfil };
  } else {
    estado = { tipo: 'error', mensaje: resultado.error ?? 'Error desconocido' };
  }

  return (
    <PerfilContext.Provider value={{ estado, reintentar }}>
      {children}
    </PerfilContext.Provider>
  );
}
