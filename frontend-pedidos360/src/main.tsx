import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { PublicClientApplication } from '@azure/msal-browser';
import { MsalProvider } from '@azure/msal-react';

import { msalConfig } from './authConfig';
import { PerfilProvider } from './auth/PerfilProvider';
import App from './App';
import './index.css';

const msal = new PublicClientApplication(msalConfig);

const rootElement = document.getElementById('root');

if (!rootElement) {
  throw new Error('Root element not found');
}

createRoot(rootElement).render(
  <MsalProvider instance={msal}>
    <BrowserRouter>
      <PerfilProvider>
        <App />
      </PerfilProvider>
    </BrowserRouter>
  </MsalProvider>,
);
