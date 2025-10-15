import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';

// Angular Material imports
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';

import { CertificatesRoutingModule } from './certificates-routing.module';
import { CreateCertificateComponent } from './create-certificate/create-certificate.component';

@NgModule({
  declarations: [
    CreateCertificateComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,  
    CertificatesRoutingModule,
    
    // Angular Material modules
    MatCardModule,
    MatIconModule, 
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatButtonModule
  ]
})
export class CertificatesModule { }