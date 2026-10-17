/**
 * Prints a small page of its own — a receipt, a note — without the screen around it: a new
 * window holds it, the browser's print dialog opens, the window closes once printed.
 */
export function printPage(html: string): void {
  const page = window.open('', '_blank', 'width=640,height=720');
  if (!page) {
    // A blocked pop-up: nothing to print into.
    return;
  }
  page.document.open();
  page.document.write(html);
  page.document.close();
  page.focus();
  page.onafterprint = () => page.close();
  page.print();
}
