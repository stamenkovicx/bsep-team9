import { Component, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { AuthService } from '../auth.service';
import { Router } from '@angular/router';
import { Login } from '../model/login.model';
import { environment } from 'src/env/environment';
import { HttpErrorResponse } from '@angular/common/http';
import { LoginPayload } from '../model/LoginPayload';

@Component({
  selector: 'xp-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {


  siteKey: string = environment.recaptchaSiteKey;
  private savedRecaptchaToken: string | null = null;

  twoFactorRequired: boolean = false;   //Kontroliše prikaz 2FA polja u HTML-u
  twoFactorCodeControl = new FormControl(''); // Za unos 2FA koda



  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  loginForm = new FormGroup({
    email: new FormControl('', [Validators.required, Validators.email]), 
    password: new FormControl('', [Validators.required]),
    recaptchaToken: new FormControl('', [Validators.required]),
  });

  login(): void {
    if (this.loginForm.valid) {
      
      const formValue = this.loginForm.value;
      const recaptchaToken = this.savedRecaptchaToken || formValue.recaptchaToken!;
      const loginPayload: LoginPayload = {
        email: formValue.email!,
        password: formValue.password!,
        recaptchaToken: recaptchaToken, 
        // Dodajemo 2FA kod. On je string, ili null ako nije zatražen.
        twoFactorCode: this.twoFactorRequired ? this.twoFactorCodeControl.value : null
      }
      console.log('📤 Sending login payload:', loginPayload);


      this.authService.login(loginPayload).subscribe({
        next: () => {
          console.log('✅ Login success');
          this.router.navigate(['/home']);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 401 && (err.error as any).twoFactorRequired) {
            this.savedRecaptchaToken = recaptchaToken;
            this.twoFactorRequired = true; 
            
            this.twoFactorCodeControl.setValidators([
                Validators.required, 
                Validators.minLength(6), 
                Validators.maxLength(6),
                Validators.pattern(/^\d{6}$/) // Samo 6 cifara
              ]);
            this.twoFactorCodeControl.updateValueAndValidity();

            alert("2FA required. Please enter the code from your authenticator app.");
            
          } else {
            const errorMessage = (err.error as any).message || err.error;
            alert(errorMessage);

            if (this.twoFactorRequired) {
                this.twoFactorCodeControl.reset();
            }
          }
        }
      });
    } else {
      alert('Please fill out all fields and complete the reCAPTCHA.');
    }
  }
}