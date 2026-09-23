import { NavLink, Outlet, useNavigate } from 'react-router';
import { useMsal } from '@azure/msal-react';
import { rolesDe, usePerfil } from '../auth/perfil';
import { NOMBRE_ROL, tieneAlgunRol } from '../auth/roles';
import { useCuenta } from '../bff';
import { SECCIONES } from '../rutas';

export function Layout() {
  const { instance } = useMsal();
  const cuenta = useCuenta();
  const navigate = useNavigate();
  const roles = rolesDe(usePerfil().estado);

  const visibles = SECCIONES.filter((s) => !s.roles || tieneAlgunRol(roles, s.roles));

  async function salir() {
    if (!cuenta) return;
    await instance.logoutPopup({ account: cuenta });
    navigate('/login', { replace: true });
  }

  return (
    <div className="app">
      <header className="cabecera">
        <span className="marca">Pedidos360</span>
        <div className="usuario">
          <span className="usuario-nombre">{cuenta?.name ?? cuenta?.username}</span>
          {roles.map((rol) => (
            <span key={rol} className="chip">
              {NOMBRE_ROL[rol] ?? rol}
            </span>
          ))}
          <button className="boton secundario" onClick={() => void salir().catch(console.error)}>
            Cerrar sesión
          </button>
        </div>
      </header>
      <div className="cuerpo">
        <nav className="menu" aria-label="Menú principal">
          {visibles.map((s) => (
            <NavLink key={s.ruta} to={s.ruta} className="menu-enlace">
              {s.titulo}
            </NavLink>
          ))}
        </nav>
        <main className="contenido">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
