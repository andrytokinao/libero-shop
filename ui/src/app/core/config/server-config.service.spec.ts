import { ServerConfig } from './server-config.service';

/**
 * How an address typed on a phone keyboard, or read from a QR code, becomes the one the app
 * keeps. A mistake here is a phone that cannot find its server, with nothing on screen to say why.
 */
describe('ServerConfig address handling', () => {
  it('keeps only scheme, host and port, whatever was typed around them', () => {
    expect(ServerConfig.normalise('192.168.1.10:8080')).toBe('http://192.168.1.10:8080');
    expect(ServerConfig.normalise('  http://boutique.local/ ')).toBe('http://boutique.local');
    expect(ServerConfig.normalise('http://192.168.1.10:8080/api/auth/session'))
      .toBe('http://192.168.1.10:8080');
    expect(ServerConfig.normalise('https://shop.example:8443/caisse')).toBe('https://shop.example:8443');
  });

  it('refuses what is not an address, with a message to show', () => {
    expect(() => ServerConfig.normalise('')).toThrowError(/Saisissez/);
    expect(() => ServerConfig.normalise('http://')).toThrowError(/pas une adresse/);
  });

  it('reads the connection code of the administrator screen', () => {
    expect(ServerConfig.fromScan('liberoshop://connect?server=http%3A%2F%2F192.168.1.10%3A8080'))
      .toBe('http://192.168.1.10:8080');
  });

  it('accepts a plain web address, and refuses any other QR code', () => {
    expect(ServerConfig.fromScan('http://192.168.1.10:8080')).toBe('http://192.168.1.10:8080');
    expect(() => ServerConfig.fromScan('WIFI:S:Boutique;T:WPA;P:secret;;'))
      .toThrowError(/pas un code de connexion/);
  });
});
