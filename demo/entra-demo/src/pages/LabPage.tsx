import { useState } from 'react';
import { useMsal } from '@azure/msal-react';
import { consultarApi } from '../api';
import { mensajeDe, useBff, useCuenta } from '../bff';
import { rolesDe, usePerfil } from '../auth/perfil';
import { EncabezadoPagina } from '../components/ui';
import { obtenerToken } from '../token';

export function LabPage() {
  const { instance } = useMsal();
  const cuenta = useCuenta();
  const perfil = usePerfil();
  const llamar = useBff();

  const [salida, setSalida] = useState('');
  const [salidaParte2, setSalidaParte2] = useState('');
  const [tokenDebug, setTokenDebug] = useState('');
  const [ocupado, setOcupado] = useState(false);

  const apiLista = Boolean(import.meta.env.VITE_API_BASE_URL);
  const bffListo = Boolean(import.meta.env.VITE_BFF_BASE_URL);
  const rolesAplicados = rolesDe(perfil.estado);

  async function ejecutar(accion: () => Promise<string>, destino: (texto: string) => void) {
    setOcupado(true);
    destino('');
    try {
      destino(await accion());
    } catch (error) {
      destino(mensajeDe(error));
    } finally {
      setOcupado(false);
    }
  }

  async function probarToken() {
    if (!cuenta) return '';
    const token = await obtenerToken(instance, cuenta);
    setTokenDebug(token.accessToken);
    return (
      'Token de API obtenido. Vence: ' +
      (token.expiresOn?.toLocaleString() ?? 'Consultar metadatos')
    );
  }

  async function consultar() {
    return cuenta ? consultarApi(instance, cuenta) : '';
  }

  async function consultarBff() {
    return JSON.stringify(await llamar('/api/data'), null, 2);
  }

  return (
    <>
      <EncabezadoPagina
        titulo="Laboratorio"
        descripcion="Comprobaciones de las guías Parte 1 (Entra ID) y Parte 2 (BFF + microservicios)."
      />

      {perfil.estado.tipo === 'listo' && (
        <section className="seccion tarjeta">
          <h2>Perfil recibido desde Entra ID</h2>
          <p>Usuario: {perfil.estado.perfil.usuario ?? 'No informado'}</p>
          <p>
            Roles originales:{' '}
            {perfil.estado.perfil.roles.length > 0
              ? perfil.estado.perfil.roles.join(', ')
              : 'ninguno'}
          </p>
          <p>
            Perfil aplicado:{' '}
            {rolesAplicados.join(', ') || 'sin perfil'}
            {perfil.estado.perfil.roles.length === 0 && ' (por defecto)'}
          </p>
        </section>
      )}

      <section className="seccion tarjeta">
        <h2>Comprobación Parte 1</h2>
        <p>Autenticación con Microsoft Entra ID y acceso a la API protegida.</p>
        <div className="botonera">
          <button
            className="boton secundario"
            disabled={ocupado}
            onClick={() => void ejecutar(probarToken, setSalida)}
          >
            Obtener token API
          </button>
          <button
            className="boton secundario"
            disabled={ocupado || !apiLista}
            onClick={() => void ejecutar(consultar, setSalida)}
          >
            Consultar API
          </button>
        </div>
        <pre className="salida">{salida}</pre>

        {tokenDebug && (
          <div>
            <p>
              <strong>Access token (solo para pruebas locales):</strong>{' '}
              <button
                className="boton secundario"
                onClick={() => void navigator.clipboard.writeText(tokenDebug)}
              >
                Copiar token
              </button>
            </p>
            <textarea className="token" readOnly value={tokenDebug} rows={6} />
          </div>
        )}
      </section>

      <section className="seccion tarjeta">
        <h2>Comprobación Parte 2</h2>
        <p>Flujo completo React → BFF → ms-clientes.</p>
        <button
          className="boton secundario"
          disabled={ocupado || !bffListo}
          onClick={() => void ejecutar(consultarBff, setSalidaParte2)}
        >
          Consultar BFF + ms-clientes
        </button>
        <pre className="salida">{salidaParte2}</pre>
      </section>
    </>
  );
}
