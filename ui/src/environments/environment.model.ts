/**
 * What is true of every installation of a build — never of one shop.
 *
 * <p>The address of a shop's server does not belong here: whatever an environment file says is
 * frozen into the build, and one build serves every shop. It is configured at run time, in
 * `ServerConfig`.
 */
export interface Environment {
  platform: 'web' | 'mobile';
  /** Whether the app must be told a server before anyone can sign in. */
  requiresServerSetup: boolean;
}
