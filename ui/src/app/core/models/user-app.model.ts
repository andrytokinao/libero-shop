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
}
