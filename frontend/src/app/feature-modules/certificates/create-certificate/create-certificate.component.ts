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
    
    // Odredi tip sertifikata (End-Entity ako je basicConstraints CA:FALSE)
    const isCA = templateData.basicConstraints.toUpperCase() === 'CA:TRUE';
    const certificateType = isCA ? 'INTERMEDIATE' : 'END_ENTITY';
    
    // Postavi osnovne podatke
    const today = new Date();
    const validTo = new Date();
    validTo.setDate(today.getDate() + templateData.maxValidityDays);
    
    // 💡 AŽURIRANI patchValue za sve ekstenzije
    this.certificateForm.patchValue({ 
        certificateType: certificateType,
        issuerCertificateId: templateData.caIssuerId,
        validFrom: today,
        validTo: validTo,
        
        basicConstraints: isCA, // Boolean
        extendedKeyUsage: templateData.extendedKeyUsage, // String
        
        // Key Usage Booleani (Pretpostavljamo da templateData.keyUsage ima 9 booleana)
        digitalSignature: templateData.keyUsage[0],
        nonRepudiation: templateData.keyUsage[1],
        keyEncipherment: templateData.keyUsage[2],
        dataEncipherment: templateData.keyUsage[3],
        keyAgreement: templateData.keyUsage[4],
        keyCertSign: templateData.keyUsage[5], 
        cRLSign: templateData.keyUsage[6], 
        encipherOnly: templateData.keyUsage[7],
        decipherOnly: templateData.keyUsage[8],
    });
    
    this.selectedType = certificateType;
    this.setupCNValidation();
    this.onTypeChange(); // Postavlja validaciju za Issuer
    this.disableTemplateFields(); // Onemogući fiksna polja
    
    setTimeout(() => {
      this.showSuccess(`Template applied! Certificate type: ${certificateType}`);
    }, 100);
}
private disableTemplateFields(): void {
    // Onemogući sva polja koja su fiksirana šablonom
    this.certificateForm.get('issuerCertificateId')?.disable();
    this.certificateForm.get('validFrom')?.disable();
    this.certificateForm.get('validTo')?.disable();
    this.certificateForm.get('certificateType')?.disable();
    this.certificateForm.get('basicConstraints')?.disable();
    
    // Onemogući sve ekstenzije!
    this.certificateForm.get('digitalSignature')?.disable();
    this.certificateForm.get('nonRepudiation')?.disable();
    this.certificateForm.get('keyEncipherment')?.disable();
    this.certificateForm.get('dataEncipherment')?.disable();
    this.certificateForm.get('keyAgreement')?.disable();
    this.certificateForm.get('keyCertSign')?.disable();
    this.certificateForm.get('cRLSign')?.disable();
    this.certificateForm.get('encipherOnly')?.disable();
    this.certificateForm.get('decipherOnly')?.disable();
    this.certificateForm.get('extendedKeyUsage')?.disable(); 
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
      // SVI Key Usage bitovi iz tvoje getKeyUsageArray u Template component-i
        digitalSignature: [false], // [0]
        nonRepudiation: [false],   // [1]
        keyEncipherment: [false],  // [2]
        dataEncipherment: [false], // [3]
        keyAgreement: [false],     // [4]
        keyCertSign: [true],       // [5] (ostaje zbog ROOT/INTERMEDIATE)
        cRLSign: [true],           // [6] (ostaje zbog ROOT/INTERMEDIATE)
        encipherOnly: [false],     // [7]
        decipherOnly: [false],     // [8]
        
        // Extended Key Usage (kao string)
        extendedKeyUsage: [''], // OVO JE NOVO DODATO POLJE
    });
  }

  onTypeChange(): void {
    this.selectedType = this.certificateForm.get('certificateType')?.value;
    
    if (this.selectedType === 'INTERMEDIATE') {
      // Ako pravimo INTERMEDIATE, prikaže SAMO ROOT sertifikate
      this.loadIssuers(true); 
    } else if (this.selectedType === 'END_ENTITY') {
      // Ako pravimo END_ENTITY,  prikaže SVE (i Root i Intermediate)
      this.loadIssuers(false);
    } else {
      // Za ROOT, lista nam ne treba
      this.issuers = [];
    }

    // Dinamički ažuriraj validaciju based on type
    this.updateValidationBasedOnType();
  }

  // metoda za ucitavanje liste izdavaoca 
  private loadIssuers(filterForRootOnly: boolean): void {
    this.issuersLoading = true;
    this.certificateService.getIssuers().subscribe({ 
      next: (data) => {
        if (filterForRootOnly) {
          // Ako je `filterForRootOnly` true, zadrži samo one sertifikate čiji je tip 'ROOT'
          this.issuers = data.filter(cert => cert.type === 'ROOT');
        } else {
          this.issuers = data;
        }

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
      extendedKeyUsage: formValue.extendedKeyUsage,
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
    // Koristi vrednosti iz forme (koje su popunjene šablonom)
    return [
      formValue.digitalSignature, // 0
      formValue.nonRepudiation,   // 1
      formValue.keyEncipherment,  // 2
      formValue.dataEncipherment, // 3
      formValue.keyAgreement,     // 4
      formValue.keyCertSign,      // 5
      formValue.cRLSign,          // 6
      formValue.encipherOnly,     // 7
      formValue.decipherOnly      // 8
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