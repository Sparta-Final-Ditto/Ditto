import { memo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiClient, ApiError } from '../lib/apiClient';
import { useBlocks, useToggleBlock } from '../hooks/useBlock';
import type { AuthTokenResponse, LocationUpdateResponse } from '../types/user';
import './ProfileTab.css';
import './SettingsTab.css';

const ICONS = {
  lock: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}><rect x="5" y="11" width="14" height="9" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></svg>,
  tag: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}><path d="M4 4h7l9 9-7 7-9-9V4z" /><circle cx="8.5" cy="8.5" r="1.2" fill="currentColor" stroke="none" /></svg>,
  pin: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}><path d="M12 21s7-6.5 7-11a7 7 0 1 0-14 0c0 4.5 7 11 7 11z" /><circle cx="12" cy="10" r="2.5" /></svg>,
  shield: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}><path d="M12 3l8 3v6c0 5-4 8-8 9-4-1-8-4-8-9V6l8-3z" /></svg>,
  warning: <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}><path d="M12 9v4M12 17h.01" /><path d="M10.3 4.3 2.7 18a2 2 0 0 0 1.7 3h15.2a2 2 0 0 0 1.7-3L13.7 4.3a2 2 0 0 0-3.4 0z" /></svg>,
};

function SettingsTab() {
  const navigate = useNavigate();

  // 비밀번호 변경
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [pwSaving, setPwSaving] = useState(false);
  const [pwMessage, setPwMessage] = useState<{ text: string; ok: boolean } | null>(null);

  // 관심사
  const [interestsInput, setInterestsInput] = useState('');
  const [interestsSaving, setInterestsSaving] = useState(false);
  const [interestsMessage, setInterestsMessage] = useState<{ text: string; ok: boolean } | null>(null);

  // 위치
  const [location, setLocation] = useState<{ latitude: number; longitude: number } | null>(null);
  const [locStatus, setLocStatus] = useState<'idle' | 'loading' | 'ok' | 'denied'>('idle');
  const [locSaving, setLocSaving] = useState(false);
  const [locMessage, setLocMessage] = useState<{ text: string; ok: boolean } | null>(null);

  // 차단 목록
  const { data: blockedUsers = [], isLoading: blockedLoading } = useBlocks();
  const toggleBlockMutation = useToggleBlock();
  const [unblockBusyId, setUnblockBusyId] = useState<string | null>(null);

  // 회원 탈퇴
  const [deleting, setDeleting] = useState(false);

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPwSaving(true);
    setPwMessage(null);
    try {
      const tokens = await apiClient.post<AuthTokenResponse>('/api/v1/users/me/password', { currentPassword, newPassword });
      localStorage.setItem('accessToken', tokens.accessToken);
      if (tokens.refreshToken) localStorage.setItem('refreshToken', tokens.refreshToken);
      setCurrentPassword('');
      setNewPassword('');
      setPwMessage({ text: '비밀번호를 변경했어요.', ok: true });
    } catch (err) {
      setPwMessage({ text: err instanceof ApiError ? err.message : '비밀번호 변경에 실패했어요.', ok: false });
    } finally {
      setPwSaving(false);
    }
  };

  const handleInterestsSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const hashtags = Array.from(new Set(interestsInput.split(/[,\s]+/).map((t) => t.trim()).filter(Boolean)));
    if (hashtags.length === 0 || hashtags.length > 10) {
      setInterestsMessage({ text: '관심사는 1개 이상, 10개 이하로 입력해주세요.', ok: false });
      return;
    }
    setInterestsSaving(true);
    setInterestsMessage(null);
    try {
      await apiClient.post('/api/v1/users/me/interests', { hashtags });
      setInterestsMessage({ text: '관심사를 등록했어요.', ok: true });
      setInterestsInput('');
    } catch (err) {
      setInterestsMessage({ text: err instanceof ApiError ? err.message : '관심사 등록에 실패했어요.', ok: false });
    } finally {
      setInterestsSaving(false);
    }
  };

  const detectLocation = () => {
    if (!navigator.geolocation) { setLocStatus('denied'); return; }
    setLocStatus('loading');
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocation({ latitude: pos.coords.latitude, longitude: pos.coords.longitude });
        setLocStatus('ok');
      },
      () => setLocStatus('denied'),
    );
  };

  const applyLocation = async () => {
    if (!location) return;
    setLocSaving(true);
    setLocMessage(null);
    try {
      const res = await apiClient.patch<LocationUpdateResponse>('/api/v1/users/me/location', location);
      setLocMessage({ text: `이제 ${res.neighborhood}로 표시돼요.`, ok: true });
    } catch (err) {
      setLocMessage({ text: err instanceof ApiError ? err.message : '위치 변경에 실패했어요.', ok: false });
    } finally {
      setLocSaving(false);
    }
  };

  const unblock = async (userId: string) => {
    setUnblockBusyId(userId);
    try {
      await toggleBlockMutation.mutateAsync({ targetId: userId, currentlyBlocked: true });
    } catch {
      // 실패하면 그대로 둔다.
    } finally {
      setUnblockBusyId(null);
    }
  };

  const handleDeleteAccount = async () => {
    if (!window.confirm('정말 탈퇴할까요? 모든 데이터가 삭제되고 되돌릴 수 없어요.')) return;
    if (!window.confirm('한 번 더 확인할게요. 탈퇴를 진행할까요?')) return;
    setDeleting(true);
    try {
      await apiClient.delete('/api/v1/users/me');
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('userId');
      localStorage.removeItem('userName');
      navigate('/login');
    } catch (err) {
      alert(err instanceof ApiError ? err.message : '탈퇴에 실패했어요.');
      setDeleting(false);
    }
  };

  return (
    <div className="settings-page">
      <div className="view-head"><h1>설정</h1><p>계정과 관련된 정보를 관리해요.</p></div>

      <section className="settings-section">
        <div className="settings-section-head">
          <div className="settings-section-icon">{ICONS.lock}</div>
          <div>
            <h2>비밀번호 변경</h2>
            <p>주기적으로 바꿔주면 계정이 더 안전해져요.</p>
          </div>
        </div>
        {pwMessage && <div className={`settings-message ${pwMessage.ok ? 'ok' : 'err'}`}>{pwMessage.text}</div>}
        <form onSubmit={handlePasswordSubmit}>
          <div className="settings-field">
            <label htmlFor="current-password">현재 비밀번호</label>
            <input id="current-password" type="password" required value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
          </div>
          <div className="settings-field">
            <label htmlFor="new-password">새 비밀번호 (8자 이상)</label>
            <input id="new-password" type="password" required minLength={8} value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
          </div>
          <div className="settings-actions">
            <button type="submit" className="settings-btn settings-btn-primary" disabled={pwSaving}>
              {pwSaving ? '변경 중...' : '비밀번호 변경'}
            </button>
          </div>
        </form>
      </section>

      <section className="settings-section">
        <div className="settings-section-head">
          <div className="settings-section-icon">{ICONS.tag}</div>
          <div>
            <h2>관심사</h2>
            <p>쉼표로 구분해 1~10개를 입력해주세요.</p>
          </div>
        </div>
        {interestsMessage && <div className={`settings-message ${interestsMessage.ok ? 'ok' : 'err'}`}>{interestsMessage.text}</div>}
        <form onSubmit={handleInterestsSubmit}>
          <div className="settings-field">
            <input type="text" placeholder="예: 여행, 카페, 러닝" value={interestsInput} onChange={(e) => setInterestsInput(e.target.value)} />
          </div>
          <div className="settings-actions">
            <button type="submit" className="settings-btn settings-btn-primary" disabled={interestsSaving}>
              {interestsSaving ? '등록 중...' : '관심사 등록'}
            </button>
          </div>
        </form>
      </section>

      <section className="settings-section">
        <div className="settings-section-head">
          <div className="settings-section-icon">{ICONS.pin}</div>
          <div>
            <h2>위치</h2>
            <p>동네 정보는 매칭 및 게시물 위치 표시에 사용돼요.</p>
          </div>
        </div>
        {locMessage && <div className={`settings-message ${locMessage.ok ? 'ok' : 'err'}`}>{locMessage.text}</div>}
        {locStatus === 'loading' && <div className="settings-loc-status loading">위치 확인 중...</div>}
        {locStatus === 'ok' && location && (
          <div className="settings-loc-status ok">✓ 위치를 확인했어요 ({location.latitude.toFixed(4)}, {location.longitude.toFixed(4)})</div>
        )}
        {locStatus === 'denied' && <div className="settings-loc-status denied">위치 권한이 거부됐어요.</div>}
        <div className="settings-actions" style={{ justifyContent: 'space-between' }}>
          <button type="button" className="settings-btn settings-btn-secondary" onClick={detectLocation}>📍 현재 위치 감지</button>
          <button type="button" className="settings-btn settings-btn-primary" disabled={!location || locSaving} onClick={applyLocation}>
            {locSaving ? '적용 중...' : '이 위치로 저장'}
          </button>
        </div>
      </section>

      <section className="settings-section">
        <div className="settings-section-head">
          <div className="settings-section-icon">{ICONS.shield}</div>
          <div>
            <h2>차단 목록</h2>
            <p>차단한 사람은 서로의 게시물과 활동을 볼 수 없어요.</p>
          </div>
        </div>
        {blockedLoading && <div className="settings-blocked-empty">불러오는 중...</div>}
        {!blockedLoading && blockedUsers.length === 0 && <div className="settings-blocked-empty">차단한 사용자가 없어요.</div>}
        {blockedUsers.map((u) => (
          <div key={u.id} className="profile-user-row">
            <div className="profile-user-avatar" style={u.profileImageUrl ? { backgroundImage: `url(${u.profileImageUrl})` } : undefined} />
            <div className="profile-user-info"><div className="profile-user-name">{u.nickname}</div></div>
            <button className="profile-follow-btn following" disabled={unblockBusyId === u.id} onClick={() => unblock(u.id)}>
              차단 해제
            </button>
          </div>
        ))}
      </section>

      <section className="settings-section settings-danger">
        <div className="settings-section-head">
          <div className="settings-section-icon">{ICONS.warning}</div>
          <div>
            <h2>회원 탈퇴</h2>
            <p>탈퇴 시 모든 데이터가 삭제되며 되돌릴 수 없어요.</p>
          </div>
        </div>
        <div className="settings-actions">
          <button type="button" className="settings-btn-danger" onClick={handleDeleteAccount} disabled={deleting}>
            {deleting ? '탈퇴 처리 중...' : '회원 탈퇴하기'}
          </button>
        </div>
      </section>
    </div>
  );
}

export default memo(SettingsTab);
