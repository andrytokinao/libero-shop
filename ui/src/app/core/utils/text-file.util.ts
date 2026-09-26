/** What a spreadsheet export is likely to be encoded in, tried in this order. */
const FALLBACK_ENCODINGS = ['windows-1252', 'iso-8859-1'] as const;

/**
 * Reads a file the operator picked, as text, guessing its encoding.
 *
 * <p>This has to happen here rather than on the server, and the reason is Excel. Saving a
 * sheet as "CSV" on a French Windows writes Windows-1252, not UTF-8 — so the bytes for "Café"
 * are not valid UTF-8 at all. A server handed those bytes can only guess, and a wrong guess
 * puts "CafÃ©" in the catalogue permanently. The browser, on the other hand, can simply try:
 * `TextDecoder` with `fatal: true` throws on a byte sequence that is not UTF-8, and that
 * failure is the signal to fall back.
 *
 * <p>UTF-8 first because it is both the modern default and the strict one — a Windows-1252
 * file almost always fails it, while a UTF-8 file decoded as Windows-1252 would silently
 * produce mojibake with nothing to catch. Trying the forgiving encoding last is what makes the
 * test meaningful.
 *
 * @throws Error when the file cannot be read at all, or decodes under no known encoding
 */
export async function readTextFile(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer());

  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    // Not UTF-8. Expected often enough that it is not worth reporting on its own.
  }

  for (const encoding of FALLBACK_ENCODINGS) {
    try {
      // Not fatal: these single-byte encodings map every byte to something, which is exactly
      // why they are the fallback and not the first attempt.
      return new TextDecoder(encoding).decode(bytes);
    } catch {
      // An encoding this browser does not know. Try the next.
    }
  }

  throw new Error(
    `Impossible de lire « ${file.name} » : encodage non reconnu. ` +
      'Réenregistrez le fichier en CSV UTF-8 depuis votre tableur.',
  );
}

/** Extensions offered in the file picker — what a spreadsheet exports as delimited text. */
export const TEXT_FILE_ACCEPT = '.csv,.txt,.tsv,text/csv,text/plain,text/tab-separated-values';
