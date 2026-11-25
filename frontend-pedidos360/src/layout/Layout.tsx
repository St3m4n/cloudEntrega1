import { NavLink, Outlet, useLocation, useNavigate } from 'react-router';
import { useMsal } from '@azure/msal-react';
import { rolesDe, usePerfil } from '../auth/perfil';
import { NOMBRE_ROL, tieneAlgunRol } from '../auth/roles';
import { useCuenta } from '../bff';
import { Icono, Logo } from '../components/Icono';
import { SECCIONES } from '../rutas';

function iniciales(nombre: string): string {
  return nombre
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('');
}

export function Layout() {
  const { instance } = useMsal();
  const cuenta = useCuenta();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const roles = rolesDe(usePerfil().estado);

  const visibles = SECCIONES.filter((s) => !s.roles || tieneAlgunRol(roles, s.roles));
  const actual = SECCIONES.find((s) => pathname.startsWith(s.ruta));
  const nombre = cuenta?.name ?? cuenta?.username ?? '';

  async function salir() {
    if (!cuenta) return;
    await instance.logoutPopup({
      account: cuenta,
      // redirect.html relays the popup completion to this window and lets
      // MSAL close the popup instead of leaving the application open there.
      mainWindowRedirectUri: window.location.origin + '/login',
    });
    navigate('/login', { replace: true });
  }

  return (
    <div className="app">
      <aside className="lateral">
        <div className="marca">
          <Logo />
          <div>
            <span className="marca-nombre">Pedidos360</span>
            <span className="marca-lema">Panaderías y cafés</span>
          </div>
        </div>
        <nav className="menu" aria-label="Menú principal">
          <span className="menu-grupo">Navegación</span>
          {visibles.map((s) => (
            <NavLink key={s.ruta} to={s.ruta} className="menu-enlace">
              <Icono nombre={s.icono} />
              <span>{s.titulo}</span>
            </NavLink>
          ))}
        </nav>
        <div className="lateral-pie">
          <span className="punto-vivo" aria-hidden="true" />
          AWS · us-east-1
        </div>
      </aside>
      <div className="principal">
        <header className="cabecera">
          <span className="cabecera-titulo">{actual?.titulo ?? ''}</span>
          <div className="usuario">
            <div className="usuario-roles">
              {roles.map((rol) => (
                <span key={rol} className="chip">
                  {NOMBRE_ROL[rol] ?? rol}
                </span>
              ))}
            </div>
            <span className="avatar" title={nombre} aria-hidden="true">{iniciales(nombre)}</span>
            <span className="usuario-nombre">{nombre}</span>
            <button className="boton secundario" onClick={() => void salir().catch(console.error)}>
              <Icono nombre="salir" />
              <span className="ocultar-movil">Cerrar sesión</span>
            </button>
          </div>
        </header>
        <main className="contenido">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
