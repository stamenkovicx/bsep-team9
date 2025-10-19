import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CertificateService } from '../certificate.service';
import { CreateCertificateDTO } from '../models/create-certificate.dto';
import { AuthService } from 'src/app/infrastructure/auth/auth.service';
import { User } from 'src/app/infrastructure/auth/model/user.model';
import { Certificate } from '../models/certificate.interface';
import { CertificateTemplatesService } from '../../certificate-templates/certificate-templates.service';

@Component({
  selector: 'app-create-certificate',
  templateUrl: './create-certificate.component.html',
  styleUrls: ['./create-certificate.component.css']
})
export class CreateCertificateComponent implements OnInit {
  certificateForm: FormGroup;
  isLoading = false;
  selectedType: string = 'ROOT'; // default vrijednost
  showSuccessMessage = false;  
  successData: any = null;
  currentUser: User | null = null;
  
  issuers: Certificate[] = [];      // Niz za čuvanje mogucih izdavaoca
  issuersLoading = false;           // Za prikaz spinner-a dok se ucitavaju

  private templateData: any = null;

  constructor(
    private fb: FormBuilder,
    private certificateService: CertificateService,
    private certificateTemplatesService: CertificateTemplatesService,
    private router: Router,
    private authService: AuthService
  ) {
    this.certificateForm = this.createForm();
  }

  ngOnInit(): void {
    this.loadCurrentUser();

    const templateData = this.certificateTemplatesService.getCurrentTemplateData();
  
    if (templateData) {
      console.log('🎯 FOUND TEMPLATE DATA IN SERVICE:', templateData);
      this.applyTemplateData(templateData);
    } else {
      console.log('❌ NO TEMPLATE DATA FOUND');
      // Postavi default vrijednosti...
      if (this.isCA()) {
        this.certificateForm.patchValue({ certificateType: 'INTERMEDIATE' });
        this.selectedType = 'INTERMEDIATE';
      } else {
        this.certificateForm.patchValue({ certificateType: 'ROOT' });
        this.selectedType = 'ROOT';
      }
    }

    this.onTypeChange();
  }

  // METODA ZA PRIMJENU TEMPLATE PODATAKA
  private applyTemplateData(templateData: any): void {
    this.templateData = templateData;

    console.log('Applying template data:', templateData);
    
    // Odredi tip sertifikata
    const isCA = templateData.basicConstraints === 'CA:TRUE';
    const certificateType = isCA ? 'INTERMEDIATE' : 'END_ENTITY';
    
    // Postavi osnovne podatke
    this.certificateForm.patchValue({ 
      certificateType: certificateType,
      issuerCertificateId: templateData.caIssuerId,
      basicConstraints: isCA
    });
    this.selectedType = certificateType;
    
    // 👇 AUTOMATSKI POSTAVI DATUME
    const today = new Date();
    const validTo = new Date();
    validTo.setDate(today.getDate() + templateData.maxValidityDays);
    
    this.certificateForm.patchValue({
      validFrom: today,
      validTo: validTo
    });
    
    // Postavi Key Usage
    if (templateData.keyUsage && Array.isArray(templateData.keyUsage)) {
      this.certificateForm.patchValue({
        keyCertSign: templateData.keyUsage[5],
        cRLSign: templateData.keyUsage[6]
      });
    }

    this.setupCNValidation();

    // Obaveštenje korisniku
    setTimeout(() => {
      this.showSuccess(`Template applied! Certificate type: ${certificateType}`);
    }, 100);
    
    this.onTypeChange();
  }

  private setupCNValidation(): void {
  const cnControl = this.certificateForm.get('subjectCommonName');
  
  if (cnControl && this.templateData?.commonNameRegex) {
    console.log('🔧 Setting up CN validation with pattern:', this.templateData.commonNameRegex);
    
    cnControl.valueChanges.subscribe(cn => {
      console.log('🔍 CN value changed:', cn);
      
      if (cn && this.templateData?.commonNameRegex) {
        try {
          const regex = new RegExp(this.templateData.commonNameRegex);
          const isValid = regex.test(cn);
          
          console.log(`🔍 Validation result: ${isValid} for "${cn}" vs pattern "${this.templateData.commonNameRegex}"`);
          
          if (!isValid) {
            console.warn(`⚠️ Common Name "${cn}" doesn't match template pattern: ${this.templateData.commonNameRegex}`);
            cnControl.setErrors({ patternMismatch: true });
            console.log('🔍 Errors set on control:', cnControl.errors);
          } else {
            // Očisti grešku ako je validno
            if (cnControl.hasError('patternMismatch')) {
              cnControl.setErrors(null);
              console.log('🔍 Errors cleared from control');
            }
          }
        } catch (error) {
          console.error('🔧 Regex error:', error);
        }
      } else {
        console.log('🔍 No CN value or no regex pattern');
      }
    });
    
    const currentCN = cnControl.value;
    if (currentCN && this.templateData?.commonNameRegex) {
      console.log('🔧 Running initial validation for:', currentCN);
      const regex = new RegExp(this.templateData.commonNameRegex);
      const isValid = regex.test(currentCN);
      if (!isValid) {
        cnControl.setErrors({ patternMismatch: true });
      }
    }
  } else {
    console.log('❌ Cannot setup CN validation - missing control or regex');
  }
}

