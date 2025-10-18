import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CertificateTemplatesService } from '../certificate-templates.service';
import { CertificateTemplate } from '../models/certificate-template.interface';
import { CreateTemplateDTO } from '../models/create-template.dto';
import { CertificateService } from '../../certificates/certificate.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-certificate-templates',
  templateUrl: './certificate-templates.component.html',
  styleUrls: ['./certificate-templates.component.css']
})
export class CertificateTemplatesComponent implements OnInit {
  templates: CertificateTemplate[] = [];
  templateForm: FormGroup;
  isEditing = false;
  currentTemplateId?: number;
  isLoading = false;
  showForm = false;
  caIssuers: any[] = [];

  constructor(
    private fb: FormBuilder,
    private templatesService: CertificateTemplatesService,
    private certificateService: CertificateService, 
    private snackBar: MatSnackBar,
    private router: Router
  ) {
    this.templateForm = this.createTemplateForm();
  }

  ngOnInit(): void {
    this.loadTemplates();
    this.loadCaIssuers();

    this.templateForm.get('keyUsage')?.valueChanges.subscribe(value => {
        const validUsages = ['digitalsignature', 'nonrepudiation', 'contentcommitment', 
                            'keyencipherment', 'dataencipherment', 'keyagreement', 
                            'keycertsign', 'crlsign', 'encipheronly', 'decipheronly'];
        
        const inputUsages = value.toLowerCase().split(',').map((u: string) => u.trim());
        const invalidUsages = inputUsages.filter((u: string) => u && !validUsages.includes(u));
        
        if (invalidUsages.length > 0) {
            console.warn('Invalid key usage values:', invalidUsages);
        }
    });
  }

