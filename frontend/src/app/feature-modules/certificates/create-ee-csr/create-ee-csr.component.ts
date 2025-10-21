import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CertificateService } from '../certificate.service';
import { Certificate } from '../models/certificate.interface';
import { CreateEeCsrDTO } from '../../certificate-templates/models/create-ee-csr.dto';
import { Router, ActivatedRoute } from '@angular/router';
import * as forge from 'node-forge'; 
import { AuthService } from 'src/app/infrastructure/auth/auth.service';
import { User } from 'src/app/infrastructure/auth/model/user.model';
import { CertificateTemplatesService } from '../../certificate-templates/certificate-templates.service';
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

  currentUser: User | null = null;
  templateData: any = null;
  templateName: String = '';

  constructor(
    private fb: FormBuilder,
    private certificateService: CertificateService,
    private router: Router,
    private authService: AuthService,
    private certificateTemplatesService: CertificateTemplatesService,
    private route: ActivatedRoute
  ) {
    this.csrForm = this.createForm();
  }

  ngOnInit(): void {
    this.loadCurrentUser();
    this.loadIssuers();
    this.loadTemplateData();
  }

  isCA(): boolean {
    return this.currentUser?.role === 'CA'
  }

  loadCurrentUser(): void {
    this.authService.user$.subscribe(user => {
      this.currentUser = user;
    });
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

        // Ako imamo template podatke, primijeni ih nakon što se učitaju issueri
        if (this.templateData) {
          setTimeout(() => this.applyTemplateData(this.templateData), 100);
        }
      },
      error: (err) => {
        console.error('Error loading issuers:', err);
        this.issuersLoading = false;
        alert('Greška pri učitavanju izdavalaca.');
      }
    });
  }

  private loadTemplateData(): void {
    // Proveri query parametre za template ID
    this.route.queryParams.subscribe(params => {
      if (params['templateId']) {
        this.templateName = params['templateName'] || 'Unknown Template';
        console.log('📋 Loading template from query params:', params['templateId']);
      }
    });

    // Uzmi template podatke iz servisa
    this.templateData = this.certificateTemplatesService.getCurrentTemplateData();
    
    if (this.templateData) {
      console.log('🎯 FOUND TEMPLATE DATA FOR EE CSR:', this.templateData);
      
      // Ako issueri već postoje, primijeni template odmah
      if (this.issuers.length > 0) {
        this.applyTemplateData(this.templateData);
      }
      // Inače će se primijeniti kada se issueri ucitaju u loadIssuers()
    } else {
      console.log('❌ No template data found for EE CSR');
    }
  }

  private applyTemplateData(templateData: any): void {
    if (!templateData) return;

    console.log('🔧 Applying template data to EE CSR form:', templateData);

    // Pronađi odgovarajućeg issuera u listi
    const selectedIssuer = this.issuers.find(issuer => issuer.id === templateData.caIssuerId);
    
    if (selectedIssuer) {
      this.csrForm.patchValue({
        issuerCertificateId: templateData.caIssuerId
      });
      console.log('✅ Issuer set from template:', selectedIssuer);
    } else {
      console.warn('⚠️ Template issuer not found in available issuers:', templateData.caIssuerId);
    }

    // Postavi maksimalni validity period iz šablona
    const today = new Date();
    const maxValidTo = new Date();
    maxValidTo.setDate(today.getDate() + templateData.maxValidityDays);
    
    this.csrForm.patchValue({
      validTo: this.formatDateForInput(maxValidTo)
    });

    // Onemogući polja koja su fiksirana šablonom
    this.csrForm.get('issuerCertificateId')?.disable();
    this.csrForm.get('validTo')?.disable();

    // Postavi CN validaciju prema šablonu
    if (templateData.commonNameRegex && templateData.commonNameRegex !== '.*') {
      this.setupCNValidation(templateData.commonNameRegex);
    }

    // Postavi podrazumevane vrednosti za organizaciju ako postoje u šablonu
    // (Ovo je opciono - možeš dodati polja u template za ove podatke)
    
    console.log('✅ Template successfully applied to EE CSR form');
  }

  private setupCNValidation(regexPattern: string): void {
    const cnControl = this.csrForm.get('commonName');
    
    if (cnControl && regexPattern) {
      console.log('🔧 Setting up CN validation with pattern:', regexPattern);
      
      cnControl.valueChanges.subscribe(cn => {
        if (cn && regexPattern) {
          try {
            const regex = new RegExp(regexPattern);
            const isValid = regex.test(cn);
            
            console.log(`🔍 CN validation: "${cn}" vs "${regexPattern}" -> ${isValid}`);
            
            if (!isValid) {
              cnControl.setErrors({ patternMismatch: true });
            } else {
              if (cnControl.hasError('patternMismatch')) {
                cnControl.setErrors(null);
              }
            }
          } catch (error) {
            console.error('❌ Regex error:', error);
          }
        }
      });

      // Pokreni inicijalnu validaciju ako već postoji vrednost
      const currentCN = cnControl.value;
      if (currentCN) {
        const regex = new RegExp(regexPattern);
        if (!regex.test(currentCN)) {
          cnControl.setErrors({ patternMismatch: true });
        }
      }
    }
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

  // Helper metode za template prikaz
  getTemplateName(): String {
    return this.templateName || this.templateData?.name || '';
  }

  hasTemplate(): boolean {
    return !!this.templateData;
  }

  getTemplateCNPattern(): string {
    return this.templateData?.commonNameRegex || '';
  }
}