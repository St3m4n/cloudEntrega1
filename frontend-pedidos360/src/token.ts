import { InteractionRequiredAuthError } from '@azure/msal-browser';
import type {
  AccountInfo,
  IPublicClientApplication,
} from '@azure/msal-browser';
import { tokenRequest } from './authConfig';

export async function obtenerToken(
  instance: IPublicClientApplication,
  account: AccountInfo,
) {
  const request = { ...tokenRequest, account };

  const result = await instance
    .acquireTokenSilent(request)
    .catch((error: unknown) => {
      if (error instanceof InteractionRequiredAuthError) {
        return instance.acquireTokenPopup(request);
      }
      throw error;
    });

  return result;
}
