import { useEffect, useState } from 'react';
import { apiClient, ApiError } from '../lib/apiClient';
import type { PublicProfile } from '../types/user';
import type { ChatRoomDetail, ChatRoomInviteResponse } from '../types/chat';
import UserSelectList from './UserSelectList';

interface Props {
  roomId: string;
  myUserId: string;
  onClose: () => void;
  onNotificationChanged: (enabled: boolean) => void;
  onLeft: () => void;
  onNavigateToUser: (userId: string) => void;
}

export default function RoomManagePanel({ roomId, myUserId, onClose, onNotificationChanged, onLeft, onNavigateToUser }: Props) {
  const [detail, setDetail] = useState<ChatRoomDetail | null>(null);
  const [profiles, setProfiles] = useState<Record<string, PublicProfile>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busyUserId, setBusyUserId] = useState<string | null>(null);
  const [notifBusy, setNotifBusy] = useState(false);
  const [leaveBusy, setLeaveBusy] = useState(false);
  const [inviting, setInviting] = useState(false);
  const [followings, setFollowings] = useState<PublicProfile[]>([]);
  const [followingsLoading, setFollowingsLoading] = useState(false);
  const [inviteSelected, setInviteSelected] = useState<Set<string>>(new Set());
  const [inviteBusy, setInviteBusy] = useState(false);

  const loadDetail = () => {
    setLoading(true);
    apiClient.get<ChatRoomDetail>(`/api/v1/chat/rooms/${roomId}`)
      .then(async (res) => {
        setDetail(res);
        const active = res.participants.filter((p) => !p.leftAt);
        const entries = await Promise.all(
          active.map(async (p) => {
            try {
              const profile = await apiClient.get<PublicProfile>(`/api/v1/users/${p.userId}`);
              return [p.userId, profile] as const;
            } catch {
              return [p.userId, null] as const;
            }
          }),
        );
        const map: Record<string, PublicProfile> = {};
        entries.forEach(([id, profile]) => { if (profile) map[id] = profile; });
        setProfiles(map);
      })
      .catch(() => setError('채팅방 정보를 불러오지 못했어요.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadDetail(); }, [roomId]);

  const myRole = detail?.participants.find((p) => p.userId === myUserId)?.role;
  const activeParticipants = detail?.participants.filter((p) => !p.leftAt) ?? [];

  const toggleNotification = async () => {
    if (!detail) return;
    setNotifBusy(true);
    try {
      const next = !detail.notificationEnabled;
      await apiClient.patch(`/api/v1/chat/rooms/${roomId}/notifications`, { enabled: next });
      setDetail((prev) => (prev ? { ...prev, notificationEnabled: next } : prev));
      onNotificationChanged(next);
    } catch {
      // 실패하면 그대로 둔다.
    } finally {
      setNotifBusy(false);
    }
  };

  const kickUser = async (userId: string) => {
    if (!window.confirm('이 참여자를 내보낼까요?')) return;
    setBusyUserId(userId);
    try {
      await apiClient.delete(`/api/v1/chat/rooms/${roomId}/participants/${userId}`);
      loadDetail();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '내보내지 못했어요.');
    } finally {
      setBusyUserId(null);
    }
  };

  const transferOwner = async (userId: string) => {
    if (!window.confirm('방장을 위임할까요? 위임 후에는 되돌릴 수 없어요.')) return;
    setBusyUserId(userId);
    try {
      await apiClient.patch(`/api/v1/chat/rooms/${roomId}/participants/${userId}/role`, { role: 'OWNER' });
      loadDetail();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '방장 위임에 실패했어요.');
    } finally {
      setBusyUserId(null);
    }
  };

  const openInvite = () => {
    setInviting(true);
    setInviteSelected(new Set());
    // 패널이 열려있는 동안 팔로우 목록이 바뀔 수 있으니 열 때마다 새로 불러온다.
    setFollowingsLoading(true);
    apiClient.get<PublicProfile[]>(`/api/v1/users/${myUserId}/followings`)
      .then(setFollowings)
      .catch(() => setFollowings([]))
      .finally(() => setFollowingsLoading(false));
  };

  const submitInvite = async () => {
    if (inviteSelected.size === 0) return;
    setInviteBusy(true);
    try {
      await apiClient.post<ChatRoomInviteResponse>(`/api/v1/chat/rooms/${roomId}/participants`, {
        userIds: Array.from(inviteSelected),
      });
      setInviting(false);
      loadDetail();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '초대에 실패했어요.');
    } finally {
      setInviteBusy(false);
    }
  };

  const leaveRoom = async () => {
    if (!window.confirm('이 채팅방을 나갈까요?')) return;
    setLeaveBusy(true);
    try {
      await apiClient.post(`/api/v1/chat/rooms/${roomId}/leave`);
      onLeft();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '나가지 못했어요.');
    } finally {
      setLeaveBusy(false);
    }
  };

  const existingIds = new Set(activeParticipants.map((p) => p.userId));

  return (
    <div style={{ borderLeft: '1px solid var(--border)', width: 280, padding: 18, overflowY: 'auto', flexShrink: 0 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
        <b style={{ fontSize: 14 }}>채팅방 관리</b>
        <button onClick={onClose} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 14 }}>✕</button>
      </div>

      {error && <div className="profile-edit-err">{error}</div>}
      {loading && <div className="profile-empty">불러오는 중...</div>}

      {!loading && detail && !inviting && (
        <>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, marginBottom: 16 }}>
            <input type="checkbox" checked={detail.notificationEnabled} disabled={notifBusy} onChange={toggleNotification} />
            이 채팅방 알림 받기
          </label>

          <div style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--im)', marginBottom: 8 }}>
            참여자 {activeParticipants.length}명
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
            {activeParticipants.map((p) => (
              <div
                key={p.userId}
                style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}
                onClick={() => onNavigateToUser(p.userId)}
              >
                <div className="profile-user-avatar" style={{ width: 28, height: 28 }} />
                <div style={{ flex: 1, fontSize: 13 }}>
                  {profiles[p.userId]?.nickname || '알 수 없음'}
                  {p.role === 'OWNER' && <span style={{ marginLeft: 6, fontSize: 11, color: 'var(--green)' }}>방장</span>}
                </div>
                {myRole === 'OWNER' && p.userId !== myUserId && (
                  <div style={{ display: 'flex', gap: 4 }} onClick={(e) => e.stopPropagation()}>
                    <button
                      disabled={busyUserId === p.userId}
                      onClick={() => transferOwner(p.userId)}
                      style={{ fontSize: 11, background: 'none', border: '1px solid var(--border)', borderRadius: 8, padding: '3px 6px', cursor: 'pointer' }}
                    >위임</button>
                    <button
                      disabled={busyUserId === p.userId}
                      onClick={() => kickUser(p.userId)}
                      style={{ fontSize: 11, background: 'none', border: '1px solid var(--border)', borderRadius: 8, padding: '3px 6px', cursor: 'pointer', color: '#b3402b' }}
                    >강퇴</button>
                  </div>
                )}
              </div>
            ))}
          </div>

          {detail.roomType === 'GROUP' && (
            <button
              onClick={openInvite}
              style={{ width: '100%', marginBottom: 8, fontSize: 12.5, fontWeight: 600, color: 'var(--green)', background: 'rgba(31,111,74,.08)', border: '1px solid rgba(31,111,74,.18)', borderRadius: 10, padding: '9px 0', cursor: 'pointer' }}
            >참여자 초대하기</button>
          )}

          <button
            onClick={leaveRoom}
            disabled={leaveBusy}
            style={{ width: '100%', fontSize: 12.5, fontWeight: 600, color: '#b3402b', background: 'none', border: '1px solid rgba(179,64,43,.3)', borderRadius: 10, padding: '9px 0', cursor: 'pointer' }}
          >{leaveBusy ? '나가는 중...' : '채팅방 나가기'}</button>
        </>
      )}

      {inviting && (
        <>
          <div style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--im)', marginBottom: 8 }}>초대할 사람 선택</div>
          <UserSelectList
            users={followings.filter((f) => !existingIds.has(f.id))}
            loading={followingsLoading}
            selected={inviteSelected}
            onToggle={(id) => setInviteSelected((prev) => {
              const next = new Set(prev);
              if (next.has(id)) next.delete(id); else next.add(id);
              return next;
            })}
            emptyText="초대할 수 있는 사람이 없어요."
          />
          <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
            <button onClick={() => setInviting(false)} style={{ flex: 1, fontSize: 12.5, padding: '8px 0', borderRadius: 10, border: 'none', background: 'var(--bg2)', cursor: 'pointer' }}>취소</button>
            <button onClick={submitInvite} disabled={inviteBusy || inviteSelected.size === 0} style={{ flex: 1, fontSize: 12.5, padding: '8px 0', borderRadius: 10, border: 'none', background: 'var(--green)', color: '#fff', cursor: 'pointer' }}>
              {inviteBusy ? '초대 중...' : '초대하기'}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
