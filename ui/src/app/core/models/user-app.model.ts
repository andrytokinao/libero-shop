import { RoleApp } from './enums';

/**
 * Mirrors com.houssen.libertyshop.entity.UserApp (table user_app), as served by
 * UserResponse. The password hash has no counterpart here: it never leaves the server.
 */
export interface UserApp {
  id: number;
  fullName: string;
  username: string;
  role: RoleApp;
  enabled: boolean;
}
