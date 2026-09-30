import type { CapacitorConfig } from '@capacitor/cli';

/**
 * The Android shell around the Angular app. See docs/mobile-android.md for the build.
 *
 * <p>No server address here, on purpose: one APK serves every shop, and each phone is told its
 * server at first launch (typed, or scanned from the QR code on the administrator's screen).
 * Anything written here is frozen into the APK.
 */
const config: CapacitorConfig = {
  appId: 'com.houssen.liberoshop',
  appName: 'Libero Shop',
  // The output of `ng build --configuration mobile`.
  webDir: 'dist/ui-mobile/browser',
  server: {
    // The shop's server is plain HTTP on the local network. Served from http://localhost, the
    // app's pages may call it -- REST and WebSocket alike -- without the mixed-content block an
    // https:// origin would impose. Switch both to https the day the server has a certificate,
    // and change liberoshop.security.mobile-origins to match.
    androidScheme: 'http',
    cleartext: true,
  },
  plugins: {
    // Live updates of the pages, driven by LiveUpdateService against the shop's own server.
    // Everything that would talk to Capgo's cloud is off: no automatic check against their
    // servers, no statistics sent -- the phones only ever talk to the shop's server.
    CapacitorUpdater: {
      autoUpdate: false,
      statsUrl: '',
      // A downloaded version that has not started within this time is rolled back to the
      // previous one (the pages call notifyAppReady first thing, see main.ts).
      appReadyTimeout: 15000,
      // A new APK brings newer pages than any downloaded: start again from those.
      resetWhenUpdate: true,
    },
  },
};

export default config;
