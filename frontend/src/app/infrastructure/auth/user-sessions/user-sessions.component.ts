import { Component, OnInit } from '@angular/core';
import { AuthService } from '../auth.service';
import { UserSession } from '../model/user-session.model';
import { MatTableDataSource } from '@angular/material/table';
import { MatDialog } from '@angular/material/dialog';
import { ConfirmationDialogComponent } from './confirmation-dialog.component';

@Component({
  selector: 'app-user-sessions',
  templateUrl: './user-sessions.component.html',
  styleUrls: ['./user-sessions.component.css']
})
export class UserSessionsComponent implements OnInit {
  
  sessions: UserSession[] = [];
  dataSource = new MatTableDataSource<UserSession>();
  displayedColumns: string[] = ['deviceType', 'browserName', 'ipAddress', 'lastActivity', 'createdAt', 'isCurrentSession', 'actions'];
  loading = false;
  errorMessage = '';

  constructor(
    private authService: AuthService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.loadSessions();
  }

  loadSessions(): void {
    this.loading = true;
    this.errorMessage = '';
    
    this.authService.getActiveSessions().subscribe({
      next: (sessions) => {
        this.sessions = sessions;
        this.dataSource.data = sessions;
        this.loading = false;
      },
      error: (error) => {
        this.errorMessage = 'Failed to load sessions';
        this.loading = false;
        console.error('Error loading sessions:', error);
      }
    });
  }

  revokeSession(session: UserSession): void {
    if (session.isCurrentSession) {
      this.errorMessage = 'Cannot revoke current session';
      return;
    }

    const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
      width: '400px',
      data: {
        title: 'Revoke Session',
        message: `Are you sure you want to revoke the session from ${session.deviceType} (${session.browserName})?`,
        confirmText: 'Revoke',
        cancelText: 'Cancel'
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.authService.revokeSession(session.sessionId).subscribe({
          next: () => {
            this.loadSessions(); // Reload sessions
          },
          error: (error) => {
            this.errorMessage = 'Failed to revoke session';
            console.error('Error revoking session:', error);
          }
        });
      }
    });
  }

  revokeAllSessions(): void {
    const dialogRef = this.dialog.open(ConfirmationDialogComponent, {
      width: '400px',
      data: {
        title: 'Revoke All Sessions',
        message: 'Are you sure you want to revoke all sessions? This will log you out from all devices except this one.',
        confirmText: 'Revoke All',
        cancelText: 'Cancel'
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.authService.revokeAllSessions().subscribe({
          next: () => {
            this.loadSessions(); // Reload sessions
          },
          error: (error) => {
            this.errorMessage = 'Failed to revoke sessions';
            console.error('Error revoking sessions:', error);
          }
        });
      }
    });
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleString();
  }

  getDeviceIcon(deviceType: string): string {
    switch (deviceType.toLowerCase()) {
      case 'mobile':
        return '📱';
      case 'tablet':
        return '📱';
      case 'desktop':
        return '💻';
      default:
        return '🖥️';
    }
  }

  getBrowserIcon(browserName: string): string {
    switch (browserName.toLowerCase()) {
      case 'chrome':
        return '🌐';
      case 'firefox':
        return '🦊';
      case 'safari':
        return '🧭';
      case 'edge':
        return '🌐';
      case 'opera':
        return '🎭';
      default:
        return '🌐';
    }
  }

  getOtherSessionsCount(): number {
    return this.sessions.filter(s => !s.isCurrentSession).length;
  }
}
