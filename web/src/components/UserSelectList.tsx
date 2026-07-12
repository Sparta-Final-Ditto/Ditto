import type { PublicProfile } from '../types/user';

interface Props {
  users: PublicProfile[];
  loading: boolean;
  selected: Set<string>;
  onToggle: (userId: string) => void;
  emptyText: string;
}

export default function UserSelectList({ users, loading, selected, onToggle, emptyText }: Props) {
  if (loading) return <div className="profile-empty">불러오는 중...</div>;
  if (users.length === 0) return <div className="profile-empty">{emptyText}</div>;

  return (
    <div className="profile-user-list">
      {users.map((u) => {
        const checked = selected.has(u.id);
        return (
          <div
            key={u.id}
            className="profile-user-row"
            style={{ cursor: 'pointer' }}
            onClick={() => onToggle(u.id)}
          >
            <div
              className="profile-user-avatar"
              style={u.profileImageUrl ? { backgroundImage: `url(${u.profileImageUrl})` } : undefined}
            />
            <div className="profile-user-info">
              <div className="profile-user-name">{u.nickname}</div>
            </div>
            <input type="checkbox" checked={checked} readOnly style={{ flexShrink: 0 }} />
          </div>
        );
      })}
    </div>
  );
}
