import { useState } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useMsal } from '@azure/msal-react';
import { InteractionStatus } from '@azure/msal-browser';
import { tokenRequest } from '../authConfig';
import { mensajeDe, useCuenta } from '../bff';
import { Icono, Logo } from '../components/Icono';

export function LoginPage() {
  const { instance, inProgress } = useMsal();
  const cuenta = useCuenta();
  const location = useLocation();
  const [error, setError] = useState('');

  const destino = (location.state as { desde?: string } | null)?.desde ?? '/dashboard';

  if (cuenta) {
    return <Navigate to={destino} replace />;
  }

  async function entrar() {
    setError('');
    try {
      const resultado = await instance.loginPopup({
        ...tokenRequest,
        prompt: 'select_account',
      });
      instance.setActiveAccount(resultado.account);
    } catch (e) {
      setError(mensajeDe(e));
    }
  }

  return (
    <div className="login">
      <section className="login-hero">
        <div className="marca">
          <Logo tamano={40} />
          <div>
            <span className="marca-nombre">Pedidos360</span>
            <span className="marca-lema">Panaderías y cafés</span>
          </div>
        </div>
        <div>
          <h1>Pedidos y despacho en tiempo real</h1>
          <p>Plataforma de pedidos para la red de panaderías y cafés, desplegada en AWS con microservicios.</p>
        </div>
        <ul className="login-puntos">
          <li>
            <span className="icono-caja"><Icono nombre="pedidos" /></span>
            <div><strong>Pedidos de punta a punta</strong><span>Creación, preparación, despacho y entrega con trazabilidad.</span></div>
          </li>
          <li>
            <span className="icono-caja"><Icono nombre="kafka" /></span>
            <div><strong>Eventos con Kafka y RabbitMQ</strong><span>Auditoría y reportes alimentados por eventos.</span></div>
          </li>
          <li>
            <span className="icono-caja"><Icono nombre="auditoria" /></span>
            <div><strong>Acceso con Microsoft Entra ID</strong><span>Permisos según el rol de cada usuario.</span></div>
          </li>
        </ul>
      </section>
      <div className="login-lado">
        <div className="tarjeta login-tarjeta">
          <div>
            <h2>Iniciar sesión</h2>
            <p className="subtitulo">Usa tu cuenta corporativa de Microsoft.</p>
          </div>
          <button
            className="boton primario boton-grande"
            disabled={inProgress !== InteractionStatus.None}
            onClick={() => void entrar()}
          >
            <Icono nombre="usuario" />
            Iniciar sesión con Microsoft
          </button>
          {error && <p className="texto-error">{error}</p>}
          <p className="nota">
            Tus permisos dependen del rol asignado en Entra ID: Administrador, Operador o Cliente.
          </p>
        </div>
      </div>
    </div>
  );
}
