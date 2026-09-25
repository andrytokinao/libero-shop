/**
 * Base path of the REST API.
 *
 * <p>Relative on purpose. In development `proxy.conf.json` forwards `/api` to the backend;
 * in production the Angular bundle is served by that same backend, so the path resolves on
 * its own — no origin to configure, and no cross-origin request to allow.
 *
 * <p>It is also what `authInterceptor` matches on to decide whether a request may carry
 * the access token, so pointing this at an absolute URL would mean rethinking that rule
 * rather than just editing a string.
 */
export const API_BASE_URL = '/api';
