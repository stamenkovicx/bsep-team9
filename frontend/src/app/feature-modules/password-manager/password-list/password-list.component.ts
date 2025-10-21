import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PasswordManagerService } from '../password-manager.service';
import { PasswordEntryDTO } from '../model/password-manager.model';
import { CryptoService } from '../service/crypto.service';
import { ChangeDetectorRef } from '@angular/core';
import { AuthService } from 'src/app/infrastructure/auth/auth.service';

@Component({
  selector: 'app-password-list',
  templateUrl: './password-list.component.html',
  styleUrls: ['./password-list.component.css']
})
export class PasswordListComponent implements OnInit {
  @ViewChild('fileInput') fileInput!: ElementRef;

  passwords: (PasswordEntryDTO & { showDecrypted?: boolean; decrypting?: boolean })[] = [];
  isLoading = false;
  decryptingId: number | null = null;
  decryptedPasswordId: number | null = null;  
  decryptedPasswordText: string = '';  
  currentUserId = 1;

  constructor(
    private passwordService: PasswordManagerService,
    private router: Router,
    private snackBar: MatSnackBar,
    private cryptoService: CryptoService,
    private cdr: ChangeDetectorRef,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.loadPasswords();
    this.loadCurrentUser();
  }

  loadCurrentUser(): void {
    this.authService.user$.subscribe(user => {
      this.currentUserId = user.id;
    });
  }

  loadPasswords(): void {
    this.isLoading = true;
    this.passwordService.getUserPasswords().subscribe({
      next: (passwords) => {
        this.passwords = passwords.map(p => ({ ...p, showDecrypted: false, decrypting: false }));
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error loading passwords:', error);
        this.snackBar.open('Error loading passwords', 'Close', { duration: 3000 });
        this.isLoading = false;
      }
    });
  }

  onDecryptPassword(password: any): void {
    this.decryptingId = password.id;
    password.decrypting = true;
    
    // Otvori file picker za privatni ključ
    this.fileInput.nativeElement.click();
    
    // Privremeno čuvanje reference na password
    this.fileInput.nativeElement.dataset.passwordId = password.id;
  }

  onPrivateKeySelected(event: any): void {
    const file = event.target.files[0];
    const passwordId = event.target.dataset.passwordId;
    
    if (!file) {
      this.decryptingId = null;
      return;
    }

    // Ovdje ćeš implementirati dekripciju koristeći Web Crypto API
    this.decryptPasswordWithPrivateKey(file, passwordId);
    
    // Reset file input
    event.target.value = '';
  }

  async decryptPasswordWithPrivateKey(privateKeyFile: File, passwordId: number): Promise<void> {
    try {
      console.log('[1] Starting decryption for password:', passwordId);
      
      // Pročitaj fajl sa privatnim ključem
      const privateKeyPem = await this.readFileAsText(privateKeyFile);
      console.log('[2] Private key file read, length:', privateKeyPem.length);
      
      // Dobavi enkriptovan password sa backend-a
      this.passwordService.getEncryptedPassword(passwordId).subscribe({
        next: async (encryptedPassword) => {
          try {
            console.log('[3] Got encrypted password, length:', encryptedPassword.length);
            
            // Dekriptuj password koristeći crypto service
            const decryptedPassword = await this.cryptoService.decryptWithPrivateKey(
              privateKeyPem, 
              encryptedPassword
            );
            
            console.log('[4] Password decrypted successfully:', decryptedPassword);
            console.log('[5] decryptedPasswordId before:', this.decryptedPasswordId);
            console.log('[6] decryptedPasswordText before:', this.decryptedPasswordText);
            
            // Prikaži dekriptovan password
            this.decryptedPasswordId = passwordId;
            this.decryptedPasswordText = decryptedPassword;
            
            console.log('[7] decryptedPasswordId after:', this.decryptedPasswordId);
            console.log('[8] decryptedPasswordText after:', this.decryptedPasswordText);
            console.log('[9] All passwords:', this.passwords.map(p => ({ id: p.id, site: p.siteName })));
            
            // FORSIRAJ CHANGE DETECTION
            this.cdr.detectChanges();
            console.log('[10] Change detection forced');
            
            // Automatski sakrij nakon 30 sekundi
            setTimeout(() => {
              console.log('[11] Hiding password after 30 seconds');
              this.decryptedPasswordId = null;
              this.decryptedPasswordText = '';
              this.cdr.detectChanges();
            }, 30000);
            
            this.snackBar.open('Password decrypted successfully', 'Close', { duration: 3000 });
            
          } catch (decryptError) {
            console.error('❌ Decryption failed:', decryptError);
            this.snackBar.open('Failed to decrypt password. Check your private key.', 'Close', { duration: 5000 });
          } finally {
            this.decryptingId = null;
          }
        },
        error: (error) => {
          console.error('❌ Error getting encrypted password:', error);
          this.snackBar.open('Error accessing password', 'Close', { duration: 3000 });
          this.decryptingId = null;
        }
      });
      
    } catch (error) {
      console.error('❌ File reading failed:', error);
      this.snackBar.open('Error reading private key file', 'Close', { duration: 3000 });
      this.decryptingId = null;
    }
  }

  private readFileAsText(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = (e) => resolve(e.target?.result as string);
      reader.onerror = (e) => reject(e);
      reader.readAsText(file);
    });
  }

  onSharePassword(password: PasswordEntryDTO): void {
    this.router.navigate(['/password-manager/passwords/share', password.id]);
  }

  onDeletePassword(passwordId: number): void {
    if (confirm('Are you sure you want to delete this password?')) {
      this.passwordService.deletePassword(passwordId).subscribe({
        next: () => {
          this.passwords = this.passwords.filter(p => p.id !== passwordId);
          this.snackBar.open('Password deleted successfully', 'Close', { duration: 3000 });
        },
        error: (error) => {
          console.error('Error deleting password:', error);
          this.snackBar.open('Error deleting password', 'Close', { duration: 3000 });
        }
      });
    }
  }

  onCopyPassword(password: string): void {
    navigator.clipboard.writeText(password).then(() => {
      this.snackBar.open('Password copied to clipboard', 'Close', { duration: 2000 });
    });
  }
}