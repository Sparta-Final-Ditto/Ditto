export interface UserProfile {
  id: string;
  email: string;
  nickname: string;
  gender: 'MALE' | 'FEMALE';
  birthdate: string;
  profileImageUrl: string | null;
  bio: string | null;
  neighborhood: string | null;
}

export interface PublicProfile {
  id: string;
  nickname: string;
  profileImageUrl: string | null;
  bio: string | null;
}

export interface LocationUpdateResponse {
  latitude: number;
  longitude: number;
  neighborhood: string;
}

export interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
}

export type ReportType = 'SPAM' | 'HATE_SPEECH' | 'SEXUAL_CONTENT' | 'VIOLENCE' | 'MISINFORMATION' | 'OTHER';
