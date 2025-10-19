import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';

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
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialogModule } from '@angular/material/dialog';

import { CertificatesRoutingModule } from './certificates-routing.module';
import { CreateCertificateComponent } from './create-certificate/create-certificate.component';
import { ChainViewComponent } from './chain-view/chain-view/chain-view.component';
import { MatTreeModule } from '@angular/material/tree';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CreateEeCsrComponent } from './create-ee-csr/create-ee-csr.component';

@NgModule({
  declarations: [
    CreateCertificateComponent,
    ChainViewComponent,
    CreateEeCsrComponent,
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,  
    CertificatesRoutingModule,
    HttpClientModule,
    
    // Angular Material modules
    MatCardModule,
    MatIconModule, 
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatTreeModule,
    MatDialogModule,
    MatTooltipModule
  ]
})
export class CertificatesModule { }