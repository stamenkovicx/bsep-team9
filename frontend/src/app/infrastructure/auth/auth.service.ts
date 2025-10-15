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

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  user$ = new BehaviorSubject<User>({email: "", id: 0, role: "" });

  constructor(private http: HttpClient,
    private tokenStorage: TokenStorage,
    private router: Router) { }

    login(login: Login): Observable<LoginResponse> {
      return this.http
        .post<LoginResponse>(environment.apiHost + 'auth/login', login)
        .pipe(
          tap((response) => {
            // 2. Cuvamo token iz odgovora
            this.tokenStorage.saveAccessToken(response.token);
            
            // 3. Postavljamo korisnika direktno iz odgovora, nema potrebe za dekodiranjem
            const user: User = {
              id: response.userId,
              email: response.email,
              role: response.userRole
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
    };
    this.user$.next(user);
}
}
