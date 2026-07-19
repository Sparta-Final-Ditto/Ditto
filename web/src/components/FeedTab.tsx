import { memo, useCallback, useEffect, useState } from 'react';
import { apiClient } from '../lib/apiClient';
import type { FeedItem, FeedPageResponse } from '../types/post';
import CreatePostModal from './CreatePostModal';
import PostDetailModal from './PostDetailModal';
import './FeedTab.css';

type FeedType = 'follow' | 'match' | 'random';

const ENDPOINTS: Record<FeedType, string> = {
  follow: '/api/v1/feeds/follow',
  match: '/api/v1/feeds/match',
  random: '/api/v1/feeds/random',
};

const CommentIcon = () => (
  <svg viewBox="0 0 24 24"><path d="M4 12a8 8 0 1 1 8 8H6l-2 2z" /></svg>
);
const HeartIcon = () => (
  <svg viewBox="0 0 24 24"><path d="M12 21s-7-4.6-9.5-9C.6 8.4 3 5 6.5 5 8.6 5 10.4 6.2 12 8c1.6-1.8 3.4-3 5.5-3 3.5 0 5.9 3.4 4 7-2.5 4.4-9.5 9-9.5 9z" /></svg>
);

const formatTime = (iso: string) => {
  const diffMs = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diffMs / 60000);
  if (min < 1) return '방금 전';
  if (min < 60) return `${min}분 전`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}시간 전`;
  const day = Math.floor(hour / 24);
  if (day < 7) return day === 1 ? '어제' : `${day}일 전`;
  return new Date(iso).toLocaleDateString('ko-KR');
};

function PostCard({ post, onSelect }: { post: FeedItem; onSelect: (postId: string) => void }) {
  return (
    <div className="post" onClick={() => onSelect(post.postId)} style={{ cursor: 'pointer' }}>
      <div className="post-head">
        <div className="post-av g1" />
        <div><div className="post-name">{post.author.nickname}</div><div className="post-time">{formatTime(post.createdAt)}{post.neighborhood ? ` · ${post.neighborhood}` : ''}</div></div>
      </div>
      {post.mediaFiles.length > 0 && (
        <div className="post-media">
          {post.mediaFiles[0].mediaType === 'VIDEO' ? (
            <video src={post.mediaFiles[0].mediaUrl} controls onClick={(e) => e.stopPropagation()} />
          ) : (
            <img src={post.mediaFiles[0].mediaUrl} alt="" />
          )}
        </div>
      )}
      {post.content && <div className="post-body">{post.content}</div>}
      {post.tags.length > 0 && (
        <div className="post-tags">{post.tags.map((t) => <span key={t} className="post-tag">#{t}</span>)}</div>
      )}
      <div className="post-actions">
        <div className="post-action"><CommentIcon />댓글 {post.commentCount}</div>
        <div className="post-action"><HeartIcon />좋아요 {post.likeCount}</div>
      </div>
    </div>
  );
}

function FeedTab() {
  const [active, setActive] = useState<FeedType>('follow');
  const [posts, setPosts] = useState<FeedItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [cursor, setCursor] = useState<string | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [creating, setCreating] = useState(false);
  const [selectedPostId, setSelectedPostId] = useState<string | null>(null);

  const currentUserId = localStorage.getItem('userId') || '';

  const load = useCallback((type: FeedType, after?: string | null) => {
    setLoading(true);
    setError(null);
    const query = after ? `?cursor=${after}&size=20` : '?size=20';
    apiClient.get<FeedPageResponse>(`${ENDPOINTS[type]}${query}`)
      .then((res) => {
        setPosts((prev) => (after ? [...prev, ...res.feeds] : res.feeds));
        setCursor(res.nextCursor);
        setHasNext(res.hasNext);
      })
      .catch(() => setError('피드를 불러오지 못했어요.'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(active); }, [active, load]);

  const refresh = useCallback(() => load(active), [active, load]);

  return (
    <div>
      <div className="view-head" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h1>피드</h1>
          <p>친구들의 최근 관심사와 활동을 확인해보세요.</p>
        </div>
        <button
          onClick={() => setCreating(true)}
          style={{
            fontFamily: 'var(--fk)', fontSize: 12.5, fontWeight: 600, color: '#fff',
            background: 'var(--green)', border: 'none', borderRadius: 100, padding: '9px 16px', cursor: 'pointer',
          }}
        >+ 새 게시물</button>
      </div>

      <div className="feed-tabs">
        <div className={`feed-tab ${active === 'follow' ? 'act' : ''}`} onClick={() => setActive('follow')}>팔로우</div>
        <div className={`feed-tab ${active === 'match' ? 'act' : ''}`} onClick={() => setActive('match')}>매칭 후보</div>
        <div className={`feed-tab ${active === 'random' ? 'act' : ''}`} onClick={() => setActive('random')}>랜덤</div>
      </div>

      {loading && posts.length === 0 && <div className="profile-empty">불러오는 중...</div>}
      {error && <div className="profile-empty">{error}</div>}
      {!loading && !error && posts.length === 0 && <div className="profile-empty">아직 표시할 게시물이 없어요.</div>}

      {posts.map((p) => <PostCard key={p.postId} post={p} onSelect={setSelectedPostId} />)}

      {hasNext && (
        <div
          style={{ textAlign: 'center', fontSize: 13, color: 'var(--im)', cursor: 'pointer', padding: '12px 0' }}
          onClick={() => !loading && cursor && load(active, cursor)}
        >
          {loading ? '불러오는 중...' : '더 보기'}
        </div>
      )}

      {creating && (
        <CreatePostModal onClose={() => setCreating(false)} onCreated={refresh} />
      )}

      {selectedPostId && (
        <PostDetailModal
          postId={selectedPostId}
          currentUserId={currentUserId}
          onClose={() => setSelectedPostId(null)}
          onChanged={refresh}
        />
      )}
    </div>
  );
}

export default memo(FeedTab);
