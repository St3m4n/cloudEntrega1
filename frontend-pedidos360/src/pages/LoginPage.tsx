import { useState } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useMsal } from '@azure/msal-react';
import { InteractionStatus } from '@azure/msal-browser';
import { tokenRequest } from '../authConfig';
import { mensajeDe, useCuenta } from '../bff';

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
      <div className="tarjeta login-tarjeta">
        <span className="marca marca-grande">Pedidos360</span>
        <p className="subtitulo">
          Plataforma de pedidos y despacho para la red de panaderías y cafés.
        </p>
        <button
          className="boton primario boton-grande"
          disabled={inProgress !== InteractionStatus.None}
          onClick={() => void entrar()}
        >
          Iniciar sesión con Microsoft
        </button>
        {error && <p className="texto-error">{error}</p>}
        <p className="nota">
          Acceso con cuenta corporativa de Microsoft Entra ID. Tus permisos dependen del rol
          asignado: Administrador, Operador o Cliente.
        </p>
      </div>
    </div>
  );
}
