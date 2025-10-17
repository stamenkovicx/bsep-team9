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
  selectedTemplateType: 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY' = 'ROOT';

  constructor(
    private fb: FormBuilder,
    private templatesService: CertificateTemplatesService,
    private snackBar: MatSnackBar
  ) {
    this.templateForm = this.createTemplateForm();
  }

  ngOnInit(): void {
    this.loadTemplates();
  }

  createTemplateForm(): FormGroup {
    return this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3)]],
      description: [''],
      certificateType: ['ROOT' as 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY', Validators.required],
      
      // Subject Information
      subjectCommonName: ['', Validators.required],
      subjectOrganization: ['', Validators.required],
      subjectOrganizationalUnit: [''],
      subjectCountry: ['', [Validators.required, Validators.maxLength(2)]],
      subjectState: [''],
      subjectLocality: [''],
      subjectEmail: ['', Validators.email],
      
      // Validity
      validityDays: [365, [Validators.required, Validators.min(1), Validators.max(3650)]],
      
      // Extensions
      basicConstraints: [true],
      digitalSignature: [false],
      keyEncipherment: [false],
      keyAgreement: [false],
      keyCertSign: [true],
      cRLSign: [true]
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

  onTemplateTypeChange(): void {
    const type = this.templateForm.get('certificateType')?.value;
    this.selectedTemplateType = type as 'ROOT' | 'INTERMEDIATE' | 'END_ENTITY';
    this.updateFormValidation();
  }

  private updateFormValidation(): void {
    const basicConstraintsControl = this.templateForm.get('basicConstraints');
    const keyCertSignControl = this.templateForm.get('keyCertSign');
    const cRLSignControl = this.templateForm.get('cRLSign');

    if (this.selectedTemplateType === 'ROOT' || this.selectedTemplateType === 'INTERMEDIATE') {
      basicConstraintsControl?.setValue(true);
      keyCertSignControl?.setValue(true);
      cRLSignControl?.setValue(true);
    } else { // END_ENTITY
      basicConstraintsControl?.setValue(false);
      keyCertSignControl?.setValue(false);
      cRLSignControl?.setValue(false);
    }
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
      certificateType: formValue.certificateType,
      caIssuerId: 14,
      subjectCommonName: formValue.subjectCommonName,
      subjectOrganization: formValue.subjectOrganization,
      subjectOrganizationalUnit: formValue.subjectOrganizationalUnit,
      subjectCountry: formValue.subjectCountry,
      subjectState: formValue.subjectState,
      subjectLocality: formValue.subjectLocality,
      subjectEmail: formValue.subjectEmail,
      maxValidityDays: formValue.validityDays,
      basicConstraints: formValue.basicConstraints,
      keyUsage: [
        formValue.digitalSignature,
        formValue.keyEncipherment,
        formValue.keyAgreement,
        formValue.keyCertSign,
        formValue.cRLSign,
        false, false, false, false // remaining bits
      ]
    };

    // FIX: Kreiraj CertificateTemplate objekat sa ispravnim tipom
    const updateData: CertificateTemplate = {
      id: this.currentTemplateId!,
      name: templateData.name,
      description: templateData.description,
      certificateType: templateData.certificateType,
      subjectCommonName: templateData.subjectCommonName,
      subjectOrganization: templateData.subjectOrganization,
      subjectOrganizationalUnit: templateData.subjectOrganizationalUnit,
      subjectCountry: templateData.subjectCountry,
      subjectState: templateData.subjectState,
      subjectLocality: templateData.subjectLocality,
      subjectEmail: templateData.subjectEmail,
      maxValidityDays: templateData.maxValidityDays,
      basicConstraints: templateData.basicConstraints,
      keyUsage: {
        digitalSignature: templateData.keyUsage[0],
        keyEncipherment: templateData.keyUsage[1],
        keyAgreement: templateData.keyUsage[2],
        keyCertSign: templateData.keyUsage[3],
        cRLSign: templateData.keyUsage[4]
      }
    };

    const apiCall = this.isEditing && this.currentTemplateId
      ? this.templatesService.updateTemplate(this.currentTemplateId, updateData)
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
        this.showError('Failed to save template');
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
      caIssuerId: 14,
      certificateType: template.certificateType,
      subjectCommonName: template.subjectCommonName,
      subjectOrganization: template.subjectOrganization,
      subjectOrganizationalUnit: template.subjectOrganizationalUnit,
      subjectCountry: template.subjectCountry,
      subjectState: template.subjectState,
      subjectLocality: template.subjectLocality,
      subjectEmail: template.subjectEmail,
      maxValidityDays: template.maxValidityDays,
      basicConstraints: template.basicConstraints,
      digitalSignature: template.keyUsage.digitalSignature,
      keyEncipherment: template.keyUsage.keyEncipherment,
      keyAgreement: template.keyUsage.keyAgreement,
      keyCertSign: template.keyUsage.keyCertSign,
      cRLSign: template.keyUsage.cRLSign
    });

    this.selectedTemplateType = template.certificateType;
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
      certificateType: 'ROOT',
      validityDays: 365,
      basicConstraints: true,
      keyCertSign: true,
      cRLSign: true
    });
    this.isEditing = false;
    this.currentTemplateId = undefined;
    this.showForm = false;
    this.selectedTemplateType = 'ROOT';
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

  getTemplateIcon(certificateType: string): string {
    switch (certificateType) {
      case 'ROOT':
        return 'security';
      case 'INTERMEDIATE':
        return 'verified_user';
      case 'END_ENTITY':
        return 'person';
      default:
        return 'description';
    }
  }
}