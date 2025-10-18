import { Certificate } from "./certificate.interface";

// Definišemo "ravni" čvor (flat node) koji MatTree koristi
export interface CertificateFlatNode {
  expandable: boolean;
  name: string; // Ovo je ono sto ce se prikazati
  level: number;
  data: Certificate; // Originalni podaci
  isChainInvalid?: boolean; // Za vizuelni status lanca
}