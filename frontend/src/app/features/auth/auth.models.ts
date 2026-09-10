export interface CurrentUser {
  readonly id: string;
  readonly email: string;
}

export interface AuthenticationResponse {
  readonly user: CurrentUser;
  readonly accessToken: string;
  readonly accessTokenExpiresAt: string;
}

export interface LoginRequest {
  readonly email: string;
  readonly password: string;
}

export interface RegisterRequest {
  readonly email: string;
  readonly password: string;
}

export type AuthState =
  | { readonly status: 'initializing' | 'anonymous' }
  | ({ readonly status: 'authenticated' } & AuthenticationResponse);
