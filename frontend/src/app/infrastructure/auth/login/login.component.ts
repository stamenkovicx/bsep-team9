import { Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { AuthService } from '../auth.service';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { LoginPayload } from '../model/LoginPayload';
import { environment } from 'src/env/environment';

@Component({
  selector: 'xp-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {

  siteKey: string = environment.recaptchaSiteKey;
  selectedAuthMethod: 'basic' | 'keycloak' = 'basic';
  twoFactorRequired: boolean = false;
  twoFactorCodeControl = new FormControl('');
  private savedRecaptchaToken: string | null = null;

  loginForm = new FormGroup({
    email: new FormControl('', [Validators.required, Validators.email]),
    password: new FormControl('', [Validators.required]),
    recaptchaToken: new FormControl('', [Validators.required]),
  });

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  loginWithBasic(): void {
    if (this.loginForm.valid) {
      const formValue = this.loginForm.value;
      const recaptchaToken = this.savedRecaptchaToken || formValue.recaptchaToken!;
      const loginPayload: LoginPayload = {
        email: formValue.email!,
        password: formValue.password!,
        recaptchaToken: recaptchaToken,
        twoFactorCode: this.twoFactorRequired ? this.twoFactorCodeControl.value : null
      };

      this.authService.loginBasic(loginPayload).subscribe({
        next: (response) => {
          if (response.passwordChangeRequired) {
            this.router.navigate(['/change-password-required']);
          } else {
            this.router.navigate(['/home']);
          }
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 401) {
            const errorData = err.error;
            if (errorData.passwordChangeRequired) {
              if (errorData.token) {
                localStorage.setItem('tempToken', errorData.token);
              }
              this.router.navigate(['/change-password-required']);
              return;
            }

            if (errorData.twoFactorRequired) {
              this.savedRecaptchaToken = recaptchaToken;
              this.twoFactorRequired = true;
              this.twoFactorCodeControl.setValidators([
                Validators.required,
                Validators.minLength(6),
                Validators.maxLength(6),
                Validators.pattern(/^\d{6}$/)
              ]);
              this.twoFactorCodeControl.updateValueAndValidity();
              alert("2FA required. Please enter the code from your authenticator app.");
            } else {
              alert(errorData.message || err.error);
            }
          }
        }
      });
    } else {
      alert('Please fill out all fields and complete the reCAPTCHA.');
    }
  }

  loginWithKeycloak(): void {
    this.authService.loginKeycloak();
  }

  selectAuthMethod(method: 'basic' | 'keycloak'): void {
    this.selectedAuthMethod = method;
    this.twoFactorRequired = false;
    this.twoFactorCodeControl.reset();
  }
}