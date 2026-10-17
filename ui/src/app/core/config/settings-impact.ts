import { ROLE_PRECEDENCE, RoleApp, ShopFeatures, ShopSettings, businessTypeInfo, normalizeFeatures } from '../models';
import { ROLE_NAVIGATION } from './navigation';

/** What a new configuration does to one module's menu: the screens it gains, loses or renames. */
export interface ModuleChange {
  role: RoleApp;
  /** The module's heading in the sidebar, worded by the new configuration ("Bar", "Stock"). */
  module: string;
  added: string[];
  removed: string[];
  renamed: { from: string; to: string }[];
}

/**
 * The screens each module's menu shows under a configuration, by path. A module the shop does
 * not use (no depot queue at a counter) shows none: whoever holds only that role keeps a menu
 * all the same, but the module is gone for the shop.
 */
function screensOf(role: RoleApp, settings: ShopSettings): Map<string, string> {
  const words = businessTypeInfo(settings.businessType).vocabulary;
  const enabled = (requires?: (features: ShopFeatures) => boolean) => !requires || requires(settings);
  const section = ROLE_NAVIGATION[role];
  if (!enabled(section.requires)) {
    return new Map();
  }
  return new Map(
    section.items
      .filter((item) => enabled(item.requires))
      .map((item) => [item.path, item.wording ? words[item.wording] : item.label]),
  );
}

/**
 * The menus a change of configuration touches, module by module, in the sidebar's order:
 * what the administrator is told before saving, since these switches change the menu of the
 * whole staff. Modules left as they were are not listed.
 */
export function menuChanges(before: ShopSettings, after: ShopSettings): ModuleChange[] {
  const words = businessTypeInfo(after.businessType).vocabulary;
  return ROLE_PRECEDENCE.map((role) => {
    const was = screensOf(role, before);
    const now = screensOf(role, after);
    const section = ROLE_NAVIGATION[role];
    return {
      role,
      module: section.wording ? words[section.wording] : section.label,
      added: [...now].filter(([path]) => !was.has(path)).map(([, label]) => label),
      removed: [...was].filter(([path]) => !now.has(path)).map(([, label]) => label),
      renamed: [...now]
        .filter(([path, label]) => was.has(path) && was.get(path) !== label)
        .map(([path, label]) => ({ from: was.get(path)!, to: label })),
    };
  }).filter((change) => change.added.length || change.removed.length || change.renamed.length);
}

/**
 * What ticking one switch adds to the menus, the others staying as they are: "« Caisse dépôt »
 * (Dépôt)". Empty when the switch changes how a screen behaves but no menu.
 */
export function switchEffect(settings: ShopSettings, key: keyof ShopFeatures): string {
  const withSwitch = (on: boolean): ShopSettings => ({
    ...settings,
    ...normalizeFeatures({ ...settings, [key]: on }),
  });
  return menuChanges(withSwitch(false), withSwitch(true))
    .filter((change) => change.added.length)
    .map((change) => `${change.added.map((label) => `« ${label} »`).join(', ')} (${change.module})`)
    .join(' · ');
}
