import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CertificateTemplatesService } from '../certificate-templates.service';
import { CertificateTemplate } from '../models/certificate-template.interface';
import { CreateTemplateDTO } from '../models/create-template.dto';

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
    private snackBar: MatSnackBar
  ) {
    this.templateForm = this.createTemplateForm();
  }

  ngOnInit(): void {
    this.loadTemplates();
    this.loadCaIssuers();
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
    // Ovdje treba da pozoveš service da dobiješ CA issuere
    // Za sada mock podaci
    this.caIssuers = [
      { id: 1, name: 'My Root CA' },
      { id: 2, name: 'Intermediate CA 1' }
    ];
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
      caIssuerId: 14,
      commonNameRegex: formValue.commonNameRegex,
      sansRegex: formValue.sansRegex,
      maxValidityDays: formValue.maxValidityDays,
      keyUsage: this.getKeyUsageArray(formValue.keyUsage),
      extendedKeyUsage: formValue.extendedKeyUsage,
      basicConstraints: formValue.basicConstraints
    };

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
    const usages = keyUsageString.split(',');
    return [
      usages.includes('digitalSignature'),
      usages.includes('keyEncipherment'),
      usages.includes('keyAgreement'),
      usages.includes('keyCertSign'),
      usages.includes('cRLSign'),
      false, false, false, false
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
      next: () => {
        this.showSuccess(`Template "${template.name}" used successfully!`);
      },
      error: (error) => {
        console.error('Error using template:', error);
        this.showError('Failed to use template');
      }
    });
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