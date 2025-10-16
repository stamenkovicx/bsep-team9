import { Component, OnInit } from '@angular/core';
import { CertificateService } from '../../certificates/certificate.service';
import { Certificate } from '../../certificates/models/certificate.interface';
import { AuthService } from 'src/app/infrastructure/auth/auth.service';
import { User } from 'src/app/infrastructure/auth/model/user.model';

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
}