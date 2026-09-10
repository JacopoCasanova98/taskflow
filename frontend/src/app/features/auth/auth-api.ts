import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { API_BASE_URL } from '../../core/config/api-base-url';
import { AuthenticationResponse, CurrentUser, LoginRequest, RegisterRequest } from './auth.models';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/auth`;

  csrf() {
    return this.http.get<void>(`${this.base}/csrf`);
  }
  register(request: RegisterRequest) {
    return this.http.post<AuthenticationResponse>(`${this.base}/register`, request);
  }
  login(request: LoginRequest) {
    return this.http.post<AuthenticationResponse>(`${this.base}/login`, request);
  }
  refresh() {
    return this.http.post<AuthenticationResponse>(`${this.base}/refresh`, null);
  }
  logout() {
    return this.http.post<void>(`${this.base}/logout`, null);
  }
  me() {
    return this.http.get<CurrentUser>(`${this.base}/me`);
  }
}
