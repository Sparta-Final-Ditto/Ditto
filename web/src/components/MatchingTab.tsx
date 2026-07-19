import { memo, useCallback, useEffect, useState } from 'react';
import { apiClient, ApiError } from '../lib/apiClient';
import type { MatchRequest, MatchResult, RecommendationItem } from '../types/match';
import type { PublicProfile } from '../types/user';
import './MatchingTab.css';

const fetchProfiles = async (userIds: string[]): Promise<Record<string, PublicProfile>> => {
  const unique = Array.from(new Set(userIds));
  const entries = await Promise.all(
    unique.map(async (id) => {
      try {
        const profile = await apiClient.get<PublicProfile>(`/api/v1/users/${id}`);
        return [id, profile] as const;
      } catch {
        return [id, null] as const;
      }
    }),
  );
  const map: Record<string, PublicProfile> = {};
  entries.forEach(([id, profile]) => { if (profile) map[id] = profile; });
  return map;
};

function MatchingTab() {
  const [today, setToday] = useState<MatchResult | null>(null);
  const [todayLoading, setTodayLoading] = useState(true);
  const [requestLoading, setRequestLoading] = useState(false);
  const [statusBusy, setStatusBusy] = useState(false);
  const [profiles, setProfiles] = useState<Record<string, PublicProfile>>({});

  const [history, setHistory] = useState<MatchResult[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);

  const [recommendations, setRecommendations] = useState<RecommendationItem[]>([]);
  const [recLoading, setRecLoading] = useState(true);

  const [openExplanationId, setOpenExplanationId] = useState<string | null>(null);
  const [explanations, setExplanations] = useState<Record<string, string>>({});
  const [explanationLoading, setExplanationLoading] = useState(false);

  const loadToday = useCallback(async () => {
    setTodayLoading(true);
    try {
      const result = await apiClient.get<MatchResult>('/api/v1/matching/today', { raw: true });
      setToday(result);
      const p = await fetchProfiles([result.matchedUserId]);
      setProfiles((prev) => ({ ...prev, ...p }));
    } catch {
      setToday(null);
    } finally {
      setTodayLoading(false);
    }
  }, []);

  const loadHistory = useCallback(async () => {
    setHistoryLoading(true);
    try {
      const list = await apiClient.get<MatchResult[]>('/api/v1/matching/history');
      setHistory(list);
      const p = await fetchProfiles(list.map((h) => h.matchedUserId));
      setProfiles((prev) => ({ ...prev, ...p }));
    } catch {
      setHistory([]);
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  const loadRecommendations = useCallback(async () => {
    setRecLoading(true);
    try {
      const list = await apiClient.get<RecommendationItem[]>('/api/v1/matching/recommendations?limit=10');
      setRecommendations(list);
      const p = await fetchProfiles(list.map((r) => r.userId));
      setProfiles((prev) => ({ ...prev, ...p }));
    } catch {
      setRecommendations([]);
    } finally {
      setRecLoading(false);
    }
  }, []);

  useEffect(() => {
    loadToday();
    loadHistory();
    loadRecommendations();
  }, [loadToday, loadHistory, loadRecommendations]);

  const requestMatch = async () => {
    setRequestLoading(true);
    try {
      const request: MatchRequest = { genderFilter: 'NONE', locationFilterOn: false, minAge: null, maxAge: null };
      const result = await apiClient.post<MatchResult>('/api/v1/matching/today', request, { raw: true });
      setToday(result);
      const p = await fetchProfiles([result.matchedUserId]);
      setProfiles((prev) => ({ ...prev, ...p }));
    } catch (err) {
      console.error('매칭 생성 실패', err);
    } finally {
      setRequestLoading(false);
    }
  };

  const updateStatus = async (status: 'ACCEPTED' | 'REJECTED') => {
    if (!today) return;
    setStatusBusy(true);
    try {
      await apiClient.patch(`/api/v1/matching/${today.matchId}/status`, { status });
      setToday((prev) => (prev ? { ...prev, status } : prev));
      loadHistory();
    } catch (err) {
      console.error('매칭 상태 변경 실패', err);
    } finally {
      setStatusBusy(false);
    }
  };

  const toggleExplanation = async (matchId: string) => {
    if (openExplanationId === matchId) {
      setOpenExplanationId(null);
      return;
    }
    setOpenExplanationId(matchId);
    if (explanations[matchId]) return;
    setExplanationLoading(true);
    try {
      const text = await apiClient.get<string>(`/api/v1/matching/${matchId}/explanation`);
      setExplanations((prev) => ({ ...prev, [matchId]: text }));
    } catch (err) {
      setExplanations((prev) => ({ ...prev, [matchId]: err instanceof ApiError ? err.message : '설명을 불러오지 못했어요.' }));
    } finally {
      setExplanationLoading(false);
    }
  };

  const statusLabel = (status: MatchResult['status']) =>
    status === 'ACCEPTED' ? '수락함' : status === 'REJECTED' ? '넘어감' : '대기중';
  const statusClass = (status: MatchResult['status']) =>
    status === 'ACCEPTED' ? 'accepted' : status === 'REJECTED' ? 'rejected' : 'pending';

  return (
    <div>
      <div className="view-head"><h1>오늘의 매칭</h1><p>지금 이 시점, 가장 결이 맞는 한 사람을 찾아드려요.</p></div>

      {todayLoading && <div className="match-empty"><p>불러오는 중...</p></div>}

      {!todayLoading && today && (
        <div className="match-today">
          <div className="match-top">
            <div className="match-avpair"><div className="match-av a" /><div className="match-av b" /></div>
            <div className="match-score"><b>{Math.round(today.similarityScore * 100)}%</b><span>일치</span></div>
          </div>
          <div style={{ fontSize: 13.5, color: 'var(--im)', marginBottom: 12 }}>
            {profiles[today.matchedUserId]?.nickname || '알 수 없는 상대'}님과의 매칭이에요.
          </div>

          <div
            className="match-explain"
            style={{ cursor: 'pointer' }}
            onClick={() => toggleExplanation(today.matchId)}
          >
            {openExplanationId === today.matchId ? (
              explanationLoading ? '불러오는 중...' : (explanations[today.matchId] || today.explanation || '설명이 없어요.')
            ) : (
              <b>왜 매칭됐는지 보기 ›</b>
            )}
          </div>

          {today.status === 'PENDING' ? (
            <div className="match-cta">
              <button className="mbtn mbtn-reject" disabled={statusBusy} onClick={() => updateStatus('REJECTED')}>넘어갈게요</button>
              <button className="mbtn mbtn-accept" disabled={statusBusy} onClick={() => updateStatus('ACCEPTED')}>대화 시작하기</button>
            </div>
          ) : (
            <div className={`hist-status ${statusClass(today.status)}`}>{statusLabel(today.status)}</div>
          )}
        </div>
      )}

      {!todayLoading && !today && (
        <div className="match-empty">
          <p>아직 오늘의 매칭이 없어요.</p>
          <button className="mbtn mbtn-accept" onClick={requestMatch} disabled={requestLoading}>
            {requestLoading ? '찾는 중...' : '매칭 받기'}
          </button>
        </div>
      )}

      {!recLoading && recommendations.length > 0 && (
        <>
          <div className="view-head" style={{ marginTop: 32 }}>
            <h1 style={{ fontSize: 18 }}>추천 상대</h1>
          </div>
          {recommendations.map((r) => (
            <div className="hist-item" key={r.userId}>
              <div className="hist-av" />
              <div className="hist-info">
                <div className="hist-score">{profiles[r.userId]?.nickname || '알 수 없는 사용자'}</div>
                {r.score != null && <div className="hist-date">추천 점수 {Math.round(r.score * 100)}%</div>}
              </div>
            </div>
          ))}
        </>
      )}

      <div className="view-head" style={{ marginTop: 32 }}>
        <h1 style={{ fontSize: 18 }}>매칭 히스토리</h1>
      </div>
      {historyLoading && <div className="hist-item"><div className="hist-info">불러오는 중...</div></div>}
      {!historyLoading && history.length === 0 && <div className="hist-item"><div className="hist-info">아직 매칭 기록이 없어요.</div></div>}
      {history.map((h) => (
        <div
          className="hist-item" key={h.matchId}
          style={{ cursor: 'pointer' }}
          onClick={() => toggleExplanation(h.matchId)}
        >
          <div className="hist-av" />
          <div className="hist-info">
            <div className="hist-score">{profiles[h.matchedUserId]?.nickname || '알 수 없는 상대'} · {Math.round(h.finalScore * 100)}% 일치</div>
            <div className="hist-date">{new Date(h.matchedAt).toLocaleDateString('ko-KR')}</div>
            {openExplanationId === h.matchId && (
              <div style={{ fontSize: 12.5, color: 'var(--im)', marginTop: 6 }}>
                {explanationLoading ? '불러오는 중...' : (explanations[h.matchId] || h.explanation || '설명이 없어요.')}
              </div>
            )}
          </div>
          <div className={`hist-status ${statusClass(h.status)}`}>{statusLabel(h.status)}</div>
        </div>
      ))}
    </div>
  );
}

export default memo(MatchingTab);
