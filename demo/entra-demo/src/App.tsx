import { useState } from 'react';
import { useMsal } from '@azure/msal-react';
import { InteractionStatus } from '@azure/msal-browser';
import { tokenRequest } from './authConfig';
import { obtenerToken } from './token';
import { consultarApi } from './api';

export default function App() {
  const { instance, accounts, inProgress } = useMsal();
  const [salida, setSalida] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [tokenDebug, setTokenDebug] = useState('');

  const account = accounts[0];
  const bloqueado =
    ocupado || inProgress !== InteractionStatus.None;
  const apiLista = Boolean(import.meta.env.VITE_API_BASE_URL);

  async function ejecutar(action: () => Promise<void>) {
    setOcupado(true);
    setSalida('');
    try {
      await action();
    } catch (error) {
      setSalida(
        error instanceof Error ? error.message : String(error),
      );
    } finally {
      setOcupado(false);
    }
  }

  async function entrar() {
    await instance.loginPopup({
      ...tokenRequest,
      prompt: 'select_account',
    });
  }

  async function probarToken() {
    if (!account) return;
    const token = await obtenerToken(instance, account);
    if (!token.accessToken) {
      throw new Error('No se obtuvo access token');
    }
    setTokenDebug(token.accessToken);
    setSalida(
      'Token de API obtenido. Vence: ' +
        (token.expiresOn?.toLocaleString() ?? 'Consultar metadatos'),
    );
  }

  async function copiarToken() {
    await navigator.clipboard.writeText(tokenDebug);
  }

  async function consultar() {
    if (account) {
      setSalida(await consultarApi(instance, account));
    }
  }

  async function salir() {
    if (account) {
      await instance.logoutPopup({ account });
    }
  }

  return (
    <main style={{ maxWidth: 850, margin: '40px auto', padding: 20 }}>
      <h1>Demo Entra ID y Spring Boot</h1>

      {!account ? (
        <button disabled={bloqueado}
          onClick={() => void ejecutar(entrar)}>
          Iniciar sesión
        </button>
      ) : (
        <>
          <p>Sesión: {account.username}</p>
          <button disabled={bloqueado}
            onClick={() => void ejecutar(probarToken)}>
            Obtener token API
          </button>{' '}
          <button disabled={bloqueado || !apiLista}
            onClick={() => void ejecutar(consultar)}>
            Consultar API
          </button>{' '}
          <button disabled={bloqueado}
            onClick={() => void ejecutar(salir)}>
            Cerrar sesión
          </button>
        </>
      )}

      <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
        {salida}
      </pre>

      {tokenDebug && (
        <div style={{ marginTop: 20 }}>
          <p>
            <strong>Access token (solo para pruebas locales):</strong>{' '}
            <button onClick={() => void copiarToken()}>
              Copiar token
            </button>
          </p>
          <textarea
            readOnly
            value={tokenDebug}
            rows={6}
            style={{ width: '100%', fontFamily: 'monospace', fontSize: 12 }}
          />
        </div>
      )}
    </main>
  );
}
