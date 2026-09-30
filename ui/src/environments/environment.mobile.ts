import { Environment } from './environment.model';

/**
 * The mobile build (`npm run build:mobile`): the pages are bundled in the APK, so the server is
 * somewhere else and each phone has to be told where — at first launch, then in the settings.
 */
export const environment: Environment = {
  platform: 'mobile',
  requiresServerSetup: true,
};
