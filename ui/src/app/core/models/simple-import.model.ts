import { ImportAction, ImportOutcome } from './product-import.model';

/**
 * Importing something that is only a few text columns wide.
 *
 * <p>Rayons and suppliers share this shape, and products deliberately do not: a product line
 * carries decisions nothing else has — top up a stock or add a second reference, a price, a rayon
 * — and flattening those into a bag of strings would cost the type safety that makes the product
 * dialog readable. What is left once you take those away really is "a name and two other fields",
 * twice over.
 *
 * <p>So the values travel as a record keyed by field, and the shape of that record travels with
 * them in `fields`. That is what lets one dialog serve both: it renders a column per spec rather
 * than a column per hard-coded name.
 */

/** One column of the file, as the dialog should render it. */
export interface ImportFieldSpec {
  /** How the value is keyed in `SimpleImportLine.values`. */
  key: string;
  /** The recommended header, and the column heading shown. */
  label: string;
  /** A line with this value blank cannot be imported. */
  required: boolean;
  /** What the column holds; the input is capped at it rather than truncated server-side. */
  maxLength: number;
}

export interface SimpleImportLine {
  line: number;
  /** Keyed by `ImportFieldSpec.key`. A key the file had no column for is present and empty. */
  values: Record<string, string>;
  outcome: ImportOutcome;
  action: ImportAction;
  existingId: number | null;
  /** How the match reads, so the row can justify itself on screen. */
  existingLabel: string | null;
  selected: boolean;
  /** Ready to show, in French. */
  notes: string[];
}

export interface SimpleImportPreview {
  /** `'categories'` or `'suppliers'` — used for wording, never for behaviour. */
  resource: string;
  separator: string;
  total: number;
  created: number;
  /** Lines that would complete something already recorded rather than add to it. */
  merged: number;
  skipped: number;
  fields: ImportFieldSpec[];
  recognised: string[];
  ignored: string[];
  missing: string[];
  lines: SimpleImportLine[];
}

export interface SimpleImportLineRequest {
  line: number;
  values: Record<string, string>;
  action: ImportAction;
  /** Required by MERGE, ignored otherwise. */
  mergeIntoId: number | null;
}

export interface SimpleImportRequest {
  lines: SimpleImportLineRequest[];
}

export interface SimpleImportResultLine {
  line: number;
  label: string;
  outcome: ImportOutcome;
  id: number | null;
  note: string;
}

export interface SimpleImportResult {
  created: number;
  merged: number;
  skipped: number;
  lines: SimpleImportResultLine[];
}

/** Which resource a generic import dialog is working on, and how to word it. */
export interface SimpleImportKind {
  /** Matches `SimpleImportPreview.resource`. */
  resource: 'categories' | 'suppliers';
  /** Button label, e.g. "Importer des catégories". */
  action: string;
  /** Dialog title. */
  title: string;
  /** Plural noun for the counters: "catégorie(s)", "fournisseur(s)". */
  noun: string;
  /** One line under the title saying what applying will and will not do. */
  caution: string;
}

export const CATEGORY_IMPORT: SimpleImportKind = {
  resource: 'categories',
  action: 'Importer des catégories',
  title: 'Importer des catégories — vérification',
  noun: 'catégorie(s)',
  caution:
    "Une catégorie dont le parent n'existe pas et n'est créé par aucune ligne du fichier est " +
    'refusée : elle ne deviendra pas une catégorie principale par accident.',
};

export const SUPPLIER_IMPORT: SimpleImportKind = {
  resource: 'suppliers',
  action: 'Importer des fournisseurs',
  title: 'Importer des fournisseurs — vérification',
  noun: 'fournisseur(s)',
  caution:
    "Un fournisseur déjà enregistré est complété, jamais écrasé : un contact corrigé à la main " +
    'dans la boutique résiste à un export qui porte encore l’ancien numéro.',
};
