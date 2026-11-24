import { provideHttpClient } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';

export const appConfig: ApplicationConfig = {
  // HttpClient copies Spring's XSRF-TOKEN cookie into the request header by default.
  providers: [provideBrowserGlobalErrorListeners(), provideHttpClient()],
};
