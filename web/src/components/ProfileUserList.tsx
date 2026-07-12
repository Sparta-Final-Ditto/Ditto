import { useState } from 'react';
import type { PublicProfile } from '../types/user';

export default function ProfileUserList({
  users,
  loading,
  emptyText,
  isFollowing,
  onToggleFollow,
  busyIds,
  isBlocked,
  onToggleBlock,
  onReport,
  blockBusyIds,
  onNavigateToUser,
}: {
  users: PublicProfile[];
  loading: boolean;
  emptyText: string;
  isFollowing: (id: string) => boolean;
  onToggleFollow: (id: string) => void;
  busyIds: Set<string>;
  isBlocked?: (id: string) => boolean;
  onToggleBlock?: (id: string) => void;
  onReport?: (id: string) => void;
  blockBusyIds?: Set<string>;
  onNavigateToUser?: (id: string) => void;
}) {
  const [openMenuId, setOpenMenuId] = useState<string | null>(null);

  if (loading) return <div className="profile-empty">불러오는 중...</div>;

  if (users.length === 0) {
    return <div className="profile-empty">{emptyText}</div>;
  }

  return (
    <div className="profile-user-list">
      {users.map((u) => {
        const following = isFollowing(u.id);
        const blocked = isBlocked?.(u.id) ?? false;
        return (
          <div
            key={u.id}
            className="profile-user-row"
            style={{ position: 'relative', cursor: onNavigateToUser ? 'pointer' : undefined }}
            onClick={() => onNavigateToUser?.(u.id)}
          >
            <div
              className="profile-user-avatar"
              style={u.profileImageUrl ? { backgroundImage: `url(${u.profileImageUrl})` } : undefined}
            />
            <div className="profile-user-info">
              <div className="profile-user-name">{u.nickname}</div>
              {u.bio && <div className="profile-user-bio">{u.bio}</div>}
            </div>
            <button
              className={`profile-follow-btn ${following ? 'following' : ''}`}
              disabled={busyIds.has(u.id)}
              onClick={(e) => { e.stopPropagation(); onToggleFollow(u.id); }}
            >
              {following ? '팔로잉' : '팔로우'}
            </button>
            {(onToggleBlock || onReport) && (
              <button
                onClick={(e) => { e.stopPropagation(); setOpenMenuId((prev) => (prev === u.id ? null : u.id)); }}
                style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 16, color: 'var(--id)', padding: '0 4px' }}
              >⋯</button>
            )}
            {openMenuId === u.id && (
              <div
                onClick={(e) => e.stopPropagation()}
                style={{
                  position: 'absolute', top: '100%', right: 0, zIndex: 5, background: '#fff',
                  border: '1px solid var(--border)', borderRadius: 10, boxShadow: '0 8px 24px rgba(18,20,15,.15)',
                  display: 'flex', flexDirection: 'column', minWidth: 120, overflow: 'hidden',
                }}>
                {onToggleBlock && (
                  <button
                    disabled={blockBusyIds?.has(u.id)}
                    onClick={() => { onToggleBlock(u.id); setOpenMenuId(null); }}
                    style={{ padding: '9px 14px', fontSize: 12.5, textAlign: 'left', background: 'none', border: 'none', cursor: 'pointer' }}
                  >{blocked ? '차단 해제' : '차단하기'}</button>
                )}
                {onReport && (
                  <button
                    onClick={() => { onReport(u.id); setOpenMenuId(null); }}
                    style={{ padding: '9px 14px', fontSize: 12.5, textAlign: 'left', background: 'none', border: 'none', cursor: 'pointer', color: '#b3402b' }}
                  >신고하기</button>
                )}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
