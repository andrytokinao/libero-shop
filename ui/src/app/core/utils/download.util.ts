/**
 * Hands a file the app fetched itself to the browser's "save as".
 *
 * <p>Needed for anything behind the API's bearer token: a plain link cannot carry the header,
 * so the file is fetched as a blob and saved from memory.
 */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // After the click has been handled: revoking at once can cancel the download in some browsers.
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
