export type MatchStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED';

export interface MatchResult {
  matchId: string;
  matchedUserId: string;
  similarityScore: number;
  finalScore: number;
  matchedAt: string;
  status: MatchStatus;
  explanation: string | null;
}

export interface MatchRequest {
  genderFilter: 'NONE' | 'MALE' | 'FEMALE';
  locationFilterOn: boolean;
  minAge: number | null;
  maxAge: number | null;
}

export interface RecommendationItem {
  userId: string;
  score: number | null;
}
