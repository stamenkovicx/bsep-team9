import { Injectable } from '@angular/core';
import { BehaviorSubject, from } from 'rxjs';
import { tap } from 'rxjs/operators';
import Keycloak from 'keycloak-js';
import { environment } from 'src/env/environment';

@Injectable({
  providedIn: 'root'
})
export class KeycloakService {
  private keycloak: Keycloak;
  private initialized = false;
  private authenticated = new BehaviorSubject<boolean>(false);
  
  public authenticated$ = this.authenticated.asObservable();

  constructor() {
    this.keycloak = new Keycloak({
      url: environment.keycloak.url,
      realm: environment.keycloak.realm,
      clientId: environment.keycloak.clientId
    });
  }

  /**
   * Initialize Keycloak
   */
  init(): Promise<boolean> {
    if (this.initialized) {
      return Promise.resolve(this.authenticated.getValue());
    }

    return new Promise((resolve) => {
      this.keycloak.init({
        onLoad: 'check-sso',
        checkLoginIframe: environment.keycloak.checkLoginIframe,
        enableLogging: environment.keycloak.enableLogging,
        pkceMethod: 'S256'
      })
        .then(authenticated => {
          console.log('Keycloak initialized successfully', authenticated);
          this.initialized = true;
          this.authenticated.next(authenticated);
          
          // Setup token refresh
          if (authenticated) {
            this.setupTokenRefresh();
          }
          
          resolve(authenticated);
        })
        .catch(error => {
          console.warn('Keycloak initialization failed. Keycloak may not be running or not configured. Error:', error);
          // Don't reject, just mark as not authenticated
          this.initialized = true;
          this.authenticated.next(false);
          resolve(false);
        });
    });
  }

  /**
   * Login with Keycloak
   */
  login(): Promise<void> {
    return this.keycloak.login({
      redirectUri: window.location.origin
    });
  }

  /**
   * Logout
   */
  logout(): Promise<void> {
    return this.keycloak.logout({
      redirectUri: window.location.origin
    });
  }

  /**
   * Get access token
   */
  getToken(): string | undefined {
    return this.keycloak.token;
  }

  /**
   * Get refresh token
   */
  getRefreshToken(): string | undefined {
    return this.keycloak.refreshToken;
  }

  /**
   * Get user info
   */
  getUserInfo(): any {
    return this.keycloak.tokenParsed;
  }

  /**
   * Check if user is authenticated
   */
  isAuthenticated(): boolean {
    return this.keycloak.authenticated || false;
  }

  /**
   * Get user roles
   */
  getUserRoles(): string[] {
    return this.keycloak.realmAccess?.roles || [];
  }

  /**
   * Check if user has a specific role
   */
  hasRole(role: string): boolean {
    return this.getUserRoles().includes(role);
  }

  /**
   * Get username
   */
  getUsername(): string | undefined {
    const tokenParsed = this.keycloak.tokenParsed;
    return tokenParsed?.['preferred_username'] || tokenParsed?.['email'] || tokenParsed?.['sub'];
  }

  /**
   * Get user email
   */
  getUserEmail(): string | undefined {
    const tokenParsed = this.keycloak.tokenParsed;
    return tokenParsed?.['email'];
  }

  /**
   * Get user ID
   */
  getUserId(): string | undefined {
    const tokenParsed = this.keycloak.tokenParsed;
    return tokenParsed?.['sub'];
  }

  /**
   * Update token
   */
  updateToken(minValidity: number = 5): Promise<boolean> {
    return this.keycloak.updateToken(minValidity);
  }

  /**
   * Setup automatic token refresh
   */
  private setupTokenRefresh(): void {
    // Refresh token 30 seconds before expiry
    this.keycloak.onTokenExpired = () => {
      this.updateToken(-1).then((refreshed) => {
        if (refreshed) {
          console.log('Token refreshed');
        }
      }).catch((error) => {
        console.error('Failed to refresh token', error);
      });
    };

    // Logout on auth error
    this.keycloak.onAuthError = (errorData) => {
      console.error('Keycloak auth error', errorData);
      this.logout();
    };
  }

  /**
   * Load user profile
   */
  loadUserProfile(): Promise<any> {
    return this.keycloak.loadUserProfile();
  }

  /**
   * Check if token is expired
   */
  isTokenExpired(): boolean {
    const tokenParsed = this.keycloak.tokenParsed;
    if (!tokenParsed || !tokenParsed['exp']) {
      return true;
    }
    
    const expirationTime = tokenParsed['exp'] * 1000;
    return Date.now() >= expirationTime;
  }
}

