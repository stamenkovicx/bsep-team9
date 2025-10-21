import { Component } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { MatDialogRef } from '@angular/material/dialog';
import * as QRCode from 'qrcode';

@Component({
  selector: 'xp-two-factor-dialog',
  templateUrl: './two-factor-dialog.component.html',
  styleUrls: ['./two-factor-dialog.component.css']
})
export class TwoFactorDialogComponent {
    qrCodeUrl: string | null = null;
    showQRCode = false;
    verificationCode = '';
    is2FAActive: boolean; 


    constructor(
      public dialogRef: MatDialogRef<TwoFactorDialogComponent>, 
      private authService: AuthService,
    ) {}

    async enable2FA(): Promise<void> {
      this.authService.setup2fa().subscribe({
        next: async (response) => {
          try {
            this.qrCodeUrl = await QRCode.toDataURL(response.qrCodeUrl, {
              width: 250,
              margin: 2,
              color: { dark: '#000000', light: '#FFFFFF' }
            });
            this.showQRCode = true;
          } catch (error) {
            console.error('❌ Greška pri generisanju QR koda:', error);
            alert('Error generating QR code');
            this.onCancel();
          }
        },
        error: (err) => {
          alert('Error initiating 2FA setup: ' + (err.error?.message || 'Unknown error'));
          this.onCancel();
        }
      });
    }

    verify2FACode(): void {
      if (!this.verificationCode || this.verificationCode.length !== 6) {
        alert('Molimo unesite ispravan 6-cifreni kod.'); 
        return;
      }
  
      this.authService.verify2fa(this.verificationCode).subscribe({
        next: (response) => {          
          alert('2FA uspješno omogućen! Ponovno ćete biti prijavljeni.');           
          this.dialogRef.close(true); 
        },
        error: (err) => {
          alert('Kod nije ispravan ili je istekao. Molimo pokušajte ponovo.');
          this.verificationCode = ''; 
        }
      });
    }

    onCancel(): void {
      this.dialogRef.close(false); 
    }
}