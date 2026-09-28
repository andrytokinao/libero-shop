import { provideHttpClient, withInterceptors, withNoXsrfProtection } from '@angular/common/http';
import { ApplicationConfig, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { TitleStrategy, provideRouter, withInMemoryScrolling } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { PageTitleStrategy } from './core/services/page-title.strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideHttpClient(

      withNoXsrfProtection(),

      withInterceptors([authInterceptor, errorInterceptor]),
    ),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
    { provide: TitleStrategy, useExisting: PageTitleStrategy },
  ],
};