  private showSuccess(message: string): void {
    console.log('SUCCESS:', message);
    // this.snackBar.open(message, 'Close', { duration: 5000 });
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
    return this.fb.group({
      // Certificate Type
      certificateType: ['ROOT', Validators.required],
      // za cuvanje ID-a izabranog izdavaoca
      issuerCertificateId: [null],
      // Subject Information
      subjectCommonName: ['', Validators.required],
      subjectOrganization: ['', Validators.required],
      subjectOrganizationalUnit: [''],
      subjectCountry: ['', [Validators.required, Validators.maxLength(2)]],
      subjectState: [''],
      subjectLocality: [''],
      subjectEmail: ['', Validators.email],
      
      // Validity Period
      validFrom: ['', Validators.required],
      validTo: ['', Validators.required],
      
      // Extensions
      basicConstraints: [true], // CA:TRUE by default for Root
      keyCertSign: [true],
      cRLSign: [true]
    });
  }

  onTypeChange(): void {
    this.selectedType = this.certificateForm.get('certificateType')?.value;
    
    if (this.selectedType === 'INTERMEDIATE' || this.selectedType === 'END_ENTITY') {
      this.loadIssuers(); 
  } else {
       // Za ROOT, lista nam ne treba
        this.issuers = [];
       }

    // Dinamički ažuriraj validaciju based on type
    this.updateValidationBasedOnType();
  }

  // metoda za ucitavanje liste izdavaoca 
  private loadIssuers(): void {
      this.issuersLoading = true;
      this.certificateService.getIssuers().subscribe({ 
        next: (data) => {
          this.issuers = data;
          this.issuersLoading = false;
      },
     error: (err) => {
        console.error('Error loading issuers:', err);
        this.issuersLoading = false;
      }
    });
  }

  private updateValidationBasedOnType(): void {
    const basicConstraintsControl = this.certificateForm.get('basicConstraints');
    const issuerIdControl = this.certificateForm.get('issuerCertificateId');
    
    if (this.selectedType === 'ROOT') {
      // Za ROOT, issuer NIJE potreban
      issuerIdControl?.setValue(null);
      issuerIdControl?.clearValidators();
      
      basicConstraintsControl?.setValue(true);
      basicConstraintsControl?.disable();

    } else { // Za INTERMEDIATE i END_ENTITY
      // Issuer JE OBAVEZAN
      issuerIdControl?.setValidators([Validators.required]);
      
      if (this.selectedType === 'INTERMEDIATE') {
        basicConstraintsControl?.setValue(true);
        basicConstraintsControl?.disable();
      } else { // END_ENTITY
        basicConstraintsControl?.setValue(false);
        basicConstraintsControl?.disable();
      }
    }
    // Obavezno pozovi ovo da se validacija primeni na polje
    issuerIdControl?.updateValueAndValidity();
  }


  onSubmit(): void {
    if (this.certificateForm.invalid) {
      this.certificateForm.markAllAsTouched(); // Pokaži greške ako forma nije validna
      return;
    }

    this.isLoading = true;
    const formValue = this.certificateForm.getRawValue(); // Uzima i vrednosti iz onemogućenih polja
    
    const request: CreateCertificateDTO = {
      subjectCommonName: formValue.subjectCommonName,
      subjectOrganization: formValue.subjectOrganization,
      subjectOrganizationalUnit: formValue.subjectOrganizationalUnit,
      subjectCountry: formValue.subjectCountry,
      subjectState: formValue.subjectState,
      subjectLocality: formValue.subjectLocality,
      subjectEmail: formValue.subjectEmail,
      validFrom: new Date(formValue.validFrom).toISOString(),
      validTo: new Date(formValue.validTo).toISOString(),
      keyUsage: this.getKeyUsageArray(formValue),
      basicConstraints: formValue.basicConstraints ? 'CA:TRUE' : 'CA:FALSE',
      extendedKeyUsage: '',
      issuerCertificateId: formValue.issuerCertificateId // Sada se uzima vrednost iz forme
    };

    let apiCall;

    if (this.selectedType === 'ROOT') {
      apiCall = this.certificateService.createRootCertificate(request);
    } else { // Za INTERMEDIATE (i kasnije za End-Entity)
      apiCall = this.certificateService.createIntermediateCertificate(request);
    }

    apiCall.subscribe({
      next: (response) => {
        this.isLoading = false;
        this.successData = response; 
        this.showSuccessMessage = true; 
      },
      error: (error) => {
        this.isLoading = false;
        console.error('Error creating certificate:', error);
        // TODO: Prikazati grešku korisniku (npr. Snackbar poruka)
      }
    });
  }

  private getKeyUsageArray(formValue: any): boolean[] {
    // Key Usage bits: [digitalSignature, keyEncipherment, keyAgreement, keyCertSign, cRLSign, ...]
    return [
      false, // digitalSignature
      false, // keyEncipherment  
      false, // keyAgreement
      formValue.keyCertSign, // keyCertSign
      formValue.cRLSign,     // cRLSign
      false, false, false, false // remaining bits
    ];
  }
onDownloadCertificate(): void {
    if (this.successData?.certificateId) {
      this.certificateService.downloadCertificate(this.successData.certificateId).subscribe({
        next: (blob) => {
          // Kreiraj download link
          const url = window.URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = `certificate_${this.successData.serialNumber}.pem`;
          a.click();
          window.URL.revokeObjectURL(url);
        },
        error: (error) => {
          console.error('Error downloading certificate:', error);
        }
      });
    }
  }

  onBackToList(): void {
    this.showSuccessMessage = false;
    this.router.navigate(['/home']);
  }

  onCreateAnother(): void {
    this.showSuccessMessage = false;
    this.certificateForm.reset();
    this.ngOnInit(); // Ponovo pozovi ngOnInit da se forma ispravno resetuje na osnovu uloge
  }

  extractCommonName(subject: string): string {
    if (!subject) {
      return 'Unknown Issuer';
    }
    // Koristi regularni izraz da pronađeš vrednost posle "CN="
    const cnMatch = subject.match(/CN=([^,]+)/);
    return cnMatch ? cnMatch[1] : 'Unnamed Certificate';
  }
}