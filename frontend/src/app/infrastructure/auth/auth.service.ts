import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { TokenStorage } from './jwt/token.service';
import { KeycloakService } from './keycloak.service';
import { environment } from 'src/env/environment';
import { JwtHelperService } from '@auth0/angular-jwt';
import { Login } from './model/login.model';
import { AuthenticationResponse } from './model/authentication-response.model';
import { User } from './model/user.model';
import { Registration } from './model/registration.model';
import { LoginResponse } from './model/login-response.model';
import { TwoFACodeDTO } from './dto/TwoFACodeDTO';
import { LoginPayload } from './model/LoginPayload';
import { TokenSession } from './model/token-session.model';


@Injectable({
  providedIn: 'root'
})
export class AuthService {
  user$ = new BehaviorSubject<User>({email: "", id: 0, role: "" });

  constructor(
    private http: HttpClient,
    private tokenStorage: TokenStorage,
    private router: Router,
    private keycloakService: KeycloakService
  ) {
    // Subscribe to Keycloak authentication state
    this.keycloakService.authenticated$.subscribe(isAuthenticated => {
      if (isAuthenticated) {
        this.setUser();
      }
    });
  }

    // Basic login with JWT
    loginBasic(loginPayload: LoginPayload): Observable<LoginResponse> {
      return this.http.post<LoginResponse>(environment.apiHost + 'auth/login', loginPayload)
        .pipe(
          tap((response) => {
            this.tokenStorage.saveAccessToken(response.token);
            const user: User = {
              id: response.userId,
              email: response.email,
              role: response.userRole,
              is2FAEnabled: response.is2faEnabled,
              passwordChangeRequired: response.passwordChangeRequired || false
            };
            this.user$.next(user);
          })
        );
    }

    // Keycloak login
    loginKeycloak(): void {
      this.keycloakService.login();
    }

    // Legacy login method - redirects to appropriate method
    login(loginPayload: LoginPayload): void {
      // Default to Keycloak for now
      this.loginKeycloak();
  }


    register(registration: Registration): Observable<{message: string}> {
      return this.http.post<{message: string}>(
          environment.apiHost + 'auth/register', 
          registration
      );
  }

  
  logout(): void {
    this.keycloakService.logout();
    this.tokenStorage.clear();
    this.user$.next({email: "", id: 0, role: "" });
  }

  checkIfUserExists(): void {
    const accessToken = this.tokenStorage.getAccessToken();
    if (accessToken == null) {
      return;
    }
    this.setUser();
  }

  private setUser(): void {
    // Try Keycloak first
    if (this.keycloakService.isAuthenticated()) {
      const roles = this.keycloakService.getUserRoles();
      let role = "BASIC";
      if (roles.includes("ADMIN")) {
        role = "ADMIN";
      } else if (roles.includes("CA")) {
        role = "CA";
      }

      const user: User = {
        id: 0,
        email: this.keycloakService.getUserEmail() || "",
        role: role,
        is2FAEnabled: false
      };
      this.user$.next(user);
      return;
    }

    // Fallback to basic auth (JWT token)
    const jwtHelperService = new JwtHelperService();
    const accessToken = this.tokenStorage.getAccessToken() || "";
    if (accessToken === "") return;

    const decodedToken = jwtHelperService.decodeToken(accessToken);
    const user: User = {
      id: decodedToken.userId,
      email: decodedToken.sub,
      role: decodedToken.role,
      is2FAEnabled: decodedToken.is2FAEnabled || false
    };
    this.user$.next(user);
  }

  isLoggedIn(): boolean {
    // Check if logged in via Keycloak
    if (this.keycloakService.isAuthenticated()) {
      return true;
    }
    
    // Check if logged in via basic auth
    const token = this.tokenStorage.getAccessToken();
    if (!token) return false;
    
    const jwtHelperService = new JwtHelperService();
    return !jwtHelperService.isTokenExpired(token);
  }
  setup2fa(): Observable<{ qrCodeUrl: string }> {
    // Backend identifikuje korisnika preko JWT tokena
    return this.http.post<{ qrCodeUrl: string }>(
      environment.apiHost + 'auth/2fa/setup', 
      {} 
    );
  }

  verify2fa(code: string): Observable<{ message: string }> {
    const dto: TwoFACodeDTO = { code: code };
    return this.http.post<{ message: string }>(
      environment.apiHost + 'auth/2fa/verify', 
      dto
    );
  }

  registerCA(registration: any): Observable<{message: string}> {
    return this.http.post<{message: string}>(
        environment.apiHost + 'auth/register-ca', 
        registration
    );
  }

  changePasswordRequired(request: { newPassword: string, confirmPassword: string }, tempToken: string) {
    return this.http.post<{message: string}>(
      environment.apiHost + 'auth/change-password-required',
      request,
      { headers: { Authorization: `Bearer ${tempToken}` } }
    );
  }

  forgotPassword(email: string): Observable<{message: string}> {
    return this.http.post<{message: string}>(
      environment.apiHost + 'auth/forgot-password',
      { email }
    );
  }
  
  resetPassword(token: string, newPassword: string, confirmPassword: string): Observable<{message: string}> {
    return this.http.post<{message: string}>(
      environment.apiHost + 'auth/reset-password',
      { token, newPassword, confirmPassword }
    );
  }

  getActiveSessions(): Observable<TokenSession[]> {
    return this.http.get<TokenSession[]>(environment.apiHost + 'auth/sessions');
  }

  revokeSession(sessionId: string): Observable<{message: string}> {
    return this.http.post<{message: string}>(
      environment.apiHost + `auth/sessions/${sessionId}/revoke`,
      {}
    );
  }

  revokeAllSessions(): Observable<{message: string}> {
    return this.http.post<{message: string}>(
      environment.apiHost + 'auth/sessions/revoke-all',
      {}
    );
  }
}
