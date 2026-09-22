import { useState } from 'react';
import { useMsal } from '@azure/msal-react';
import { InteractionStatus } from '@azure/msal-browser';

import { tokenRequest } from './authConfig';
import { obtenerToken } from './token';
import { consultarApi } from './api';

export default function App() {
  const { instance, accounts, inProgress } = useMsal();

  const [salida, setSalida] = useState('');
  const [salidaParte2, setSalidaParte2] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const [tokenDebug, setTokenDebug] = useState('');

  const account = instance.getActiveAccount() ?? accounts[0];
  const bloqueado =
    ocupado || inProgress !== InteractionStatus.None;

  const apiLista =
    Boolean(import.meta.env.VITE_API_BASE_URL);
  const bffListo =
    Boolean(import.meta.env.VITE_BFF_BASE_URL);

  async function ejecutar(action: () => Promise<void>) {
    setOcupado(true);
    setSalida('');
    try {
      await action();
    } catch (error) {
      setSalida(
        error instanceof Error ? error.message : String(error)
      );
    } finally {
      setOcupado(false);
    }
  }

  async function entrar() {
    const result = await instance.loginPopup({
      ...tokenRequest,
      prompt: 'select_account',
    });
    instance.setActiveAccount(result.account);
  }

  // PARTE 1
  async function probarToken() {
    if (!account) return;

    const token = await obtenerToken(instance, account);

    if (!token.accessToken) {
      throw new Error('No se obtuvo access token');
    }

    setTokenDebug(token.accessToken);
    setSalida(
      'Token de API obtenido. Vence: ' +
        (token.expiresOn?.toLocaleString() ??
          'Consultar metadatos')
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

  // PARTE 2
  async function consultarBff() {
    if (!account) return;

    setOcupado(true);
    setSalidaParte2('');

    try {
      const token = await obtenerToken(instance, account);

      if (!token.accessToken) {
        throw new Error('No se obtuvo access token');
      }

      const baseUrl = import.meta.env.VITE_BFF_BASE_URL;

      if (!baseUrl) {
        throw new Error(
          'VITE_BFF_BASE_URL no está configurada'
        );
      }

      const response = await fetch(
        `${baseUrl}/api/data`,
        {
          method: 'GET',
          headers: {
            Authorization: `Bearer ${token.accessToken}`,
          },
        }
      );

      const texto = await response.text();

      if (!response.ok) {
        throw new Error(
          `Error BFF ${response.status}: ${texto}`
        );
      }

      const data = JSON.parse(texto);

      setSalidaParte2(
        JSON.stringify(data, null, 2)
      );
    } catch (error) {
      setSalidaParte2(
        error instanceof Error ? error.message : String(error)
      );
    } finally {
      setOcupado(false);
    }
  }

  async function salir() {
    if (account) {
      await instance.logoutPopup({ account });
    }
  }

  return (
    <main style={{
      maxWidth: 850,
      margin: '40px auto',
      fontFamily: 'Arial',
      padding: 20
    }}>
      <h1>Demo Entra ID, BFF y Microservicios</h1>
      <p>Estado MSAL: {inProgress}</p>
      <p>Ocupado: {String(ocupado)}</p>

      {!account ? (
        <button
          disabled={bloqueado}
          onClick={() => void ejecutar(entrar)}
        >
          Iniciar sesión
        </button>
      ) : (
        <>
          <p>Sesión: {account.username}</p>

          <hr />
          <h2>Comprobación Parte 1</h2>
          <p>
            Autenticación con Microsoft Entra ID
            y acceso a la API protegida.
          </p>

          <button
            disabled={bloqueado}
            onClick={() => void ejecutar(probarToken)}
          >
            Obtener token API
          </button>{' '}

          <button
            disabled={bloqueado || !apiLista}
            onClick={() => void ejecutar(consultar)}
          >
            Consultar API
          </button>

          <pre style={{
            whiteSpace: 'pre-wrap',
            overflowWrap: 'anywhere',
            backgroundColor: '#f5f5f5',
            padding: 15,
            marginTop: 15
          }}>
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

          <hr />
          <h2>Comprobación Parte 2</h2>
          <p>
            Flujo completo React -&gt; BFF -&gt; ms-clientes.
          </p>

          <button
            disabled={bloqueado || !bffListo}
            onClick={() => void consultarBff()}
          >
            Consultar BFF + ms-clientes
          </button>

          <pre style={{
            whiteSpace: 'pre-wrap',
            overflowWrap: 'anywhere',
            backgroundColor: '#f5f5f5',
            padding: 15,
            marginTop: 15
          }}>
            {salidaParte2}
          </pre>

          <hr />

          <button
            disabled={bloqueado}
            onClick={() => void ejecutar(salir)}
          >
            Cerrar sesión
          </button>
        </>
      )}
    </main>
  );
}