  createTemplateForm(): FormGroup {
    return this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3)]],
      description: [''],
      caIssuerId: [null, Validators.required],
      commonNameRegex: ['.*'],
      sansRegex: ['.*'],
      maxValidityDays: [365, [Validators.required, Validators.min(1), Validators.max(3650)]],
      keyUsage: ['digitalSignature,keyEncipherment'],
      extendedKeyUsage: ['serverAuth,clientAuth'],
      basicConstraints: ['CA:FALSE']
    });
  }

  loadTemplates(): void {
    this.isLoading = true;
    this.templatesService.getAllTemplates().subscribe({
      next: (templates) => {
        this.templates = templates;
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error loading templates:', error);
        this.showError('Failed to load templates');
        this.isLoading = false;
      }
    });
  }

  loadCaIssuers(): void {
    this.certificateService.getIssuers().subscribe({
      next: (issuers) => {
        this.caIssuers = issuers.filter(issuer => 
            issuer.type === 'ROOT' || issuer.type === 'INTERMEDIATE'
        ).map(issuer => ({
            id: issuer.id,
            name: `${this.extractCommonName(issuer.subject)} (${issuer.type}) - Valid until: ${new Date(issuer.validTo).toLocaleDateString()}`
        }));
        
        console.log('Loaded CA issuers:', this.caIssuers);
      },
      error: (error) => {
        console.error('Error loading CA issuers:', error);
        this.showError('Failed to load CA issuers');
        // Fallback na praznu listu
        this.caIssuers = [];
      }
    });
  }

  private extractCommonName(subject: string): string {
    if (!subject) return 'Unknown Issuer';
    const cnMatch = subject.match(/CN=([^,]+)/);
    return cnMatch ? cnMatch[1] : 'Unnamed Certificate';
  }

  onSubmit(): void {
    if (this.templateForm.invalid) {
      this.templateForm.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    const formValue = this.templateForm.value;

    const templateData: CreateTemplateDTO = {
      name: formValue.name,
      description: formValue.description,
      caIssuerId: formValue.caIssuerId,
      commonNameRegex: formValue.commonNameRegex,
      sansRegex: formValue.sansRegex,
      maxValidityDays: formValue.maxValidityDays,
      keyUsage: this.getKeyUsageArray(formValue.keyUsage),
      extendedKeyUsage: formValue.extendedKeyUsage,
      basicConstraints: formValue.basicConstraints
    };

    console.log('KeyUsage format:', templateData.keyUsage);
    console.log('Full template data:', templateData);

    console.log('Sending template data to backend:', templateData);

    const apiCall = this.isEditing && this.currentTemplateId
      ? this.templatesService.updateTemplate(this.currentTemplateId, templateData)
      : this.templatesService.createTemplate(templateData);

    apiCall.subscribe({
      next: (template) => {
        this.isLoading = false;
        this.showSuccess(
          this.isEditing ? 'Template updated successfully!' : 'Template created successfully!'
        );
        this.resetForm();
        this.loadTemplates();
      },
      error: (error) => {
        console.error('Error saving template:', error);
        this.showError(error.message || 'Failed to save template');
        this.isLoading = false;
      }
    });
  }

  onEditTemplate(template: CertificateTemplate): void {
    this.isEditing = true;
    this.currentTemplateId = template.id;
    this.showForm = true;
    
    this.templateForm.patchValue({
      name: template.name,
      description: template.description,
      caIssuerId: template.caIssuerId,
      commonNameRegex: template.commonNameRegex,
      sansRegex: template.sansRegex,
      maxValidityDays: template.maxValidityDays,
      keyUsage: this.getKeyUsageString(template.keyUsage),
      extendedKeyUsage: template.extendedKeyUsage,
      basicConstraints: template.basicConstraints
    });
  }

  private getKeyUsageString(keyUsage: boolean[]): string {
    const usages = [];
    if (keyUsage[0]) usages.push('digitalSignature');
    if (keyUsage[1]) usages.push('keyEncipherment');
    if (keyUsage[2]) usages.push('keyAgreement');
    if (keyUsage[3]) usages.push('keyCertSign');
    if (keyUsage[4]) usages.push('cRLSign');
    return usages.join(',');
  }

  private getKeyUsageArray(keyUsageString: string): boolean[] {
    if (!keyUsageString) {
        return [false, false, false, false, false, false, false, false, false];
    }
      
    const usages = keyUsageString.toLowerCase().split(',').map(u => u.trim());
      
    return [
        usages.includes('digitalsignature'),        // 0
        usages.includes('nonrepudiation') || usages.includes('contentcommitment'), // 1
        usages.includes('keyencipherment'),         // 2
        usages.includes('dataencipherment'),        // 3
        usages.includes('keyagreement'),            // 4
        usages.includes('keycertsign'),             // 5
        usages.includes('crlsign'),                 // 6
        usages.includes('encipheronly'),            // 7
        usages.includes('decipheronly')             // 8
    ];
  }

  onDeleteTemplate(templateId: number): void {
    if (confirm('Are you sure you want to delete this template?')) {
      this.templatesService.deleteTemplate(templateId).subscribe({
        next: () => {
          this.showSuccess('Template deleted successfully!');
          this.loadTemplates();
        },
        error: (error) => {
          console.error('Error deleting template:', error);
          this.showError('Failed to delete template');
        }
      });
    }
  }

  onUseTemplate(template: CertificateTemplate): void {
    this.templatesService.useTemplate(template.id!).subscribe({
      next: (response: any) => {
        console.log('Template use response:', response);
        
        // Prikaži uspješnu poruku
        this.showSuccess(`Template "${template.name}" loaded successfully!`);
        
        // prebaci korisnika na formu za sertifikate
        // i proslijedi podatke iz šablona
        this.navigateToCertificateForm(response.prefilledData, template);
      },
      error: (error) => {
        console.error('Error using template:', error);
        this.showError('Failed to use template: ' + (error.error?.message || error.message));
      }
    });
  }

  private navigateToCertificateForm(prefilledData: any, template: CertificateTemplate): void {
    // Navigiraj na formu za sertifikate i proslijedi podatke
    this.router.navigate(['/certificates/create'], { 
      queryParams: { 
        templateId: template.id,
        templateName: template.name
      }
    });

    this.templatesService.setCurrentTemplateData(prefilledData);
  }

  resetForm(): void {
    this.templateForm.reset({
      commonNameRegex: '.*',
      sansRegex: '.*',
      maxValidityDays: 365,
      keyUsage: 'digitalSignature,keyEncipherment',
      extendedKeyUsage: 'serverAuth,clientAuth',
      basicConstraints: 'CA:FALSE'
    });
    this.isEditing = false;
    this.currentTemplateId = undefined;
    this.showForm = false;
  }

  private showSuccess(message: string): void {
    this.snackBar.open(message, 'Close', {
      duration: 3000,
      panelClass: ['success-snackbar']
    });
  }

  private showError(message: string): void {
    this.snackBar.open(message, 'Close', {
      duration: 5000,
      panelClass: ['error-snackbar']
    });
  }
}