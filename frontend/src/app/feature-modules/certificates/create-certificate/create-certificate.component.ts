import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { CertificateService } from '../certificate.service';
import { CreateCertificateDTO } from '../models/create-certificate.dto';
import { AuthService } from 'src/app/infrastructure/auth/auth.service';
import { User } from 'src/app/infrastructure/auth/model/user.model';

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

  constructor(
    private fb: FormBuilder,
    private certificateService: CertificateService,
    private router: Router,
    private authService: AuthService
  ) {
    this.certificateForm = this.createForm();
  }

  ngOnInit(): void {
    this.loadCurrentUser();

    if (this.isCA()) {
      this.certificateForm.patchValue({ certificateType: 'INTERMEDIATE' });
      this.selectedType = 'INTERMEDIATE';
    } else {
      this.certificateForm.patchValue({ certificateType: 'ROOT' });
      this.selectedType = 'ROOT';
    }

    this.onTypeChange();
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
    
    // Dinamički ažuriraj validaciju based on type
    this.updateValidationBasedOnType();
  }

  private updateValidationBasedOnType(): void {
    const basicConstraintsControl = this.certificateForm.get('basicConstraints');
    
    if (this.selectedType === 'ROOT' || this.selectedType === 'INTERMEDIATE') {
      // Force CA:TRUE for Root and Intermediate
      basicConstraintsControl?.setValue(true);
      basicConstraintsControl?.disable();
    } else {
      // End Entity - CA:FALSE
      basicConstraintsControl?.setValue(false);
      basicConstraintsControl?.disable();
    }
  }

  onSubmit(): void {
    if (this.certificateForm.valid) {
      this.isLoading = true;
      
      const formValue = this.certificateForm.value;
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
        basicConstraints: this.selectedType === 'END_ENTITY' ? 'CA:FALSE' : 'CA:TRUE',
        extendedKeyUsage: '',
        issuerCertificateId: null
      };

      if (this.selectedType === 'ROOT') {
        this.certificateService.createRootCertificate(request).subscribe({
        next: (response) => {
          this.isLoading = false;
          console.log('Certificate created:', response);
          this.successData = response; 
          this.showSuccessMessage = true; 
        },
        error: (error) => {
          this.isLoading = false;
          console.error('Error creating certificate:', error);
          // TODO: Show error message
        }
      });
    } // dodati za ostale tipove
    }
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
    this.certificateForm.patchValue({
      certificateType: 'ROOT',
      basicConstraints: true,
      keyCertSign: true,
      cRLSign: true
    });
    this.selectedType = 'ROOT';
  }
}