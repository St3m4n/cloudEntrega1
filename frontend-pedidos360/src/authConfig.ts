import type { Configuration } from '@azure/msal-browser';

export const msalConfig: Configuration = {
  auth: {
    clientId: import.meta.env.VITE_SPA_CLIENT_ID,
    authority:
      'https://login.microsoftonline.com/' +
      import.meta.env.VITE_ENTRA_TENANT_ID,
    redirectUri:
      window.location.origin + '/redirect.html',
    // The popup logout must return through the MSAL bridge so it can notify
    // the main window and close itself. The main window redirect is supplied
    // by Layout after the server-side sign-out completes.
    postLogoutRedirectUri:
      window.location.origin + '/redirect.html',
  },
  cache: {
    cacheLocation: 'sessionStorage',
  },
};

export const tokenRequest = {
  scopes: [
    `api://${import.meta.env.VITE_API_CLIENT_ID}/access_as_user`,
  ],
};
