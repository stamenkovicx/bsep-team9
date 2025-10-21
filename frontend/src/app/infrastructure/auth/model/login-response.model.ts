export interface LoginResponse {
    token: string;
    userId: number;
    email: string;
    userRole: string;
    is2faEnabled?: boolean;
    passwordChangeRequired?: boolean;
  }