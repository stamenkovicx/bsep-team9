import { Injectable } from '@angular/core';

import * as forge from 'node-forge';

@Injectable({
  providedIn: 'root'
})
export class CryptoService {

  async decryptWithPrivateKey(privateKeyPem: string, encryptedDataBase64: string): Promise<string> {
    try {
      console.log('Starting decryption with PKCS1Padding...');
      
      // Parsiraj privatni ključ
      const privateKey = forge.pki.privateKeyFromPem(privateKeyPem);
      console.log('Private key parsed successfully');
      
      // DEBUG INFO
      console.log('=== DEBUG INFO ===');
      console.log('Encrypted data length:', encryptedDataBase64.length);
      console.log('Private key size:', privateKey.n.bitLength());
      console.log('==================');
      
      // Dekriptuj sa PKCS1Padding (NEMA više OAEP parametara)
      const encryptedData = forge.util.decode64(encryptedDataBase64);
      const decrypted = privateKey.decrypt(encryptedData); // BEZ parametara = PKCS1Padding
      
      console.log('Decryption successful with PKCS1Padding');
      return decrypted;
      
    } catch (error) {
      console.error('PKCS1Padding decryption failed:', error);
      
      // Probaj sa eksplicitnim PKCS1
      try {
        console.log('Trying explicit PKCS1...');
        const privateKey = forge.pki.privateKeyFromPem(privateKeyPem);
        const encryptedData = forge.util.decode64(encryptedDataBase64);
        
        const decrypted = privateKey.decrypt(encryptedData, 'RSAES-PKCS1-V1_5');
        console.log('Decryption successful with explicit PKCS1');
        return decrypted;
        
      } catch (error2) {
        console.error('All PKCS1 attempts failed:', error2);
        throw new Error('Failed to decrypt data with PKCS1Padding');
      }
    }
  }

  // Ostale metode mogu ostati iste ili ih možeš obrisati
  isRSASupported(): boolean {
    return true; // node-forge uvek podržava RSA
  }
}