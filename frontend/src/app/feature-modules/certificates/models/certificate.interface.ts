export interface Certificate {
  id: number;
  serialNumber: string;
  subject: string;
  issuer: string;
  validFrom: string;
  validTo: string;
  type: string;
  status: string;
  isCA?: boolean;
  basicConstraints?: string;
  keyUsage?: string;
  owner?: any;
}