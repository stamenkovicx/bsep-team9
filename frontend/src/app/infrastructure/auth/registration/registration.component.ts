import { Component } from '@angular/core';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { Registration } from '../model/registration.model';
import { AuthService } from '../auth.service';
import { Router } from '@angular/router';
import { User } from '../model/user.model';

@Component({
  selector: 'xp-registration',
  templateUrl: './registration.component.html',
  styleUrls: ['./registration.component.css']
})
export class RegistrationComponent {

  passwordStrength = {
    percentage: 0,
    label: '',
    class: ''
  };

  currentUser: User | null = null;

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadCurrentUser();

    // DINAMICKA VALIDACIJA
    this.authService.user$.subscribe(user => {
      this.currentUser = user;
      this.updateFormValidation();
    });
  }

  private updateFormValidation(): void {
    const passwordControl = this.registrationForm.get('password');
    const confirmPasswordControl = this.registrationForm.get('confirmPassword');

    if (this.isAdmin()) {
      // Admin: password polja NISU obavezna
      passwordControl?.clearValidators();
      confirmPasswordControl?.clearValidators();
    } else {
      // Običan korisnik: password polja SU obavezna
      passwordControl?.setValidators([Validators.required]);
      confirmPasswordControl?.setValidators([Validators.required]);
    }

    passwordControl?.updateValueAndValidity();
    confirmPasswordControl?.updateValueAndValidity();
  }

  loadCurrentUser(): void {
    this.authService.user$.subscribe(user => {
      this.currentUser = user;
    });
  }

  registrationForm = new FormGroup({
    name: new FormControl('', [Validators.required]),
    surname: new FormControl('', [Validators.required]),
    email: new FormControl('', [Validators.required]),
    organization: new FormControl(''),
    password: new FormControl(''),
    confirmPassword: new FormControl(''),
  });

  register(event?: Event): void {
     // Ako je admin prijavljen, koristi CA registraciju
    if (this.isAdmin()) {
      this.registerCA(event);
      return;
    }

    // Spreci default browser form submission
    if (event) {
      event.preventDefault();
    }

    if (this.registrationForm.invalid) {
      alert('Please fill out all required fields correctly.');
      return;
    }

    const registration: Registration = {
      name: this.registrationForm.value.name || "",
      surname: this.registrationForm.value.surname || "",
      email: this.registrationForm.value.email || "",
      password: this.registrationForm.value.password || "",
      confirmPassword: this.registrationForm.value.confirmPassword || "",
      organization: this.registrationForm.value.organization || "",
    };

    if (this.registrationForm.valid) {
      //da li se lozinke poklapaju 
      if (registration.password !== registration.confirmPassword) {
        alert("Passwords do not match!");
        return;
    }
    this.authService.register(registration).subscribe({
      next: (response) => {
        alert(response.message); 
        this.router.navigate(['/login']); 
      },
      error: (err) => {
        console.error('Registration error:', err); // Za debugging
        
        // Proveri da li postoji err.error.message (JSON odgovor)
        let errorMessage = 'Registration failed';
        
        if (err.error) {
          if (typeof err.error === 'string') {
            errorMessage = err.error; // Obican string
          } else if (err.error.message) {
            errorMessage = err.error.message; // JSON objekat sa message poljem
          }
        }
        
        alert(errorMessage);
      }
    });
    }
  }

  registerCA(event?: Event): void {
    // Spreci default browser form submission
    if (event) {
      event.preventDefault();
    }

     if (this.registrationForm.get('name')?.invalid || 
      this.registrationForm.get('surname')?.invalid || 
      this.registrationForm.get('email')?.invalid) {
        alert('Please fill out all required fields correctly.');
        return;
      }

    // Za CA registraciju ne trebaju password polja u payloadu
    const caRegistration = {
      name: this.registrationForm.value.name || "",
      surname: this.registrationForm.value.surname || "",
      email: this.registrationForm.value.email || "",
      organization: this.registrationForm.value.organization || "",
    };

    this.authService.registerCA(caRegistration).subscribe({
      next: (response) => {
        alert(response.message); 
        this.registrationForm.reset(); // Resetuj formu nakon uspešne registracije
      },
      error: (err) => {
        console.error('CA Registration error:', err);
        
        let errorMessage = 'CA Registration failed';
        if (err.error) {
          if (typeof err.error === 'string') {
            errorMessage = err.error;
          } else if (err.error.message) {
            errorMessage = err.error.message;
          }
        }
        
        alert(errorMessage);
      }
    });
  }

  onPasswordChange(): void {
    const password = this.registrationForm.get('password')?.value || '';
    this.passwordStrength = this.calculatePasswordStrength(password);
  }

  calculatePasswordStrength(password: string): any {
    let strength = 0;
    
    if (password.length >= 8) strength += 20;
    if (password.length >= 12) strength += 10;
    if (/[a-z]/.test(password)) strength += 20;
    if (/[A-Z]/.test(password)) strength += 20;
    if (/[0-9]/.test(password)) strength += 15;
    if (/[^a-zA-Z0-9]/.test(password)) strength += 15;
    
    if (strength < 40) {
      return { percentage: strength, label: 'Weak', class: 'weak' };
    } else if (strength < 60) {
      return { percentage: strength, label: 'Fair', class: 'fair' };
    } else if (strength < 80) {
      return { percentage: strength, label: 'Good', class: 'good' };
    } else {
      return { percentage: 100, label: 'Strong', class: 'strong' };
    }
  }

  isAdmin(): boolean {
    return this.currentUser?.role === 'ADMIN'
  }
  passwordsMatch(): boolean {
    const password = this.registrationForm.get('password')?.value || '';
    const confirmPassword = this.registrationForm.get('confirmPassword')?.value || '';
    
    if (!password || !confirmPassword) {
      return false;
    }
    
    return password === confirmPassword;
  }
  
}
