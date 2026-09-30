import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { bootstrapApplication } from '@angular/platform-browser';
import { Capacitor } from '@capacitor/core';
import { CapacitorUpdater } from '@capgo/capacitor-updater';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

// First thing, before any network call: tells the phone these pages started. Pages downloaded
// by LiveUpdateService that never get this far are rolled back to the previous ones.
if (Capacitor.isNativePlatform()) {
  void CapacitorUpdater.notifyAppReady();
}

registerLocaleData(localeFr);

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
