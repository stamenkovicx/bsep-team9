import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CertificateService } from '../certificate.service';
import { Certificate } from '../models/certificate.interface';
import { CreateEeCsrDTO } from '../../certificate-templates/models/create-ee-csr.dto';
import { Router } from '@angular/router';
import * as forge from 'node-forge'; 

@Component({
  selector: 'app-create-ee-csr',
  templateUrl: './create-ee-csr.component.html',
  styleUrls: ['./create-ee-csr.component.css']
})
export class CreateEeCsrComponent implements OnInit {
  csrForm: FormGroup;
  isLoading = false; 
  issuers: Certificate[] = [];
  issuersLoading = false;
  showSuccessMessage = false;
  successData: any = null;

  constructor(
    private fb: FormBuilder,
    private certificateService: CertificateService,
    private router: Router
  ) {
    this.csrForm = this.createForm();
  }

  ngOnInit(): void {
    this.loadIssuers();
  }

  createForm(): FormGroup {
    const defaultValidTo = new Date();
    defaultValidTo.setFullYear(defaultValidTo.getFullYear() + 1);

    return this.fb.group({
      issuerCertificateId: [null, Validators.required],
      validTo: [this.formatDateForInput(defaultValidTo), Validators.required],
      csrPem: ['', Validators.required],
      
      commonName: ['', Validators.required],
      organization: ['BSEP PKI', Validators.required],
      organizationalUnit: ['IT', Validators.required],
      country: ['RS', Validators.required],
      state: ['Vojvodina', Validators.required],
      locality: ['Novi Sad', Validators.required],
      emailAddress: ['', [Validators.required, Validators.email]]
    });
  }

  private formatDateForInput(date: Date): string {
    const year = date.getFullYear();
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const day = date.getDate().toString().padStart(2, '0');
    return `${year}-${month}-${day}`;
  }


  private loadIssuers(): void {
    this.issuersLoading = true;
    this.certificateService.getIssuers().subscribe({
      next: (data) => {
        this.issuers = data.filter(c => c.type === 'ROOT' || c.type === 'INTERMEDIATE');
        this.issuersLoading = false;
      },
      error: (err) => {
        console.error('Error loading issuers:', err);
        this.issuersLoading = false;
        alert('Greška pri učitavanju izdavalaca.');
      }
    });
  }
  

  generateCsrAndKey(): void {
    const formControls = this.csrForm.controls;

    if (formControls['commonName'].invalid || formControls['emailAddress'].invalid || 
        formControls['organization'].invalid || formControls['organizationalUnit'].invalid ||
        formControls['country'].invalid || formControls['state'].invalid ||
        formControls['locality'].invalid) {
        
        this.csrForm.markAllAsTouched();
        alert('Molimo popunite sva obavezna polja subjekta pre generisanja CSR-a.');
        return;
    }

    this.isLoading = true;
    this.csrForm.patchValue({ csrPem: '' });
    
    try {
        const keys = forge.pki.rsa.generateKeyPair({ bits: 2048, e: 0x10001 });
        const privateKeyPem = forge.pki.privateKeyToPem(keys.privateKey);

        const formValue = this.csrForm.value;
        const subjectAttrs = [
            { name: 'commonName', value: formValue.commonName },
            { name: 'organizationName', value: formValue.organization },
            { name: 'organizationalUnitName', value: formValue.organizationalUnit },
            { name: 'countryName', value: formValue.country },
            { name: 'stateOrProvinceName', value: formValue.state },
            { name: 'localityName', value: formValue.locality },
            { name: 'emailAddress', value: formValue.emailAddress }
        ];

        const csr = forge.pki.createCertificationRequest();
        csr.publicKey = keys.publicKey;
        csr.setSubject(subjectAttrs);
        csr.sign(keys.privateKey, forge.md.sha256.create());
        const csrPem = forge.pki.certificationRequestToPem(csr);
        
        this.csrForm.patchValue({ csrPem: csrPem });
        this.downloadFile(privateKeyPem, `ee_private_key_${formValue.commonName.replace(/\s/g, '_')}.pem`); 
        
        alert('Ključ i CSR su uspešno generisani. Privatni ključ je preuzet! Sada možete izdati sertifikat.');

    } catch (error) {
        console.error('CSR generation error:', error);
        alert('Došlo je do greške prilikom generisanja CSR-a.');
    } finally {
        this.isLoading = false;
    }
  }
  
  private downloadFile(data: string, filename: string): void {
      const blob = new Blob([data], { type: 'text/plain' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      window.URL.revokeObjectURL(url);
  }


  onSubmit(): void {
    if (this.csrForm.invalid) {
      this.csrForm.markAllAsTouched();
      alert('Molimo popunite sva obavezna polja i generišite CSR.');
      return;
    }

    this.isLoading = true;
    
    const formValue = this.csrForm.value;
    
    // KRITIČNA ISPRAVKA: Konverzija datuma u ISO format za Spring Boot
    const validToDateString: string = formValue.validTo;
    const validToDate: Date = new Date(validToDateString);
    const validToFormatted = validToDate.toISOString();


    const request: CreateEeCsrDTO = {
      csrPem: formValue.csrPem,
      validTo: validToFormatted,
      issuerCertificateId: formValue.issuerCertificateId
    };

    this.certificateService.createEECertificateFromCsr(request).subscribe({
      next: (response) => {
        this.isLoading = false;
        this.successData = response;
        this.showSuccessMessage = true;
      },
      error: (error) => {
        this.isLoading = false;
        console.error('Error creating End-Entity certificate:', error);
        alert(`Error creating End-Entity certificate: ${error.error.message || error.statusText}`);
      }
    });
  }
  
  onDownloadCertificate(): void {
    if (this.successData?.serialNumber) {
      this.certificateService.downloadEECertificate(this.successData.serialNumber).subscribe({
        next: (blob: Blob) => {
          const url = window.URL.createObjectURL(blob);
          const link = document.createElement('a');
          link.href = url;
          link.download = this.successData.serialNumber + '.cer';
          link.click();
          window.URL.revokeObjectURL(url);
        },
        error: (err) => {
          console.error('Download failed:', err);
          alert('Failed to download certificate.');
        }
      });
    }
  }

  onBackToList(): void {
    this.router.navigate(['/home']); 
  }

  extractCommonName(subject: string): string {
    if (!subject) {
      return 'Unknown Issuer';
    }
    const cnMatch = subject.match(/CN=([^,]+)/);
    return cnMatch ? cnMatch[1].trim() : 'Unnamed Certificate';
  }
}