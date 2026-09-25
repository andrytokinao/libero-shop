/**
 * Base path of the REST API.
 *
 * <p>Relative on purpose, and that is load-bearing: Angular's XSRF interceptor refuses to
 * attach the token to an absolute URL, so an `http://localhost:8080` base would silently
 * break every write with a 403. In development `proxy.conf.json` forwards `/api` to the
 * backend; in production the Angular bundle is served by that same backend, so the path
 * resolves on its own.
 */
export const API_BASE_URL = '/api';
