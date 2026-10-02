import { RoleApp } from './enums';

/**
 * Mirrors com.houssen.liberoshop.entity.UserApp (table user_app), as served by
 * UserResponse. The password hash has no counterpart here: it never leaves the server.
 */
export interface UserApp {
  id: number;
  fullName: string;
  username: string;
  /**
   * Every job the account may do, in the server's order of precedence. One entry for a
   * depot that splits the duties, several for the grocery where one person does everything.
   */
  roles: RoleApp[];
  enabled: boolean;
  /**
   * When the profile photo last changed, or null when there is none and initials stand in.
   * Part of the photo's URL, so a new photo is never hidden behind a cached old one.
   */
  photoVersion?: number | null;
}

/**
 * The least a screen needs to show a person: what an order's cash holder or a slip's author
 * carries as much as a full account. Every `UserApp` is one.
 */
export interface UserRef {
  id: number;
  fullName: string;
  username?: string;
  roles?: readonly RoleApp[];
  enabled?: boolean;
  photoVersion?: number | null;
}
