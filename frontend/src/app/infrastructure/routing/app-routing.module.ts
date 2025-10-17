import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from 'src/app/feature-modules/layout/home/home.component';
import { LoginComponent } from '../auth/login/login.component';
import { AuthGuard } from '../auth/auth.guard';
import { RegistrationComponent } from '../auth/registration/registration.component';
import { CreateCertificateComponent } from 'src/app/feature-modules/certificates/create-certificate/create-certificate.component';

const routes: Routes = [
  {path: 'home', component: HomeComponent},
  {path: 'login', component: LoginComponent},
  {path: 'register', component: RegistrationComponent},
  {
    path: 'certificates',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('src/app/feature-modules/certificates/certificates.module').then(m => m.CertificatesModule)
  },
  {
    path: 'templates',
    canActivate: [AuthGuard],
    loadChildren: () =>
      import('src/app/feature-modules/certificate-templates/certificate-templates.module').then(m => m.TemplatesModule)
  },      
 // 2. Podrazumevana ruta: Preusmerava praznu putanju na glavnu stranicu
  {path: '', redirectTo: '/home', pathMatch: 'full'},
  // 3. Džoker ruta (opciono): Preusmerava sve nepoznate URL-ove na početnu stranicu
  {path: '**', redirectTo: '/home'}
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
