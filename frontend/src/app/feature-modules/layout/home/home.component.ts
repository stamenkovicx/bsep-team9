import { Component, OnInit } from '@angular/core';
import { CertificateService } from '../../certificates/certificate.service';
import { Certificate } from '../../certificates/models/certificate.interface';
import { AuthService } from 'src/app/infrastructure/auth/auth.service';

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

  constructor(private certificateService: CertificateService, private authService: AuthService) {}

  ngOnInit(): void {
    this.loadRecentCertificates();
  }

  loadRecentCertificates(): void {
    this.certificateService.getAllCertificates().subscribe({
      next: (certificates) => {
        // Uzmi posljednja 3 sertifikata za "recent"
        this.recentCertificates = certificates.slice(-3).reverse();
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error loading certificates:', error);
        this.isLoading = false;
      }
    });
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