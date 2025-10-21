import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from 'src/env/environment';
import { PasswordEntryDTO, CreatePasswordEntryDTO, SharePasswordRequestDTO } from './model/password-manager.model';
import { CryptoService } from './service/crypto.service';

@Injectable({
  providedIn: 'root'
})
export class PasswordManagerService {

  private apiUrl = `${environment.apiHost}api/passwords`;

  constructor(private http: HttpClient) { }

  // Dohvati sve password entry-je za trenutnog korisnika
  getUserPasswords(): Observable<PasswordEntryDTO[]> {
    return this.http.get<PasswordEntryDTO[]>(this.apiUrl);
  }

  // Kreiraj novi password entry
  createPassword(createDto: CreatePasswordEntryDTO): Observable<PasswordEntryDTO> {
    return this.http.post<PasswordEntryDTO>(this.apiUrl, createDto);
  }

  // Ažuriraj password entry
  updatePassword(id: number, updateDto: CreatePasswordEntryDTO): Observable<PasswordEntryDTO> {
    return this.http.put<PasswordEntryDTO>(`${this.apiUrl}/${id}`, updateDto);
  }

  // Obriši password entry
  deletePassword(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  // Deli password entry sa drugim korisnikom
  sharePassword(shareRequest: SharePasswordRequestDTO): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/share`, shareRequest);
  }

  // Dohvati korisnike sa kojima je deljen password entry
  getSharedUsers(entryId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/${entryId}/shared-users`);
  }

  // Dohvati enkriptovani password za dekripciju
  getEncryptedPassword(entryId: number): Observable<string> {
  return this.http.get<string>(`${this.apiUrl}/${entryId}/encrypted-password`, { 
    responseType: 'text' as 'json' 
  });
}
}