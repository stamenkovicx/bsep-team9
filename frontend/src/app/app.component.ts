import { Component, OnInit } from '@angular/core';
import { AuthService } from './infrastructure/auth/auth.service';
import { KeycloakService } from './infrastructure/auth/keycloak.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {
  title = 'Explorer';

  constructor(
    private authService: AuthService,
    private keycloakService: KeycloakService
  ) {}


  async ngOnInit(): Promise<void> {
    // Initialize Keycloak
    await this.keycloakService.init();
    this.checkIfUserExists();
  }
  
  private checkIfUserExists(): void {
    this.authService.checkIfUserExists();
  }
}
