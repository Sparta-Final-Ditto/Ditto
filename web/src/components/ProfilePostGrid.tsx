import type { PostItem } from '../types/post';

export default function ProfilePostGrid({
  posts,
  loading,
  onSelect,
  onCreate,
}: {
  posts: PostItem[];
  loading: boolean;
  onSelect: (postId: string) => void;
  onCreate?: () => void;
}) {
  if (loading) return <div className="profile-empty">기록을 불러오는 중...</div>;

  if (posts.length === 0) {
    return (
      <div className="profile-empty">
        아직 남긴 기록이 없어요.<br />
        <span>오늘 느낀 결을 첫 게시물로 남겨보세요.</span>
        {onCreate && (
          <div style={{ marginTop: 14 }}>
            <button
              onClick={onCreate}
              style={{
                fontFamily: 'var(--fk)', fontSize: 12.5, fontWeight: 600, color: '#fff',
                background: 'var(--green)', border: 'none', borderRadius: 100, padding: '9px 18px', cursor: 'pointer',
              }}
            >+ 기록 남기기</button>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="profile-grid">
      {posts.map((p) => (
        <div key={p.postId} className="profile-grid-item" onClick={() => onSelect(p.postId)}>
          {p.thumbnailUrl ? (
            <img src={p.thumbnailUrl} alt="" loading="lazy" />
          ) : (
            <div className="profile-grid-text">{p.contentSummary}</div>
          )}
          {p.mediaType === 'VIDEO' && <span className="profile-grid-video-badge">▶</span>}
        </div>
      ))}
    </div>
  );
}
