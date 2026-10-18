/**
 * Shows a PDF the server produced: in a new tab, from where it is printed or shared; or, when
 * the browser refuses the tab (a pop-up blocker, a phone's web view), saved as a file instead.
 */
export function showPdf(pdf: Blob, fileName: string): void {
  const url = URL.createObjectURL(pdf);
  const tab = window.open(url, '_blank');
  if (!tab) {
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
  }
  // Long enough for the tab or the download to have read it.
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
