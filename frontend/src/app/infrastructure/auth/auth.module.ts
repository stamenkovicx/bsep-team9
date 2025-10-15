import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LoginComponent } from './login/login.component';
import { MaterialModule } from '../material/material.module';
import { ReactiveFormsModule } from '@angular/forms';
import { RegistrationComponent } from './registration/registration.component';
import { RecaptchaModule, RecaptchaFormsModule } from 'ng-recaptcha';
import { MatCardModule } from '@angular/material/card';
import { RouterModule } from '@angular/router';




@NgModule({
  declarations: [
    LoginComponent,
    RegistrationComponent
  ],
  imports: [
    CommonModule,
    MaterialModule,
    ReactiveFormsModule,
    RecaptchaModule,
    RecaptchaFormsModule,
    MatCardModule,
    RouterModule
  ],
  exports: [
    LoginComponent,
    MatCardModule
  ]
})
export class AuthModule { }
