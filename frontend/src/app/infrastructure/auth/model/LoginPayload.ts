import { Login } from "./login.model";

export interface LoginPayload extends Login {
    twoFactorCode: string | null;
  }