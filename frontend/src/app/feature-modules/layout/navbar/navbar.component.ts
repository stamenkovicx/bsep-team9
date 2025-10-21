import { Component, OnInit } from '@angular/core';
import { AuthService } from 'src/app/infrastructure/auth/auth.service';
import { User } from 'src/app/infrastructure/auth/model/user.model';
import { MatDialog } from '@angular/material/dialog';
import { TwoFactorDialogComponent } from 'src/app/infrastructure/two-factor-dialog/two-factor-dialog.component';

@Component({
  selector: 'xp-navbar',
  templateUrl: './navbar.component.html',
  styleUrls: ['./navbar.component.css']
})
export class NavbarComponent implements OnInit {

  user: User | undefined;

  constructor(private authService: AuthService,private dialog: MatDialog,) {}

  ngOnInit(): void {
    this.authService.user$.subscribe(user => {
      this.user = user;
    });
  }

  onLogout(): void {
    this.authService.logout();
  }
  open2FADialog(): void {
    const dialogRef = this.dialog.open(TwoFactorDialogComponent, {
        width: '450px', 
        disableClose: true
    });

    dialogRef.afterClosed().subscribe(result => {
        if (result === true) {
            // Opcionalno: Logika za osvežavanje statusa korisnika nakon uspešnog 2FA setup-a
            console.log('2FA setup complete.');
        }
    });
}
}
