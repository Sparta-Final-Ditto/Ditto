import { memo, useState, useCallback, useMemo } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import ProfileEditForm from './ProfileEditForm';
import ProfilePostGrid from './ProfilePostGrid';
import ProfileUserList from './ProfileUserList';
import PostDetailModal from './PostDetailModal';
import ReportModal from './ReportModal';
import CreatePostModal from './CreatePostModal';
import { apiClient, ApiError } from '../lib/apiClient';
import { useFollowers, useFollowings, useToggleFollow } from '../hooks/useFollow';
import { useBlocks, useToggleBlock } from '../hooks/useBlock';
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

function ProfileView({ userId, currentUserId, onNavigateToUser, onBack }: Props) {
  const isOwn = userId === currentUserId;
  const queryClient = useQueryClient();

  const [editing, setEditing] = useState(false);
  const [creating, setCreating] = useState(false);

  // userId가 바뀌면(다른 사람 프로필로 이동) 하위 탭을 게시물로 되돌린다 — 렌더 중 조정이라 effect가 필요 없다.
  const [prevUserId, setPrevUserId] = useState(userId);
  const [sub, setSub] = useState<SubTab>('posts');
  if (userId !== prevUserId) {
    setPrevUserId(userId);
    setSub('posts');
  }

  const [followBusyIds, setFollowBusyIds] = useState<Set<string>>(new Set());
  const [selectedPostId, setSelectedPostId] = useState<string | null>(null);
  const [blockBusyIds, setBlockBusyIds] = useState<Set<string>>(new Set());
  const [reportTargetId, setReportTargetId] = useState<string | null>(null);
  const [headerFollowBusy, setHeaderFollowBusy] = useState(false);
  const [followActionError, setFollowActionError] = useState<string | null>(null);

  const profileQueryKey = ['profile', userId, isOwn];
  const { data: profile, isLoading: loading, error: profileError } = useQuery<UserProfile | PublicProfile>({
    queryKey: profileQueryKey,
    queryFn: () => isOwn
      ? apiClient.get<UserProfile>('/api/v1/users/me')
      : apiClient.get<PublicProfile>(`/api/v1/users/${userId}`),
  });
  const error = profileError ? (profileError instanceof ApiError ? profileError.message : '프로필을 불러오지 못했어요.') : null;

  const { data: posts = [], isLoading: postsLoading, refetch: fetchPosts } = useQuery({
    queryKey: ['posts', userId],
    queryFn: () => apiClient.get<{ posts: PostItem[] }>(`/api/v1/posts/users/${userId}`).then((res) => res.posts),
    enabled: sub === 'posts',
  });

  // 팔로워/팔로잉은 상단 통계 숫자에도 쓰이므로 탭 전환과 무관하게 미리 불러온다.
  // react-query 캐시를 쓰므로 같은 userId를 다른 화면에서 이미 조회했다면 재요청하지 않는다.
  const { data: followers = [], isLoading: followersLoading } = useFollowers(userId);
  const { data: followings = [], isLoading: followingsLoading } = useFollowings(userId);
  const followLoading = followersLoading || followingsLoading;

  // 팔로우 여부는 항상 "내" 팔로잉 목록을 기준으로 판단한다(내 프로필이든 남의 프로필이든 동일).
  const { data: myFollowings = [] } = useFollowings(currentUserId);
  const toggleFollowMutation = useToggleFollow();

  const { data: blockedUsers = [] } = useBlocks();
  const blockedIds = useMemo(() => new Set(blockedUsers.map((u) => u.id)), [blockedUsers]);
  const toggleBlockMutation = useToggleBlock();

  const isBlocked = useCallback((id: string) => blockedIds.has(id), [blockedIds]);

  const toggleBlock = useCallback(async (targetId: string) => {
    const currentlyBlocked = blockedIds.has(targetId);
    setBlockBusyIds((prev) => new Set(prev).add(targetId));
    try {
      await toggleBlockMutation.mutateAsync({ targetId, currentlyBlocked });
    } catch {
      // 실패하면 그대로 둔다 — 다시 눌러보면 재시도된다.
    } finally {
      setBlockBusyIds((prev) => { const next = new Set(prev); next.delete(targetId); return next; });
    }
  }, [blockedIds, toggleBlockMutation]);

  const isFollowing = useCallback(
    (id: string) => myFollowings.some((f) => f.id === id),
    [myFollowings],
  );

  const toggleFollow = useCallback(async (targetId: string) => {
    const currentlyFollowing = myFollowings.some((f) => f.id === targetId);
    const knownProfile = followers.find((f) => f.id === targetId) || followings.find((f) => f.id === targetId);
    setFollowBusyIds((prev) => new Set(prev).add(targetId));
    setFollowActionError(null);
    try {
      // mutateAsync가 낙관적 업데이트로 myFollowings 캐시를 즉시 갱신하고,
      // 완료 후 이 프로필(userId)의 팔로워/팔로잉 목록도 자동으로 무효화한다.
      await toggleFollowMutation.mutateAsync({ currentUserId, targetId, currentlyFollowing, knownProfile });
    } catch (err) {
      setFollowActionError(err instanceof ApiError ? err.message : '팔로우 처리에 실패했어요. 다시 시도해주세요.');
    } finally {
      setFollowBusyIds((prev) => { const next = new Set(prev); next.delete(targetId); return next; });
    }
  }, [myFollowings, followers, followings, toggleFollowMutation, currentUserId]);

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
            queryClient.setQueryData(profileQueryKey, (prev: UserProfile | PublicProfile | undefined) => (prev ? { ...prev, ...updated } : prev));
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

export default memo(ProfileView);
