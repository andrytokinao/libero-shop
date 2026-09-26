/**
 * Loading a catalogue from a spreadsheet export: what the server proposes, and what it is
 * told to do.
 *
 * <p>Two calls, and nothing kept between them. `POST /api/products/import/preview` reads the
 * file and writes nothing; the browser holds the result, the operator edits it, and
 * `POST /api/products/import/apply` sends back only the lines they ticked. That is why every
 * value of a line travels twice — the preview is a proposal, not a draft the server remembers.
 */

/** What would happen, or did happen, to one line. Mirrors ProductImportOutcome. */
export enum ProductImportOutcome {
  /** No product carried that name: a new reference. */
  CREATED = 'CREATED',
  /** The name or the barcode is already in the catalogue: the quantities add up. */
  MERGED = 'MERGED',
  /** A new reference under a numbered name, because the name was taken. */
  RENAMED = 'RENAMED',
  /** Refused, or folded into another line. `notes` says which. */
  SKIPPED = 'SKIPPED',
}

/** The decision a row carries. Mirrors ProductImportAction. */
export enum ProductImportAction {
  /** Add this quantity to an existing product, keeping its name, price and rayon. */
  MERGE = 'MERGE',
  /** Add a separate reference — what the row's "Renommer" button produces. */
  CREATE = 'CREATE',
}

/** The catalogue product a line was matched with, as much as the row needs to justify it. */
export interface ProductImportMatch {
  id: number;
  name: string;
  /** What is on the shelf now, so the row can show "40 + 12 = 52". */
  stockQuantity: number;
  unit: string | null;
  price: number;
  barcode: string | null;
  categoryPath: string | null;
}

/** One line of the file, read and confronted with the catalogue. Nothing is written yet. */
export interface ProductImportLine {
  /** 1-based line number in the file, so a complaint names the line in their spreadsheet. */
  line: number;
  name: string;
  quantity: number;
  unit: string | null;
  /** Null when the file carried no readable price — the row then asks for one. */
  price: number | null;
  barcode: string | null;
  /** The rayon as the file wrote it, verbatim. */
  categoryPath: string;
  /** The rayon that path resolved to. Null with a non-blank path means it would be created. */
  categoryId: number | null;
  outcome: ProductImportOutcome;
  action: ProductImportAction;
  existing: ProductImportMatch | null;
  /** `'barcode'` or `'name'` — a barcode is an identity, a name is a guess. */
  matchedOn: string | null;
  /** A free name for "Renommer" to start from, already numbered. */
  suggestedName: string | null;
  selected: boolean;
  /** Ready to show, in French. */
  notes: string[];
}

/** POST /api/products/import/preview — the file's text, decoded by the browser. */
export interface ProductImportPreviewRequest {
  content: string;
}

export interface ProductImportPreview {
  /** The character the file turned out to use, so the dialog can say so. */
  separator: string;
  total: number;
  created: number;
  merged: number;
  renamed: number;
  skipped: number;
  /** New products with no rayon at all — what the second dialog asks about. */
  withoutCategory: number;
  /** `"quantite : Qte stock"` per column found, so the guess can be confirmed. */
  recognised: string[];
  /** Headers no column matched. A misspelled "quantite" is read as no quantity at all. */
  ignored: string[];
  /** Recommended columns the file has none of, under their documented names. */
  missing: string[];
  /** Rayon paths the file names that the shop has not got; applying creates them. */
  createdRayons: string[];
  lines: ProductImportLine[];
}

/** One ticked line, as the operator left it. */
export interface ProductImportLineRequest {
  line: number;
  name: string;
  quantity: number;
  unit: string | null;
  price: number;
  barcode: string | null;
  categoryId: number | null;
  /** A rayon by name, created if missing. Only read when `categoryId` is null. */
  categoryPath: string | null;
  action: ProductImportAction;
  /** Required by MERGE, ignored otherwise. */
  mergeIntoId: number | null;
}

export interface ProductImportRequest {
  lines: ProductImportLineRequest[];
}

export interface ProductImportResultLine {
  line: number;
  name: string;
  outcome: ProductImportOutcome;
  /** The reference created or added to; null when the line was refused. */
  productId: number | null;
  /** Why, in French, for the refused and the renamed ones. Empty for the rest. */
  note: string;
}

export interface ProductImportResult {
  created: number;
  merged: number;
  skipped: number;
  unitsAdded: number;
  rayonsCreated: string[];
  lines: ProductImportResultLine[];
}

/** GET /api/products/import/columns — served from the server's own alias table. */
export interface ImportFormat {
  columns: { key: string; label: string; required: boolean }[];
  maxRows: number;
}

/**
 * Units offered as suggestions in the preview, never imposed.
 *
 * <p>The server stores whatever the operator wrote, so this list is a keyboard shortcut and
 * not a vocabulary: a shop selling by the "régime" of bananas types that and keeps it.
 */
export const UNIT_SUGGESTIONS = [
  'pièce',
  'kg',
  'g',
  'L',
  'cl',
  'sachet',
  'paquet',
  'carton',
  'boîte',
  'bouteille',
  'sac',
] as const;
