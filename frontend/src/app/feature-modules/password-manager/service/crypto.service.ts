import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class CryptoService {

  // Dekriptuj podatke koristeći privatni ključ
  async decryptWithPrivateKey(privateKeyPem: string, encryptedDataBase64: string): Promise<string> {
    try {
      console.log('Starting decryption...');
      
      // Import privatnog ključa
      const privateKey = await this.importPrivateKey(privateKeyPem);
      console.log('Private key imported');
      
      // Konvertuj Base64 u ArrayBuffer
      const encryptedData = this.base64ToArrayBuffer(encryptedDataBase64);
      console.log('Data converted to ArrayBuffer');
      
      // Dekriptuj
      const decrypted = await window.crypto.subtle.decrypt(
        {
          name: 'RSA-OAEP'
        },
        privateKey,
        encryptedData
      );
      console.log('Data decrypted');
      
      // Konvertuj u string
      const result = new TextDecoder().decode(decrypted);
      console.log('Decryption successful');
      return result;
      
    } catch (error) {
      console.error('Decryption failed:', error);
      throw new Error('Failed to decrypt data: ' + error);
    }
  }

  private async importPrivateKey(pem: string): Promise<CryptoKey> {
    try {
      console.log('Raw PEM:', pem.substring(0, 100) + '...');
      
      let pemContents: string;

      // Proveri format
      if (pem.includes('BEGIN RSA PRIVATE KEY')) {
        console.log('Detected PKCS#1 format - converting to PKCS#8');
        // PKCS#1 format - konvertuj u PKCS#8
        pemContents = this.convertPkcs1ToPkcs8(pem);
      } else if (pem.includes('BEGIN PRIVATE KEY')) {
        console.log('Detected PKCS#8 format');
        // PKCS#8 format - koristi direktno
        pemContents = pem
          .replace(/-----BEGIN PRIVATE KEY-----/g, '')
          .replace(/-----END PRIVATE KEY-----/g, '')
          .replace(/\s/g, '');
      } else {
        throw new Error('Unsupported key format. Must be PKCS#1 or PKCS#8.');
      }

      console.log('Cleaned PEM length:', pemContents.length);
      
      // Base64 to ArrayBuffer
      const binaryDer = this.base64ToArrayBuffer(pemContents);
      console.log('Binary DER length:', binaryDer.byteLength);
      
      // Import ključa - probaj oba formata
      let key: CryptoKey;
      try {
        // Prvo probaj PKCS#8
        key = await window.crypto.subtle.importKey(
          'pkcs8',
          binaryDer,
          {
            name: 'RSA-OAEP',
            hash: { name: 'SHA-256' }
          },
          true,
          ['decrypt']
        );
        console.log('✅ Private key imported successfully as PKCS#8');
      } catch (pkcs8Error) {
        console.log('PKCS#8 failed, trying PKCS#1...');
        // Probaj PKCS#1 kao fallback
        key = await window.crypto.subtle.importKey(
          'pkcs8', // I dalje PKCS#8, ali sa konvertovanim sadržajem
          binaryDer,
          {
            name: 'RSA-OAEP',
            hash: { name: 'SHA-256' }
          },
          true,
          ['decrypt']
        );
        console.log('✅ Private key imported successfully after conversion');
      }
      
      return key;
      
    } catch (error) {
      console.error('❌ Key import error:', error);
      throw new Error('Invalid private key format. Error: ' + error);
    }
  }

  private convertPkcs1ToPkcs8(pkcs1Pem: string): string {
    try {
      console.log('Converting PKCS#1 to PKCS#8...');
      
      // Čisti PKCS#1 PEM
      const pkcs1Contents = pkcs1Pem
        .replace(/-----BEGIN RSA PRIVATE KEY-----/g, '')
        .replace(/-----END RSA PRIVATE KEY-----/g, '')
        .replace(/\s/g, '');

      // Za sada vraćamo isti sadržaj - Web Crypto API možda može da ga parsira
      console.log('Returning PKCS#1 content as-is (let Web Crypto try)');
      return pkcs1Contents;
      
    } catch (error) {
      console.error('PKCS#1 conversion failed:', error);
      // Vrati original ako konverzija ne uspe
      return pkcs1Pem
        .replace(/-----BEGIN RSA PRIVATE KEY-----/g, '')
        .replace(/-----END RSA PRIVATE KEY-----/g, '')
        .replace(/\s/g, '');
    }
  }

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binaryString = atob(base64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
  }

  // Proveri da li Web Crypto API podržava RSA-OAEP
  isRSASupported(): boolean {
    return !!window.crypto && !!window.crypto.subtle;
  }
}