import { Component } from '@angular/core';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { Registration } from '../model/registration.model';
import { AuthService } from '../auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'xp-registration',
  templateUrl: './registration.component.html',
  styleUrls: ['./registration.component.css']
})
export class RegistrationComponent {

  constructor(
    private authService: AuthService,
    private router: Router
  ) {}

  registrationForm = new FormGroup({
    name: new FormControl('', [Validators.required]),
    surname: new FormControl('', [Validators.required]),
    email: new FormControl('', [Validators.required]),
    organization: new FormControl(''),
    password: new FormControl('', [Validators.required]),
    confirmPassword: new FormControl('', [Validators.required]),
  });

  register(event?: Event): void {
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
}
