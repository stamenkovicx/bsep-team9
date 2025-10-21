import { Component } from '@angular/core';
import { FormGroup, FormControl, Validators, AbstractControl } from '@angular/forms';
import { AuthService } from '../auth.service';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
  selector: 'xp-change-password-required',
  templateUrl: './change-password-required.component.html',
  styleUrls: ['./change-password-required.component.css']
})
export class ChangePasswordRequiredComponent {

  passwordForm = new FormGroup({
    newPassword: new FormControl('', [Validators.required]),
    confirmPassword: new FormControl('', [Validators.required])
  }, { validators: this.passwordMatchValidator });

  passwordStrength = {
    percentage: 0,
    label: '',
    class: ''
  };
  isLoading = false;

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  // Validator za poklapanje lozinki
  passwordMatchValidator(control: AbstractControl) {
    const formGroup = control as FormGroup;
    const newPassword = formGroup.get('newPassword')?.value;
    const confirmPassword = formGroup.get('confirmPassword')?.value;
    return newPassword === confirmPassword ? null : { mismatch: true };
  }

  // Event handler kada korisnik menja lozinku
  onPasswordChange() {
    const password = this.passwordForm.get('newPassword')?.value || '';
    this.passwordStrength = this.calculatePasswordStrength(password);
  }

  // Funkcija za procenu jačine lozinke
  calculatePasswordStrength(password: string): any {
    let strength = 0;
    if (password.length >= 8) strength += 20;
    if (password.length >= 12) strength += 10;
    if (/[a-z]/.test(password)) strength += 20;
    if (/[A-Z]/.test(password)) strength += 20;
    if (/[0-9]/.test(password)) strength += 15;
    if (/[^a-zA-Z0-9]/.test(password)) strength += 15;
    
    if (strength < 40) return { percentage: strength, label: 'Weak', class: 'weak' };
    else if (strength < 60) return { percentage: strength, label: 'Fair', class: 'fair' };
    else if (strength < 80) return { percentage: strength, label: 'Good', class: 'good' };
    else return { percentage: 100, label: 'Strong', class: 'strong' };
  }

  // Glavna funkcija za promenu lozinke
  changePassword() {
    if (this.passwordForm.invalid) {
      alert('Please fill out all fields correctly.');
      return;
    }

    this.isLoading = true;
    const formValue = this.passwordForm.value;

    // Preuzimamo privremeni token sa localStorage
    const tempToken = localStorage.getItem('tempToken');
    console.log("TEMP TOKEN TO SEND:", tempToken);
    if (!tempToken) {
      alert('No temporary token found. Please login again.');
      this.isLoading = false;
      return;
    }

    this.authService.changePasswordRequired(
      {
        newPassword: formValue.newPassword || '',
        confirmPassword: formValue.confirmPassword || ''
      },
      tempToken // ← Prosleđujemo token u AuthService
    ).subscribe({
      next: (response) => {
        alert('Password changed successfully! Please log in with your new password.');
        localStorage.removeItem('tempToken'); // Brišemo privremeni token
        this.router.navigate(['/login']);
      },
      error: (err: HttpErrorResponse) => {
        console.error('Password change error:', err);
        console.log(tempToken);
        alert(err.error?.message || 'Failed to change password');
        this.isLoading = false;
      }
    });
  }
}
