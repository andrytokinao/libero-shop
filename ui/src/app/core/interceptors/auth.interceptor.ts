import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { API_BASE_URL } from '../api/api.config';
import { TokenStorage } from '../services/token-storage.service';

/**
 * Endpoints that must work without a token — and that a stale one would break.
 *
 * <p>The backend refuses an invalid bearer token in a filter, before the handler is ever
 * reached, so attaching a leftover token to the sign-in call would answer 401 and leave
 * the user unable to sign in again. That is not hypothetical: a backend restarted without
 * a configured secret signs with a new key, and every token in every browser becomes
 * unreadable while its stored expiry still says it is good.
 */
const TOKEN_FREE_PATHS = [`${API_BASE_URL}/auth/login`, `${API_BASE_URL}/auth/logout`];

/**
 * Attaches the access token to the API calls that need it.
 *
 * <p>Scoped to {@link API_BASE_URL}: a request for an asset or for any other host must
 * never carry the token, which would hand the credentials of the till to whoever answers.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(TokenStorage).token();

  const needsToken =
    token !== null &&
    request.url.startsWith(API_BASE_URL) &&
    !TOKEN_FREE_PATHS.some((path) => request.url.startsWith(path));

  return needsToken
    ? next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }))
    : next(request);
};
