import { Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { AuthService } from '../auth.service';
import { Router } from '@angular/router';
import { Login } from '../model/login.model';
import { environment } from 'src/env/environment';

@Component({
  selector: 'xp-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {

  siteKey: string = environment.recaptchaSiteKey;

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
      // Saljem celu vrednost forme, koja sadrzi i recaptchaToken
      this.authService.login(this.loginForm.value as Login).subscribe({
        next: () => {
          this.router.navigate(['/home']); // Preusmeri na početnu stranicu nakon prijave
        },
        error: (err) => {
          // Prikazujemo grešku koju vraća backend
          alert(err.error);
        }
      });
    } else {
      alert('Please fill out all fields and complete the reCAPTCHA.');
    }
  }
}
