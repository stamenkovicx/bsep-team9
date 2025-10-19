import { Component, OnInit, TemplateRef, ViewChild, ChangeDetectorRef } from '@angular/core';
import { CertificateService } from '../../certificate.service';
import { Certificate } from '../../models/certificate.interface';
import { MatTreeFlatDataSource, MatTreeFlattener } from '@angular/material/tree';
import { FlatTreeControl } from '@angular/cdk/tree';

import { AuthService } from 'src/app/infrastructure/auth/auth.service';
import { User } from 'src/app/infrastructure/auth/model/user.model';
import { MatDialogRef } from '@angular/material/dialog';
import { REVOCATION_REASONS } from '../../../layout/model/revocation-reason.enum';
import { MatDialog } from '@angular/material/dialog';
import { CertificateFlatNode } from '../../models/certificate-flat-node';



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
    private dialog: MatDialog,
    private changeDetectorRef: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadCurrentUser();
    this.loadTreeData();
  }

  loadTreeData(): void {
    this.certificateService.getMyChainAsTree().subscribe({
      next: (treeData) => {
        // Pre popunjavanja datasource-a, inicijalno proveri ceo lanac
        this.checkInitialChainValidity(treeData);
        this.dataSource.data = treeData;
      },
      error: (err) => {
        console.error('Greška pri učitavanju lanca:', err);
      }
    });
  }
  // Proverava da li je lanac već nevažeći pri učitavanju
  private checkInitialChainValidity(nodes: Certificate[]): void {
    for (const node of nodes) {
      if (node.status === 'REVOKED') {
        // Ako je roditelj revoked, označi svu decu
        this.markChainAsInvalid(node);
      } else if (node.children && node.children.length > 0) {
        // Nastavi proveru rekurzivno
        this.checkInitialChainValidity(node.children);
      }
    }
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

 canRevoke(certificate: Certificate, isChainInvalid?: boolean): boolean {
  // Ne možeš povući ako je već REVOKED ili ako je lanac nevažeći
  if (certificate.status !== 'VALID' || isChainInvalid) {
    return false;
  }
  // Admin može sve (što je VALID i nije chain invalid)
  if (this.isAdmin()) {
    return true;
  }
  // Niko ne može ROOT (osim Admina)
  if (certificate.type === 'ROOT') {
    return false;
  }
  // CA i Basic mogu svoje (ako je VALID i nije chain invalid)
  if (this.isCA() || this.isBasic()) {
    return true;
  }
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
    this.clearMessage();
    this.certificateService.revokeCertificate(this.selectedCertificate.id, this.selectedReason).subscribe({
      next: (response) => {
        this.showMessage('success', '✅ Certificate revoked successfully!');

        // Pronađi originalni node u stablu
        const revokedNode = this.findNodeById(this.dataSource.data, this.selectedCertificate!.id);

        if (revokedNode) {
          revokedNode.status = 'REVOKED';
          revokedNode.revocationReason = this.selectedReason;

          // Poziv nove metode za označavanje dece
          this.markChainAsInvalid(revokedNode);
        }

        this.closeDialog();
        this.revoking = false;

        // Osveži prikaz stabla
        const data = this.dataSource.data;
        this.dataSource.data = [];
        this.dataSource.data = data;
        this.changeDetectorRef.detectChanges();
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
  // Rekurzivna funkcija za označavanje dece
  private markChainAsInvalid(node: Certificate): void {
    const nodeWithStatus = node as any;
    if (node.status !== 'REVOKED') {
        nodeWithStatus.isChainInvalid = true;
    }

    // Nastavi rekurzivno za decu
    if (node.children && node.children.length > 0) {
      for (const child of node.children) {
         // Oznaci dete kao nevažeće ZBOG LANCA
         (child as any).isChainInvalid = true;
         // Pozovi rekurziju za decu deteta
         this.markChainAsInvalid(child);
      }
    }
  }

  private findNodeById(nodes: Certificate[], id: number): Certificate | null {
    for (const node of nodes) {
      if (node.id === id) { return node; }
      if (node.children && node.children.length > 0) {
        const found = this.findNodeById(node.children, id);
        if (found) { return found; }
      }
    }
    return null;
  }
  getTooltipText(node: CertificateFlatNode): string {
    return '';
  }
}