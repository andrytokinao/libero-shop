import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';
import {
  AdminDashboard,
  CashierDashboard,
  DepotDashboard,
  RevenueReport,
  StockDashboard,
  UserActivity,
  UserApp,
} from '../models';

/** One call per landing page: the figures shown side by side come from one snapshot. */
@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);

  cashier(): Observable<CashierDashboard> {
    return this.http.get<CashierDashboard>(`${API_BASE_URL}/dashboard/cashier`);
  }

  depot(): Observable<DepotDashboard> {
    return this.http.get<DepotDashboard>(`${API_BASE_URL}/dashboard/depot`);
  }

  stock(): Observable<StockDashboard> {
    return this.http.get<StockDashboard>(`${API_BASE_URL}/dashboard/stock`);
  }

  admin(): Observable<AdminDashboard> {
    return this.http.get<AdminDashboard>(`${API_BASE_URL}/dashboard/admin`);
  }

  revenue(): Observable<RevenueReport> {
    return this.http.get<RevenueReport>(`${API_BASE_URL}/dashboard/revenue`);
  }

  users(): Observable<UserApp[]> {
    return this.http.get<UserApp[]>(`${API_BASE_URL}/users`);
  }

  userActivity(): Observable<UserActivity[]> {
    return this.http.get<UserActivity[]>(`${API_BASE_URL}/users/activity`);
  }
}
