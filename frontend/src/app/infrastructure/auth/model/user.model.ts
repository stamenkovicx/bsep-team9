export interface User {
    id: number;
    email: string;
    role: string;
    is2FAEnabled?: boolean;
}
  