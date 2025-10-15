import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LoginComponent } from './login/login.component';
import { MaterialModule } from '../material/material.module';
import { ReactiveFormsModule } from '@angular/forms';
import { RegistrationComponent } from './registration/registration.component';
import { RecaptchaModule, RecaptchaFormsModule } from 'ng-recaptcha';



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
    RecaptchaFormsModule
  ],
  exports: [
    LoginComponent
  ]
})
export class AuthModule { }
