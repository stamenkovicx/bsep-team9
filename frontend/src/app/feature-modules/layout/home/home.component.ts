import { Component, OnInit } from '@angular/core';
import { CertificateService } from '../../certificates/certificate.service';
import { Certificate } from '../../certificates/models/certificate.interface';
import { AuthService } from 'src/app/infrastructure/auth/auth.service';
import { User } from 'src/app/infrastructure/auth/model/user.model';
import * as QRCode from 'qrcode';

interface DashboardCertificate {
  subjectCommonName: string;
  type: string;
  status: string;
}

@Component({
  selector: 'xp-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.css']
})
export class HomeComponent implements OnInit {
  recentCertificates: Certificate[] = [];
  isLoading = true;
  currentUser: User | null = null;

  qrCodeUrl: string | null = null;
  showQRCode = false;
  verificationCode = '';

  constructor(private certificateService: CertificateService, private authService: AuthService) {}

  ngOnInit(): void {
    this.loadCurrentUser();
  }

  loadCurrentUser(): void {
    this.authService.user$.subscribe(user => {
      this.currentUser = user;
      // Tek kada dobijemo usera, učitavamo sertifikate
      this.loadRecentCertificates();
    });
  }

  // URADJENO SAMO ZA ADMINA
  loadRecentCertificates(): void {
    if(this.isAdmin())
    {
      this.certificateService.getAllCertificates().subscribe({
        next: (certificates) => {
          // Uzmi posljednja 3 sertifikata za "recent"
          //this.recentCertificates = certificates.slice(-3).reverse();
          this.recentCertificates = certificates
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading certificates:', error);
          this.isLoading = false;
        }
      });
    } else if (this.isCA()) {
      // Za CA korisnika, pozovi metodu koja vraća CEO LANAC
      this.certificateService.getMyChain().subscribe({
        next: (certificates) => {
          this.recentCertificates = certificates.slice(-5).reverse();
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading certificate chain:', error);
          this.isLoading = false;
        }
      });
    } else {
      // Logika za Basic korisnika ili ako nema uloge
      this.isLoading = false;
      this.recentCertificates = [];
    }
  }

  isAdmin(): boolean {
    return this.currentUser?.role === 'ADMIN'
  }

  isCA(): boolean {
    return this.currentUser?.role === 'CA'
  }

  isBasic(): boolean {
      return this.currentUser?.role === 'BASIC'
  }


  isLoggedIn(): boolean {
    return this.authService.isLoggedIn();
  }

  extractCommonName(subject: string): string {
    const cnMatch = subject.match(/CN=([^,]+)/);
    return cnMatch ? cnMatch[1] : 'Unknown';
  }

  getStatusIcon(status: string): string {
    switch(status) {
      case 'VALID': return 'check_circle';
      case 'EXPIRED': return 'error';
      case 'REVOKED': return 'cancel';
      default: return 'help';
    }
  }
  
 async enable2FA(): Promise<void> {
  this.authService.setup2fa().subscribe({
    next: async (response) => {
      console.log('📥 Backend response:', response);
      console.log('📥 Original URL:', response.qrCodeUrl);
      
      try {
        //Generiši QR kod na klijentu
        this.qrCodeUrl = await QRCode.toDataURL(response.qrCodeUrl, {
          width: 300,
          margin: 2,
          color: {
            dark: '#000000',
            light: '#FFFFFF'
          }
        });
        
        console.log('✅ QR kod generisan lokalno (ne šalje se Googleu)');
        this.showQRCode = true;
      } catch (error) {
        console.error('❌ Greška pri generisanju QR koda:', error);
        alert('Error generating QR code');
      }
    },
    error: (err) => {
      alert('Error: ' + (err.error?.message || 'Unknown error'));
    }
  });
}
  
  verify2FACode(): void {
    if (!this.verificationCode || this.verificationCode.length !== 6) {
      alert('Please enter a valid 6-digit code');
      return;
    }
  
    this.authService.verify2fa(this.verificationCode).subscribe({
      next: (response) => {
        alert('2FA successfully enabled!');
        this.showQRCode = false;
        this.qrCodeUrl = null;
        this.verificationCode = '';
      },
      error: (err) => {
        alert('Invalid code. Please try again.');
        this.verificationCode = '';
      }
    });
  }
  
  cancel2FASetup(): void {
    this.showQRCode = false;
    this.qrCodeUrl = null;
    this.verificationCode = '';
  }
}