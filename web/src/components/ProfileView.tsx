import { useState, useEffect, useCallback } from 'react';
import ProfileEditForm from './ProfileEditForm';
import ProfilePostGrid from './ProfilePostGrid';
import ProfileUserList from './ProfileUserList';
import PostDetailModal from './PostDetailModal';
import ReportModal from './ReportModal';
import CreatePostModal from './CreatePostModal';
import { apiClient, ApiError } from '../lib/apiClient';
import type { UserProfile, PublicProfile } from '../types/user';
import type { PostItem } from '../types/post';
import './ProfileTab.css';

type SubTab = 'posts' | 'followers' | 'followings';

interface Props {
  userId: string;
  currentUserId: string;
  onNavigateToUser: (userId: string) => void;
  onBack?: () => void;
}

export default function ProfileView({ userId, currentUserId, onNavigateToUser, onBack }: Props) {
  const isOwn = userId === currentUserId;

  const [profile, setProfile] = useState<UserProfile | PublicProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [creating, setCreating] = useState(false);

  const [sub, setSub] = useState<SubTab>('posts');
  const [posts, setPosts] = useState<PostItem[]>([]);
  const [postsLoading, setPostsLoading] = useState(false);
  const [followers, setFollowers] = useState<PublicProfile[]>([]);
  const [followings, setFollowings] = useState<PublicProfile[]>([]);
  const [followLoading, setFollowLoading] = useState(false);
  const [myFollowings, setMyFollowings] = useState<PublicProfile[]>([]);
  const [followBusyIds, setFollowBusyIds] = useState<Set<string>>(new Set());
  const [selectedPostId, setSelectedPostId] = useState<string | null>(null);
  const [blockedIds, setBlockedIds] = useState<Set<string>>(new Set());
  const [blockBusyIds, setBlockBusyIds] = useState<Set<string>>(new Set());
  const [reportTargetId, setReportTargetId] = useState<string | null>(null);
  const [headerFollowBusy, setHeaderFollowBusy] = useState(false);
  const [followActionError, setFollowActionError] = useState<string | null>(null);

  useEffect(() => { setSub('posts'); }, [userId]);

  const fetchProfile = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = isOwn
        ? await apiClient.get<UserProfile>('/api/v1/users/me')
        : await apiClient.get<PublicProfile>(`/api/v1/users/${userId}`);
      setProfile(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '프로필을 불러오지 못했어요.');
    } finally {
      setLoading(false);
    }
  }, [userId, isOwn]);

  useEffect(() => { fetchProfile(); }, [fetchProfile]);

  const fetchPosts = useCallback(() => {
    setPostsLoading(true);
    apiClient.get<{ posts: PostItem[] }>(`/api/v1/posts/users/${userId}`)
      .then((res) => setPosts(res.posts))
      .catch(() => setPosts([]))
      .finally(() => setPostsLoading(false));
  }, [userId]);

  useEffect(() => {
    if (sub === 'posts') fetchPosts();
  }, [sub, fetchPosts]);

  // 팔로워/팔로잉은 상단 통계 숫자에도 쓰이므로 탭 전환과 무관하게 미리 불러온다.
  // 팔로우/언팔로우 액션 이후에도 재호출해서 카운트·목록을 최신 상태로 맞춘다.
  const fetchFollowLists = useCallback(() => {
    setFollowLoading(true);
    return Promise.all([
      apiClient.get<PublicProfile[]>(`/api/v1/users/${userId}/followers`),
      apiClient.get<PublicProfile[]>(`/api/v1/users/${userId}/followings`),
    ])
      .then(([followersData, followingsData]) => {
        setFollowers(followersData);
        setFollowings(followingsData);
      })
      .catch(() => {
        setFollowers([]);
        setFollowings([]);
      })
      .finally(() => setFollowLoading(false));
  }, [userId]);

  useEffect(() => { fetchFollowLists(); }, [fetchFollowLists]);

  // 팔로우 여부는 항상 "내" 팔로잉 목록을 기준으로 판단한다(내 프로필이든 남의 프로필이든 동일).
  const fetchMyFollowings = useCallback(() => {
    apiClient.get<PublicProfile[]>(`/api/v1/users/${currentUserId}/followings`)
      .then(setMyFollowings)
      .catch(() => setMyFollowings([]));
  }, [currentUserId]);

  useEffect(() => { fetchMyFollowings(); }, [fetchMyFollowings]);

  useEffect(() => {
    apiClient.get<PublicProfile[]>('/api/v1/users/me/blocks')
      .then((list) => setBlockedIds(new Set(list.map((u) => u.id))))
      .catch(() => {});
  }, []);

  const isBlocked = useCallback((id: string) => blockedIds.has(id), [blockedIds]);

  const toggleBlock = useCallback(async (targetId: string) => {
    const currentlyBlocked = blockedIds.has(targetId);
    setBlockBusyIds((prev) => new Set(prev).add(targetId));
    try {
      if (currentlyBlocked) {
        await apiClient.delete(`/api/v1/users/${targetId}/block`);
        setBlockedIds((prev) => { const next = new Set(prev); next.delete(targetId); return next; });
      } else {
        await apiClient.post(`/api/v1/users/${targetId}/block`);
        setBlockedIds((prev) => new Set(prev).add(targetId));
      }
    } catch {
      // 실패하면 그대로 둔다 — 다시 눌러보면 재시도된다.
    } finally {
      setBlockBusyIds((prev) => { const next = new Set(prev); next.delete(targetId); return next; });
    }
  }, [blockedIds]);

  const isFollowing = useCallback(
    (id: string) => myFollowings.some((f) => f.id === id),
    [myFollowings],
  );

  const toggleFollow = useCallback(async (targetId: string) => {
    const currentlyFollowing = myFollowings.some((f) => f.id === targetId);
    setFollowBusyIds((prev) => new Set(prev).add(targetId));
    setFollowActionError(null);
    try {
      await apiClient[currentlyFollowing ? 'delete' : 'post'](`/api/v1/users/${targetId}/follow`);
      if (currentlyFollowing) {
        setMyFollowings((prev) => prev.filter((f) => f.id !== targetId));
      } else {
        setMyFollowings((prev) => {
          if (prev.some((f) => f.id === targetId)) return prev;
          const known = followers.find((f) => f.id === targetId) || followings.find((f) => f.id === targetId);
          return known ? [...prev, known] : [...prev, { id: targetId, nickname: '', profileImageUrl: null, bio: null }];
        });
      }
      // 버튼 상태(myFollowings)뿐 아니라 지금 보고 있는 프로필의 팔로워/팔로잉 카운트·목록도
      // 최신 상태로 맞춘다 (예: 내 프로필의 팔로잉 탭에서 언팔로우한 사람이 계속 남아있던 문제).
      fetchFollowLists();
    } catch (err) {
      setFollowActionError(err instanceof ApiError ? err.message : '팔로우 처리에 실패했어요. 다시 시도해주세요.');
    } finally {
      setFollowBusyIds((prev) => { const next = new Set(prev); next.delete(targetId); return next; });
    }
  }, [myFollowings, followers, followings, fetchFollowLists]);

  const toggleHeaderFollow = async () => {
    setHeaderFollowBusy(true);
    await toggleFollow(userId);
    setHeaderFollowBusy(false);
  };

  if (loading) {
    return <div className="view-head"><h1>프로필</h1><p>불러오는 중...</p></div>;
  }

  if (error || !profile) {
    return <div className="view-head"><h1>프로필</h1><p>{error || '프로필을 찾을 수 없어요.'}</p></div>;
  }

  const neighborhood = 'neighborhood' in profile ? profile.neighborhood : null;

  return (
    <div className="profile-page">
      {onBack && (
        <div className="profile-back" onClick={onBack}>‹ 뒤로</div>
      )}

      <div className="profile-header">
        <div
          className="profile-avatar-lg"
          style={profile.profileImageUrl ? { backgroundImage: `url(${profile.profileImageUrl})` } : undefined}
        />
        <div className="profile-header-info">
          <div className="profile-name-row">
            <h1>{profile.nickname}</h1>
            {isOwn ? (
              <button className="profile-edit-btn" onClick={() => setEditing(true)}>내 결 편집하기</button>
            ) : (
              <div style={{ display: 'flex', gap: 8 }}>
                <button
                  className={`profile-follow-btn ${isFollowing(userId) ? 'following' : ''}`}
                  disabled={headerFollowBusy}
                  onClick={toggleHeaderFollow}
                >
                  {isFollowing(userId) ? '팔로잉' : '팔로우'}
                </button>
                <button
                  onClick={() => setReportTargetId(userId)}
                  style={{ fontFamily: 'var(--fk)', fontSize: 12.5, fontWeight: 600, color: '#b3402b', background: 'none', border: '1px solid rgba(179,64,43,.25)', borderRadius: 100, padding: '7px 14px', cursor: 'pointer' }}
                >신고</button>
                <button
                  onClick={() => toggleBlock(userId)}
                  disabled={blockBusyIds.has(userId)}
                  style={{ fontFamily: 'var(--fk)', fontSize: 12.5, fontWeight: 600, color: 'var(--im)', background: 'none', border: '1px solid var(--border)', borderRadius: 100, padding: '7px 14px', cursor: 'pointer' }}
                >{isBlocked(userId) ? '차단 해제' : '차단'}</button>
              </div>
            )}
          </div>
          {followActionError && (
            <div style={{ fontFamily: 'var(--fk)', fontSize: 12.5, color: '#b3402b', marginTop: 4 }}>
              {followActionError}
            </div>
          )}
          <div className="profile-bio">
            {profile.bio || '아직 자신의 결을 소개하지 않았어요.'}
          </div>
          {neighborhood && <div className="profile-neighborhood">📍 {neighborhood}</div>}

          <div className="profile-stats">
            <div className={`profile-stat ${sub === 'posts' ? 'act' : ''}`} onClick={() => setSub('posts')}>
              <b>{posts.length}</b><span>게시물</span>
            </div>
            <div className={`profile-stat ${sub === 'followers' ? 'act' : ''}`} onClick={() => setSub('followers')}>
              <b>{followers.length}</b><span>팔로워</span>
            </div>
            <div className={`profile-stat ${sub === 'followings' ? 'act' : ''}`} onClick={() => setSub('followings')}>
              <b>{followings.length}</b><span>팔로잉</span>
            </div>
          </div>
        </div>
      </div>

      <div className="profile-subnav">
        <div className={sub === 'posts' ? 'act' : ''} onClick={() => setSub('posts')}>게시물</div>
        <div className={sub === 'followers' ? 'act' : ''} onClick={() => setSub('followers')}>팔로워</div>
        <div className={sub === 'followings' ? 'act' : ''} onClick={() => setSub('followings')}>팔로잉</div>
        {isOwn && sub === 'posts' && posts.length > 0 && (
          <button
            onClick={() => setCreating(true)}
            style={{
              marginLeft: 'auto', alignSelf: 'center', fontFamily: 'var(--fk)', fontSize: 12.5, fontWeight: 600,
              color: '#fff', background: 'var(--green)', border: 'none', borderRadius: 100, padding: '6px 14px', cursor: 'pointer',
            }}
          >+ 게시물</button>
        )}
      </div>

      {sub === 'posts' && (
        <ProfilePostGrid
          posts={posts}
          loading={postsLoading}
          onSelect={setSelectedPostId}
          onCreate={isOwn ? () => setCreating(true) : undefined}
        />
      )}
      {sub === 'followers' && (
        <ProfileUserList
          users={followers}
          loading={followLoading}
          emptyText="아직 같은 결을 알아본 사람이 없어요."
          isFollowing={isFollowing}
          onToggleFollow={toggleFollow}
          busyIds={followBusyIds}
          isBlocked={isBlocked}
          onToggleBlock={toggleBlock}
          blockBusyIds={blockBusyIds}
          onReport={setReportTargetId}
          onNavigateToUser={onNavigateToUser}
        />
      )}
      {sub === 'followings' && (
        <ProfileUserList
          users={followings}
          loading={followLoading}
          emptyText="아직 결을 맞춰본 사람이 없어요."
          isFollowing={isFollowing}
          onToggleFollow={toggleFollow}
          busyIds={followBusyIds}
          isBlocked={isBlocked}
          onToggleBlock={toggleBlock}
          blockBusyIds={blockBusyIds}
          onReport={setReportTargetId}
          onNavigateToUser={onNavigateToUser}
        />
      )}

      {editing && isOwn && (
        <ProfileEditForm
          profile={profile as UserProfile}
          onClose={() => setEditing(false)}
          onSaved={(updated) => {
            setProfile({ ...profile, ...updated });
            setEditing(false);
          }}
        />
      )}

      {creating && (
        <CreatePostModal onClose={() => setCreating(false)} onCreated={fetchPosts} />
      )}

      {selectedPostId && (
        <PostDetailModal
          postId={selectedPostId}
          currentUserId={currentUserId}
          onClose={() => setSelectedPostId(null)}
          onChanged={fetchPosts}
        />
      )}

      {reportTargetId && (
        <ReportModal
          targetUserId={reportTargetId}
          onClose={() => setReportTargetId(null)}
          onSubmitted={() => {}}
        />
      )}
    </div>
  );
}
