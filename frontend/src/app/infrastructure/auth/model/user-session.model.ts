export interface UserSession {
  id: number;
  sessionId: string;
  ipAddress: string;
  deviceType: string;
  browserName: string;
  lastActivity: string;
  createdAt: string;
  isActive: boolean;
  expiresAt: string;
  isCurrentSession: boolean;
}
