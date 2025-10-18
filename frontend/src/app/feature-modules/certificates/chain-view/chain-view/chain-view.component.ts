import { Component, OnInit, TemplateRef, ViewChild } from '@angular/core'; 
import { CertificateService } from '../../certificate.service';
import { Certificate } from '../../models/certificate.interface';
import { MatTreeFlatDataSource, MatTreeFlattener } from '@angular/material/tree';
import { FlatTreeControl } from '@angular/cdk/tree';

import { AuthService } from 'src/app/infrastructure/auth/auth.service';
import { User } from 'src/app/infrastructure/auth/model/user.model';
import { MatDialogRef } from '@angular/material/dialog';
import { REVOCATION_REASONS } from '../../../layout/model/revocation-reason.enum';
import { MatDialog } from '@angular/material/dialog';


// Definišemo "ravni" čvor (flat node) koji MatTree koristi
interface CertificateFlatNode {
  expandable: boolean;
  name: string; // Ovo je ono sto ce se prikazati
  level: number;
  data: Certificate; // Originalni podaci
}

@Component({
  selector: 'xp-chain-view',
  templateUrl: './chain-view.component.html',
  styleUrls: ['./chain-view.component.css'] 
})
export class ChainViewComponent implements OnInit {

  // MatTree kontroleri
  private _transformer = (node: Certificate, level: number) => {
    let displayName = node.subjectCommonName; 
    if (!displayName) { 
      try {
        // Traži 'CN=' u stringu
        const match = node.subject.match(/CN=([^,]+)/);
        if (match && match[1]) {
          displayName = match[1]; // Uzima 'Root TRECI ZA High School'
        } else {
          displayName = node.subject;
        }
      } catch (e) {
        displayName = node.subject;
      }
    }
    return {
      expandable: !!node.children && node.children.length > 0,
      name: `${displayName} (${node.type})`, // Koristimo 'displayName'
      level: level,
      data: node
    };
  }

  treeControl = new FlatTreeControl<CertificateFlatNode>(
    node => node.level, 
    node => node.expandable
  );

  treeFlattener = new MatTreeFlattener(
    this._transformer, 
    node => node.level, 
    node => node.expandable, 
    node => node.children
  );

  dataSource = new MatTreeFlatDataSource(this.treeControl, this.treeFlattener);

  @ViewChild('revokeDialog') revokeDialogTemplate!: TemplateRef<any>;

  currentUser: User | null = null;
  selectedCertificate: Certificate | null = null;
  selectedReason: string = '';
  revoking = false;
  revocationReasons = REVOCATION_REASONS;
  message = { type: '', text: '' };
  private dialogRef: MatDialogRef<any> | null = null;


  constructor(
    private certificateService: CertificateService,
    private authService: AuthService,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.loadCurrentUser();
    this.certificateService.getMyChainAsTree().subscribe({
      next: (treeData) => {
        this.dataSource.data = treeData;
      },
      error: (err) => {
        console.error('Greška pri učitavanju lanca:', err);
      }
    });
  }

  hasChild = (_: number, node: CertificateFlatNode) => node.expandable;


  loadCurrentUser(): void {
 this.authService.user$.subscribe(user => {
 this.currentUser = user;
 });
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
  extractCommonName(subject: string): string {
    const cnMatch = subject.match(/CN=([^,]+)/);
    return cnMatch ? cnMatch[1] : 'Unknown';
 }

 canRevoke(certificate: Certificate): boolean {
  // 1. Ne možeš povući sertifikat koji nije validan (već je povučen/istekao)
  if (certificate.status !== 'VALID') {
    return false;
  }
  // 2. Admin može da povuče SVE (i Root i sve ostale)
  if (this.isAdmin()) {
    return true;
  }
  // 3. Ako nismo Admin, NIKO ne može da povuče ROOT sertifikat
  if (certificate.type === 'ROOT') {
    return false; 
  }
  // 4. Ako je sertifikat INTERMEDIATE ili END_ENTITY:
  //    Dozvoljavamo CA i Basic korisnicima da vide dugme.
  if (this.isCA() || this.isBasic()) {
    return true;
  }

  // U svim ostalim slučajevima, sakrij dugme
  return false;
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
 
        // Ažuriraj status u stablu
        // Malo je komplikovanije jer je stablo, ali ovo će osvežiti status
        this.selectedCertificate!.status = 'REVOKED';
        this.selectedCertificate!.revocationReason = this.selectedReason;
 
 this.closeDialog();
 this.revoking = false;
        
        // Ponovo iscrtaj stablo sa novim podacima
        const data = this.dataSource.data;
        this.dataSource.data = [];
        this.dataSource.data = data;
  },
  error: (error) => {
  this.showMessage('error', error.error?.message || '❌ Failed to revoke certificate');
  this.revoking = false;
  }
  });
 }

  //Ovo je preuzimanje CRL-a, ne samog sertifikata.
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