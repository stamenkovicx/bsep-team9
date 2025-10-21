export interface PasswordEntryDTO {
  id: number;
  siteName: string;
  username: string;
  createdAt: string;
  updatedAt: string;
  notes: string;
  ownerId: number;
  ownerEmail: string;
  decryptedPassword?: string; // Ovo će biti popunjeno samo pri dekripciji
}

export interface CreatePasswordEntryDTO {
  siteName: string;
  username: string;
  password: string;
  notes: string;
}

export interface SharePasswordRequestDTO {
  passwordEntryId: number;
  targetUserEmail: string;
}

export interface EncryptedPasswordDTO {
  encryptedPassword: string;
  publicKeyFingerprint: string;
}