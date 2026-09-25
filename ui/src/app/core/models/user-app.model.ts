import { RoleApp } from './enums';

/** Mirrors com.houssen.libertyshop.entity.UserApp (table user_app). */
export interface UserApp {
  id: number;
  fullName: string;
  role: RoleApp;
}
