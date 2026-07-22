import { useState } from 'react';
import { apiClient, ApiError } from '../lib/apiClient';
import { useFollowings } from '../hooks/useFollow';
import type { ChatDirectRoomResponse, ChatGroupRoomResponse } from '../types/chat';
import UserSelectList from './UserSelectList';
import './CreatePostModal.css';

interface Props {
  onClose: () => void;
  onCreated: (roomId: string) => void;
}

export default function NewChatModal({ onClose, onCreated }: Props) {
  const [mode, setMode] = useState<'direct' | 'group'>('direct');
  const myUserId = localStorage.getItem('userId') || '';
  const { data: followings = [], isLoading: loading } = useFollowings(myUserId);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [roomName, setRoomName] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const toggle = (userId: string) => {
    setSelected((prev) => {
      const next = new Set(mode === 'direct' ? [] : prev);
      if (prev.has(userId) && mode !== 'direct') next.delete(userId);
      else next.add(userId);
      return next;
    });
  };

  const handleSubmit = async () => {
    setError(null);
    if (selected.size === 0) {
      setError('대화할 상대를 선택해주세요.');
      return;
    }
    if (mode === 'group' && !roomName.trim()) {
      setError('방 이름을 입력해주세요.');
      return;
    }
    setSubmitting(true);
    try {
      if (mode === 'direct') {
        const [targetUserId] = Array.from(selected);
        const res = await apiClient.post<ChatDirectRoomResponse>('/api/v1/chat/rooms/direct', { targetUserId });
        onCreated(res.roomId);
      } else {
        const res = await apiClient.post<ChatGroupRoomResponse>('/api/v1/chat/rooms/group', {
          participantUserIds: Array.from(selected),
          roomName: roomName.trim(),
        });
        onCreated(res.roomId);
      }
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '채팅방을 만들지 못했어요.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="profile-edit-overlay" onClick={onClose}>
      <div className="create-post-card" onClick={(e) => e.stopPropagation()}>
        <h2>새 채팅 시작하기</h2>
        <p>팔로우한 사람 중에서 대화할 상대를 골라보세요.</p>

        {error && <div className="create-post-err">{error}</div>}

        <div className="field" style={{ flexDirection: 'row', gap: 8 }}>
          <button
            type="button"
            className={mode === 'direct' ? 'create-post-submit' : 'create-post-cancel'}
            style={{ flex: 1 }}
            onClick={() => { setMode('direct'); setSelected(new Set()); }}
          >1:1 대화</button>
          <button
            type="button"
            className={mode === 'group' ? 'create-post-submit' : 'create-post-cancel'}
            style={{ flex: 1 }}
            onClick={() => { setMode('group'); setSelected(new Set()); }}
          >그룹 대화</button>
        </div>

        {mode === 'group' && (
          <div className="field">
            <label htmlFor="room-name">방 이름</label>
            <input id="room-name" type="text" value={roomName} onChange={(e) => setRoomName(e.target.value)} />
          </div>
        )}

        <div style={{ maxHeight: 260, overflowY: 'auto', marginBottom: 14 }}>
          <UserSelectList
            users={followings}
            loading={loading}
            selected={selected}
            onToggle={toggle}
            emptyText="팔로우한 사람이 없어요. 먼저 팔로우를 해보세요."
          />
        </div>

        <div className="create-post-actions">
          <button type="button" className="create-post-cancel" onClick={onClose} disabled={submitting}>취소</button>
          <button type="button" className="create-post-submit" onClick={handleSubmit} disabled={submitting}>
            {submitting ? '만드는 중...' : '시작하기'}
          </button>
        </div>
      </div>
    </div>
  );
}
