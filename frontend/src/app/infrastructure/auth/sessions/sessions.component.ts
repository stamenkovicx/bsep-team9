import { Component, OnInit } from '@angular/core';
import { AuthService } from '../auth.service';
import { TokenSession } from '../model/token-session.model';
import { TokenStorage } from '../jwt/token.service';
import { JwtHelperService } from '@auth0/angular-jwt';
import { MatSnackBar } from '@angular/material/snack-bar';

@Component({
  selector: 'xp-sessions',
  templateUrl: './sessions.component.html',
  styleUrls: ['./sessions.component.css']
})
export class SessionsComponent implements OnInit {
  sessions: TokenSession[] = [];
  currentSessionId: string = '';
  loading: boolean = false;

  constructor(
    private authService: AuthService,
    private tokenStorage: TokenStorage,
    private snackBar: MatSnackBar
  ) { }

  ngOnInit(): void {
    this.loadSessions();
    this.getCurrentSessionId();
  }

  loadSessions(): void {
    this.loading = true;
    this.authService.getActiveSessions().subscribe({
      next: (sessions) => {
        this.sessions = sessions;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading sessions:', error);
        this.loading = false;
      }
    });
  }

  getCurrentSessionId(): void {
    const token = this.tokenStorage.getAccessToken();
    if (token) {
      const jwtHelperService = new JwtHelperService();
      const decodedToken = jwtHelperService.decodeToken(token);
      this.currentSessionId = decodedToken?.jti || '';
    }
  }

  revokeSession(sessionId: string): void {
    this.authService.revokeSession(sessionId).subscribe({
      next: (response) => {
        this.loadSessions();
        this.snackBar.open(response.message || 'Session revoked successfully. The user will be logged out on that device.', 'Close', {
          duration: 4000,
          panelClass: ['success-snackbar']
        });
      },
      error: (error) => {
        console.error('Error revoking session:', error);
        this.snackBar.open('Error revoking session. Please try again.', 'Close', {
          duration: 3000,
          panelClass: ['error-snackbar']
        });
      }
    });
  }

  revokeAllSessions(): void {
    this.authService.revokeAllSessions().subscribe({
      next: (response) => {
        this.loadSessions();
        this.snackBar.open(response.message || 'All other sessions have been revoked. Those devices will be logged out on their next request.', 'Close', {
          duration: 5000,
          panelClass: ['success-snackbar']
        });
      },
      error: (error) => {
        console.error('Error revoking all sessions:', error);
        this.snackBar.open('Error revoking sessions. Please try again.', 'Close', {
          duration: 3000,
          panelClass: ['error-snackbar']
        });
      }
    });
  }

  isCurrentSession(sessionId: string): boolean {
    return sessionId === this.currentSessionId;
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleString();
  }
}

