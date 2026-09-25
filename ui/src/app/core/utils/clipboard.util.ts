/**
 * Copies text, and says whether it worked.
 *
 * <p>The clipboard API only exists in a secure context, which a shop running the backend
 * over plain http on the local network is not. Rather than throw there, this answers
 * false so the caller can tell the user to select the value by hand — the fingerprint and
 * the renewal code are both short enough to be read off the screen.
 */
export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
