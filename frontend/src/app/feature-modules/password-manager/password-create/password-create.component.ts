import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PasswordManagerService } from '../password-manager.service';
import { CreatePasswordEntryDTO } from '../model/password-manager.model';

@Component({
  selector: 'app-password-create',
  templateUrl: './password-create.component.html',
  styleUrls: ['./password-create.component.css']
})
export class PasswordCreateComponent implements OnInit {
  passwordForm: FormGroup;
  isEditMode = false;
  isLoading = false;
  hidePassword = true;
  passwordId?: number;

  constructor(
    private fb: FormBuilder,
    private passwordService: PasswordManagerService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar
  ) {
    this.passwordForm = this.createForm();
  }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      if (params['id']) {
        this.isEditMode = true;
        this.passwordId = +params['id'];
        this.loadPasswordForEdit(this.passwordId);
      }
    });
  }

  createForm(): FormGroup {
    return this.fb.group({
      siteName: ['', [Validators.required, Validators.minLength(2)]],
      username: ['', [Validators.required, Validators.minLength(2)]],
      password: ['', [Validators.required, Validators.minLength(4)]],
      notes: ['']
    });
  }

  loadPasswordForEdit(id: number): void {
    this.isLoading = true;
    // TODO: Implementiraj dobijanje postojećeg passworda za edit
    // Za sada ćemo simulirati
    setTimeout(() => {
      this.passwordForm.patchValue({
        siteName: 'Example Site',
        username: 'user@example.com',
        password: 'current-password',
        notes: 'This is an example note'
      });
      this.isLoading = false;
    }, 500);
  }

  onSubmit(): void {
    if (this.passwordForm.valid) {
      this.isLoading = true;
      
      const formData: CreatePasswordEntryDTO = this.passwordForm.value;

      if (this.isEditMode && this.passwordId) {
        // Update postojećeg passworda
        this.passwordService.updatePassword(this.passwordId, formData).subscribe({
          next: () => {
            this.snackBar.open('Password updated successfully', 'Close', { duration: 3000 });
            this.router.navigate(['/password-manager/passwords']);
          },
          error: (error) => {
            console.error('Error updating password:', error);
            this.snackBar.open('Error updating password', 'Close', { duration: 3000 });
            this.isLoading = false;
          }
        });
      } else {
        // Kreiraj novi password
        this.passwordService.createPassword(formData).subscribe({
          next: () => {
            this.snackBar.open('Password created successfully', 'Close', { duration: 3000 });
            this.router.navigate(['/password-manager/passwords']);
          },
          error: (error) => {
            console.error('Error creating password:', error);
            this.snackBar.open('Error creating password', 'Close', { duration: 3000 });
            this.isLoading = false;
          }
        });
      }
    }
  }

  generatePassword(): void {
    const length = 16;
    const charset = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*';
    let password = '';
    
    for (let i = 0; i < length; i++) {
      password += charset.charAt(Math.floor(Math.random() * charset.length));
    }
    
    this.passwordForm.patchValue({ password });
    this.hidePassword = false;
    
    this.snackBar.open('Strong password generated!', 'Close', { duration: 2000 });
  }

  getPasswordStrengthClass(): string {
    const password = this.passwordForm.get('password')?.value;
    if (!password) return 'strength-none';
    
    const strength = this.calculatePasswordStrength(password);
    
    if (strength < 3) return 'strength-weak';
    if (strength < 5) return 'strength-medium';
    if (strength < 7) return 'strength-good';
    return 'strength-strong';
  }

  getPasswordStrengthText(): string {
    const password = this.passwordForm.get('password')?.value;
    if (!password) return 'No password';
    
    const strength = this.calculatePasswordStrength(password);
    
    if (strength < 3) return 'Weak';
    if (strength < 5) return 'Medium';
    if (strength < 7) return 'Good';
    return 'Strong';
  }

  private calculatePasswordStrength(password: string): number {
    let strength = 0;
    
    // Length check
    if (password.length >= 8) strength += 1;
    if (password.length >= 12) strength += 1;
    
    // Character variety checks
    if (/[a-z]/.test(password)) strength += 1;
    if (/[A-Z]/.test(password)) strength += 1;
    if (/[0-9]/.test(password)) strength += 1;
    if (/[^a-zA-Z0-9]/.test(password)) strength += 1;
    
    return strength;
  }
}