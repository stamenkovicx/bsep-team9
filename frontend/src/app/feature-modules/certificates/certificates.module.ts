import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CertificatesRoutingModule } from './certificates-routing.module';
import { CreateCertificateComponent } from './certificates/create-certificate/create-certificate.component';


@NgModule({
  declarations: [
    CreateCertificateComponent
  ],
  imports: [
    CommonModule,
    CertificatesRoutingModule
  ]
})
export class CertificatesModule { }
