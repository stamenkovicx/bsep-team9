import { Component, OnInit, OnDestroy } from '@angular/core';
import { AuthService } from './infrastructure/auth/auth.service';
import { SessionValidationService } from './infrastructure/auth/session-validation.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  title = 'Explorer';
  private userSubscription: Subscription = new Subscription();

  constructor(
    private authService: AuthService,
    private sessionValidationService: SessionValidationService
  ) {}

  ngOnInit(): void {
    this.checkIfUserExists();
    this.setupSessionValidation();
  }

  ngOnDestroy(): void {
    this.userSubscription.unsubscribe();
    this.sessionValidationService.stopSessionValidation();
  }
  
  private checkIfUserExists(): void {
    this.authService.checkIfUserExists();
  }

  private setupSessionValidation(): void {
    // Start session validation when user is logged in
    this.userSubscription = this.authService.user$.subscribe(user => {
      if (user.email && user.email !== '') {
        // User is logged in, start session validation
        this.sessionValidationService.startSessionValidation();
      } else {
        // User is not logged in, stop session validation
        this.sessionValidationService.stopSessionValidation();
      }
    });
  }
}
