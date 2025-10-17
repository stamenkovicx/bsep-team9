import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { CertificateService } from '../../certificates/certificate.service';
import { Certificate } from '../../certificates/models/certificate.interface';
import { AuthService } from 'src/app/infrastructure/auth/auth.service';
import { User } from 'src/app/infrastructure/auth/model/user.model';
import { MatDialogRef } from '@angular/material/dialog';
import { REVOCATION_REASONS } from '../model/revocation-reason.enum';
import { MatDialog } from '@angular/material/dialog';

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
  
  @ViewChild('revokeDialog') revokeDialogTemplate!: TemplateRef<any>;

  recentCertificates: Certificate[] = [];
  isLoading = true;
  currentUser: User | null = null;

  // Revocation
  selectedCertificate: Certificate | null = null;
  selectedReason: string = '';
  revoking = false;
  revocationReasons = REVOCATION_REASONS;
  message = { type: '', text: '' };

  private dialogRef: MatDialogRef<any> | null = null;



  constructor(private certificateService: CertificateService, private authService: AuthService,private dialog: MatDialog) {}

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

  canRevoke(certificate: Certificate): boolean {
    return certificate.type === 'INTERMEDIATE' && 
           certificate.status === 'VALID' &&
           (this.isAdmin() || this.isCA());
  }

  openRevokeDialog(certificate: Certificate): void {
    this.selectedCertificate = certificate;
    this.selectedReason = '';
    this.dialogRef = this.dialog.open(this.revokeDialogTemplate, {
      width: '600px',
      disableClose: true
    });
  }

  confirmRevoke(): void {
    if (!this.selectedCertificate || !this.selectedReason) return;

    this.revoking = true;
    this.certificateService.revokeCertificate(this.selectedCertificate.id, this.selectedReason).subscribe({
      next: (response) => {
        this.showMessage('success', '✅ Certificate revoked successfully!');
        
        // Ažuriraj status u listi
        const index = this.recentCertificates.findIndex(c => c.id === this.selectedCertificate!.id);
        if (index !== -1) {
          this.recentCertificates[index] = {
            ...this.recentCertificates[index],
            status: 'REVOKED',
            revocationReason: this.selectedReason,
            revokedAt: new Date().toISOString()
          };
        }
        
        this.closeDialog();
        this.revoking = false;
      },
      error: (error) => {
        this.showMessage('error', error.error?.message || '❌ Failed to revoke certificate');
        this.revoking = false;
      }
    });
  }
  downloadCRL(certificate: Certificate): void {
    const issuerSerial = certificate.issuerCertificate?.serialNumber || certificate.serialNumber;
    
    this.certificateService.downloadCRL(issuerSerial).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${issuerSerial}.crl`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.showMessage('success', '📥 CRL downloaded successfully');
      },
      error: () => this.showMessage('error', '❌ Failed to download CRL')
    });
  }

  showMessage(type: 'success' | 'error', text: string): void {
    this.message = { type, text };
    setTimeout(() => this.clearMessage(), 5000);
  }

  clearMessage(): void {
    this.message = { type: '', text: '' };
  }

  closeDialog(): void {
    if (this.dialogRef) {
      this.dialogRef.close();
      this.dialogRef = null;
    }
    this.selectedCertificate = null;
    this.selectedReason = '';
  }

  getRevocationReasonLabel(reason: string): string {
    const found = this.revocationReasons.find(r => r.value === reason);
    return found ? found.label : reason;
  }
  
}