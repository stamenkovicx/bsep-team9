export interface TokenSession {
  id: number;
  sessionId: string;
  userId: number;
  ipAddress: string;
  userAgent: string;
  deviceType: string;
  browser: string;
  createdAt: string;
  lastActivity: string;
  revoked: boolean;
  revokedAt?: string;
  expiresAt: string;
}

