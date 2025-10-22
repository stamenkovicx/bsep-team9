import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from 'src/env/environment';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-keycloak-login',
  templateUrl: './keycloak-login.component.html',
  styleUrls: ['./keycloak-login.component.css']
})
export class KeycloakLoginComponent implements OnInit {

  constructor(
    private http: HttpClient,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    // Check if we're returning from Keycloak
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('code')) {
      this.handleKeycloakCallback();
    }
  }

  loginWithKeycloak(): void {
    // Redirect to backend OAuth2 login endpoint
    window.location.href = environment.apiHost + 'oauth2/authorization/keycloak';
  }

  private handleKeycloakCallback(): void {
    // This method will be called when returning from Keycloak
    // The backend should handle the OAuth2 callback and redirect here
    this.router.navigate(['/home']);
  }

  getKeycloakUserInfo(): void {
    this.http.get(environment.apiHost + 'auth/keycloak/userinfo').subscribe({
      next: (userInfo: any) => {
        console.log('Keycloak user info:', userInfo);
        // Handle user info if needed
      },
      error: (error) => {
        console.error('Error getting user info:', error);
      }
    });
  }
}
