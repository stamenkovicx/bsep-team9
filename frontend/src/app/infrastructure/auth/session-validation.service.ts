import { Injectable } from '@angular/core';
import { AuthService } from './auth.service';
import { Router } from '@angular/router';
import { BehaviorSubject, interval, Subscription } from 'rxjs';
import { switchMap, catchError } from 'rxjs/operators';
import { of } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SessionValidationService {
  private validationInterval: Subscription | null = null;
  private isSessionValid$ = new BehaviorSubject<boolean>(true);
  
  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  startSessionValidation(): void {
    // Check session validity every 30 seconds
    this.validationInterval = interval(30000)
      .pipe(
        switchMap(() => {
          if (!this.authService.isLoggedIn()) {
            return of(false);
          }
          return this.authService.validateCurrentSession();
        }),
        catchError(() => {
          // If validation fails, consider session invalid
          return of({ isActive: false, sessionId: '' });
        })
      )
      .subscribe(response => {
        if (typeof response === 'boolean') {
          this.isSessionValid$.next(response);
          if (!response) {
            this.handleSessionInvalid();
          }
        } else if (!response.isActive) {
          this.isSessionValid$.next(false);
          this.handleSessionInvalid();
        } else {
          this.isSessionValid$.next(true);
        }
      });
  }

  stopSessionValidation(): void {
    if (this.validationInterval) {
      this.validationInterval.unsubscribe();
      this.validationInterval = null;
    }
  }

  getSessionValid(): BehaviorSubject<boolean> {
    return this.isSessionValid$;
  }

  private handleSessionInvalid(): void {
    console.log('Session has been revoked, logging out...');
    this.authService.logout();
    this.router.navigate(['/login']);
    alert('Your session has been revoked from another device. You have been logged out.');
  }

  // Manual validation check
  validateSessionNow(): void {
    if (!this.authService.isLoggedIn()) {
      this.isSessionValid$.next(false);
      return;
    }

    this.authService.validateCurrentSession().subscribe({
      next: (response) => {
        this.isSessionValid$.next(response.isActive);
        if (!response.isActive) {
          this.handleSessionInvalid();
        }
      },
      error: () => {
        this.isSessionValid$.next(false);
        this.handleSessionInvalid();
      }
    });
  }
}
