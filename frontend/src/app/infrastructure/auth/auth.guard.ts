import { Injectable } from '@angular/core';
import {
  CanActivate,
  UrlTree,
  Router,
} from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';
import { User } from './model/user.model';
import { SessionValidationService } from './session-validation.service';

@Injectable({
  providedIn: 'root',
})
export class AuthGuard implements CanActivate {
  constructor(
    private router: Router,
    private authService: AuthService,
    private sessionValidationService: SessionValidationService
  ) {}

  canActivate():
    | Observable<boolean | UrlTree>
    | Promise<boolean | UrlTree>
    | boolean
    | UrlTree {
        
    const user: User = this.authService.user$.getValue();
    if (user.email === '') {
      this.router.navigate(['login']);
      return false;
    }

    // Perform a quick session validation check
    this.sessionValidationService.validateSessionNow();
    
    return true;
  }
}
