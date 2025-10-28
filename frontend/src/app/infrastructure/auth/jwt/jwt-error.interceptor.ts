import { Injectable } from '@angular/core';
import { HttpInterceptor, HttpErrorResponse, HttpEvent, HttpHandler, HttpRequest } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';
import { ACCESS_TOKEN } from '../../../shared/constants';

@Injectable()
export class JwtErrorInterceptor implements HttpInterceptor {

  constructor(
    private router: Router,
    private authService: AuthService
  ) {}

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        // Handle 401 Unauthorized responses
        if (error.status === 401) {
          // Check if it's a revoked session
          const errorMessage = (error.error?.message || '').toLowerCase();
          if (errorMessage.includes('revoked') || errorMessage.includes('session')) {
            // Clear token and user data
            localStorage.removeItem(ACCESS_TOKEN);
            this.authService.user$.next({ email: "", id: 0, role: "" });
            
            // Show notification to user
            alert('Your session has been revoked. Please login again.');
            
            // Navigate to login
            this.router.navigate(['/login']);
          }
        }

        return throwError(() => error);
      })
    );
  }
}

