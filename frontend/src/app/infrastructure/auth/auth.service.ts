import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { TokenStorage } from './jwt/token.service';
import { environment } from 'src/env/environment';
import { JwtHelperService } from '@auth0/angular-jwt';
import { Login } from './model/login.model';
import { AuthenticationResponse } from './model/authentication-response.model';
import { User } from './model/user.model';
import { Registration } from './model/registration.model';
import { LoginResponse } from './model/login-response.model';
import { TwoFACodeDTO } from './dto/TwoFACodeDTO';
import { LoginPayload } from './model/LoginPayload';


@Injectable({
  providedIn: 'root'
})
export class AuthService {
  user$ = new BehaviorSubject<User>({email: "", id: 0, role: "" });

  constructor(private http: HttpClient,
    private tokenStorage: TokenStorage,
    private router: Router) { }

    login(loginPayload: LoginPayload): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(environment.apiHost + 'auth/login', loginPayload) 
      .pipe(
        tap((response) => {
          // UVIJEK ČUVAJ TOKEN, čak i kada je potrebna promena lozinke
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


    register(registration: Registration): Observable<{message: string}> {
      return this.http.post<{message: string}>(
          environment.apiHost + 'auth/register', 
          registration
      );
  }

  
  logout(): void {
    this.router.navigate(['/home']).then(_ => {
      this.tokenStorage.clear();
      this.user$.next({email: "", id: 0, role: "" });
      }
    );
  }

  checkIfUserExists(): void {
    const accessToken = this.tokenStorage.getAccessToken();
    if (accessToken == null) {
      return;
    }
    this.setUser();
  }

  private setUser(): void {
    const jwtHelperService = new JwtHelperService();
    const accessToken = this.tokenStorage.getAccessToken() || "";
    if(accessToken === "") return;

    // Dekodiranje tokena sa ispravnim poljima
    const decodedToken = jwtHelperService.decodeToken(accessToken);
    const user: User = {
        id: decodedToken.userId, // Ispravno polje je 'userId'
        email: decodedToken.sub,   // Email se nalazi u 'sub' (subject) polju
        role: decodedToken.role,   // Uloga je u 'role' polju
        is2FAEnabled: decodedToken.is2FAEnabled || false
    };
    this.user$.next(user);
  }

  isLoggedIn(): boolean {
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
}
