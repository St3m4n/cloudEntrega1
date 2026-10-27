import { Navigate, Route, Routes } from 'react-router';
import { RequiereRol, RequiereSesion } from './auth/Protegido';
import { Layout } from './layout/Layout';
import { LoginPage } from './pages/LoginPage';
import { SECCIONES } from './rutas';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequiereSesion>
            <Layout />
          </RequiereSesion>
        }
      >
        {SECCIONES.map(({ ruta, roles, Pagina }) => (
          <Route
            key={ruta}
            path={ruta}
            element={
              <RequiereRol roles={roles}>
                <Pagina />
              </RequiereRol>
            }
          />
        ))}
      </Route>
      <Route path="*" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}
