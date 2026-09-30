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
};

export default config;
