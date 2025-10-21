import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PasswordManagerService } from '../password-manager.service';
import { PasswordEntryDTO } from '../model/password-manager.model';

interface User {
  id: number;
  name: string;
  surname: string;
  email: string;
}

@Component({
  selector: 'app-password-share',
  templateUrl: './password-share.component.html',
  styleUrls: ['./password-share.component.css']
})
export class PasswordShareComponent implements OnInit {
  shareForm: FormGroup;
  password: PasswordEntryDTO | null = null;
  sharedUsers: User[] = [];
  searchResults: User[] = [];
  isLoading = false;
  isSharing = false;
  passwordId?: number;

  constructor(
    private fb: FormBuilder,
    private passwordService: PasswordManagerService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar
  ) {
    this.shareForm = this.createForm();
  }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      this.passwordId = +params['id'];
      this.loadPasswordDetails();
      this.loadSharedUsers();
    });
  }

  createForm(): FormGroup {
    return this.fb.group({
      targetUserEmail: ['', [Validators.required, Validators.email]]
    });
  }

  loadPasswordDetails(): void {
    this.isLoading = true;
    // TODO: Implementiraj dobijanje detalja passworda
    // Za sada ćemo simulirati
    setTimeout(() => {
      this.password = {
        id: this.passwordId!,
        siteName: 'Example Site',
        username: 'user@example.com',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        notes: 'This is an example password entry',
        ownerId: 1,
        ownerEmail: 'current@user.com'
      };
      this.isLoading = false;
    }, 500);
  }

  loadSharedUsers(): void {
    if (this.passwordId) {
      this.passwordService.getSharedUsers(this.passwordId).subscribe({
        next: (users) => {
          this.sharedUsers = users;
        },
        error: (error) => {
          console.error('Error loading shared users:', error);
        }
      });
    }
  }

  onShare(): void {
    if (this.shareForm.valid && this.passwordId) {
      this.isSharing = true;
      
      const shareRequest = {
        passwordEntryId: this.passwordId,
        targetUserEmail: this.shareForm.get('targetUserEmail')?.value
      };

      this.passwordService.sharePassword(shareRequest).subscribe({
        next: () => {
          this.snackBar.open('Password shared successfully', 'Close', { duration: 3000 });
          this.shareForm.reset();
          this.loadSharedUsers();
          this.searchResults = [];
          this.isSharing = false;
        },
        error: (error) => {
          console.error('Error sharing password:', error);
          this.snackBar.open('Error sharing password', 'Close', { duration: 3000 });
          this.isSharing = false;
        }
      });
    }
  }

  searchUsers(): void {
    const email = this.shareForm.get('targetUserEmail')?.value;
    if (email && email.length > 2) {
      // TODO: Implementiraj search korisnika na backendu
      // Za sada ćemo simulirati
      this.searchResults = [
        { id: 2, name: 'John', surname: 'Doe', email: 'john@example.com' },
        { id: 3, name: 'Jane', surname: 'Smith', email: 'jane@example.com' }
      ].filter(user => user.email.includes(email));
    } else {
      this.searchResults = [];
    }
  }

  selectUser(user: User): void {
    this.shareForm.patchValue({
      targetUserEmail: user.email
    });
    this.searchResults = [];
  }

  onRemoveShare(userId: number): void {
    if (confirm('Are you sure you want to remove access for this user?')) {
      // TODO: Implementiraj brisanje deljenja
      this.sharedUsers = this.sharedUsers.filter(user => user.id !== userId);
      this.snackBar.open('Access removed successfully', 'Close', { duration: 3000 });
    }
  }
}