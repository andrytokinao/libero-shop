import { Environment } from './environment.model';

/**
 * The web build, served by the backend jar: the pages and the API share an origin, so there is
 * no server address to know. The mobile build replaces this file with `environment.mobile.ts`
 * (the `mobile` configuration in angular.json).
 */
export const environment: Environment = {
  platform: 'web',
  requiresServerSetup: false,
};
