import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useMsal } from '@azure/msal-react';
import { InteractionStatus } from '@azure/msal-browser';
import { useCuenta } from '../bff';
import { Aviso, Cargando } from '../components/ui';
import { usePerfil } from './perfil';
import { NOMBRE_ROL, tieneAlgunRol } from './roles';

export function RequiereSesion({ children }: { children: ReactNode }) {
  const { inProgress } = useMsal();
  const cuenta = useCuenta();
  const location = useLocation();

  if (!cuenta) {
    if (
      inProgress === InteractionStatus.Startup ||
      inProgress === InteractionStatus.HandleRedirect
    ) {
      return <Cargando texto="Verificando sesión..." />;
    }
    return <Navigate to="/login" replace state={{ desde: location.pathname }} />;
  }
  return children;
}

export function RequiereRol({
  roles,
  children,
}: {
  roles?: readonly string[];
  children: ReactNode;
}) {
  const { estado, reintentar } = usePerfil();

  if (!roles) return children;

  switch (estado.tipo) {
    case 'sin-sesion':
    case 'cargando':
      return <Cargando texto="Cargando permisos..." />;
    case 'error':
      return (
        <Aviso tipo="error" titulo="No se pudieron obtener tus permisos">
          <p>{estado.mensaje}</p>
          <button className="boton secundario" onClick={reintentar}>
            Reintentar
          </button>
        </Aviso>
      );
    case 'listo':
      if (tieneAlgunRol(estado.perfil.roles, roles)) return children;
      return (
        <Aviso tipo="error" titulo="Acceso denegado">
          <p>
            Esta sección requiere uno de estos roles:{' '}
            {roles.map((r) => NOMBRE_ROL[r] ?? r).join(', ')}.
          </p>
        </Aviso>
      );
  }
}
