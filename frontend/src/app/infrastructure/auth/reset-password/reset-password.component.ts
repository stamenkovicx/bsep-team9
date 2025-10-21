import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'xp-reset-password',
  templateUrl: './reset-password.component.html',
  styleUrls: ['./reset-password.component.css']
})
export class ResetPasswordComponent implements OnInit {
  
  token: string = '';
  successMessage: string = '';
  errorMessage: string = '';
  isLoading: boolean = false;

  resetPasswordForm = new FormGroup({
    newPassword: new FormControl('', [Validators.required, Validators.minLength(8)]),
    confirmPassword: new FormControl('', [Validators.required])
  });

  constructor(
    private authService: AuthService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    // Preuzmi token iz URL parametara
    this.route.queryParams.subscribe(params => {
      this.token = params['token']?.trim() || ''; 
      if (!this.token) {
        this.errorMessage = 'Invalid or missing reset token.';
      }
    });
  }

  onSubmit(): void {
    if (this.resetPasswordForm.valid && this.token) {
      
      const newPassword = this.resetPasswordForm.value.newPassword!;
      const confirmPassword = this.resetPasswordForm.value.confirmPassword!;

      // Provera da li se lozinke poklapaju
      if (newPassword !== confirmPassword) {
        this.errorMessage = 'Passwords do not match.';
        return;
      }

      this.isLoading = true;
      this.errorMessage = '';
      this.successMessage = '';

      this.authService.resetPassword(this.token, newPassword, confirmPassword).subscribe({
        next: (response) => {
          this.isLoading = false;
          this.successMessage = response.message;
          
          // Preusmeri na login nakon 3 sekunde
          setTimeout(() => {
            this.router.navigate(['/login']);
          }, 3000);
        },
        error: (err) => {
          this.isLoading = false;
          this.errorMessage = err.error?.message || 'An error occurred. Please try again.';
        }
      });
    }
  }

  goToLogin(): void {
    this.router.navigate(['/login']);
  }
  passwordsMatch(): boolean {
    const newPassword = this.resetPasswordForm.get('newPassword')?.value || '';
    const confirmPassword = this.resetPasswordForm.get('confirmPassword')?.value || '';
    
    if (!newPassword || !confirmPassword) {
      return false;
    }
    
    return newPassword === confirmPassword;
  }
}