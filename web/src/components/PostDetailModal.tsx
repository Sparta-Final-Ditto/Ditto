import { useCallback, useEffect, useState } from 'react';
import { apiClient, ApiError } from '../lib/apiClient';
import type { CommentListResponse, CommentSummary, PostDetail, PostVisibility } from '../types/post';

interface Props {
  postId: string;
  currentUserId: string;
  onClose: () => void;
  /** 게시물이 삭제/복구/공개범위 변경 등으로 바뀌었을 때 목록을 갱신하고 싶을 경우 사용 */
  onChanged?: () => void;
}

export default function PostDetailModal({ postId, currentUserId, onClose, onChanged }: Props) {
  const [post, setPost] = useState<PostDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(0);
  const [likeBusy, setLikeBusy] = useState(false);

  const [comments, setComments] = useState<CommentSummary[]>([]);
  const [commentsLoading, setCommentsLoading] = useState(false);
  const [commentsCursor, setCommentsCursor] = useState<string | null>(null);
  const [commentsHasNext, setCommentsHasNext] = useState(false);
  const [newComment, setNewComment] = useState('');
  const [commentSubmitting, setCommentSubmitting] = useState(false);

  const [managing, setManaging] = useState(false);
  const [manageVisibility, setManageVisibility] = useState<PostVisibility>('PUBLIC');
  const [manageShowLocation, setManageShowLocation] = useState(true);
  const [manageBusy, setManageBusy] = useState(false);
  const [manageError, setManageError] = useState<string | null>(null);
  const [deleted, setDeleted] = useState(false);
  const [restoring, setRestoring] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    apiClient.get<PostDetail>(`/api/v1/posts/${postId}`)
      .then((detail) => {
        if (cancelled) return;
        setPost(detail);
        setLikeCount(detail.likeCount);
      })
      .catch(() => { if (!cancelled) setPost(null); })
      .finally(() => { if (!cancelled) setLoading(false); });

    // 좋아요 여부 조회는 게시물 상세와 별개로 처리한다 — 실패해도 상세 자체는 보여줘야 한다.
    apiClient.get<{ users: { userId: string }[] }>(`/api/v1/posts/${postId}/likes?size=20`)
      .then((likes) => {
        if (cancelled) return;
        setLiked(likes.users.some((u) => u.userId === currentUserId));
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [postId, currentUserId]);

  const loadComments = useCallback((cursor?: string) => {
    setCommentsLoading(true);
    const query = cursor ? `?cursor=${cursor}&size=20` : '?size=20';
    apiClient.get<CommentListResponse>(`/api/v1/posts/${postId}/comments${query}`)
      .then((res) => {
        setComments((prev) => (cursor ? [...prev, ...res.comments] : res.comments));
        setCommentsCursor(res.nextCursor);
        setCommentsHasNext(res.hasNext);
      })
      .catch(() => { if (!cursor) setComments([]); })
      .finally(() => setCommentsLoading(false));
  }, [postId]);

  useEffect(() => { loadComments(); }, [loadComments]);

  const toggleLike = async () => {
    setLikeBusy(true);
    try {
      const result = liked
        ? await apiClient.delete<{ isLiked: boolean; likeCount: number }>(`/api/v1/posts/${postId}/likes`)
        : await apiClient.post<{ isLiked: boolean; likeCount: number }>(`/api/v1/posts/${postId}/likes`);
      setLiked(result.isLiked);
      setLikeCount(result.likeCount);
    } catch {
      // 실패하면 그대로 둔다 — 다시 눌러보면 재시도된다.
    } finally {
      setLikeBusy(false);
    }
  };

  const submitComment = async (e: React.FormEvent) => {
    e.preventDefault();
    const content = newComment.trim();
    if (!content) return;
    setCommentSubmitting(true);
    try {
      const created = await apiClient.post<CommentSummary>(`/api/v1/posts/${postId}/comments`, { content });
      setComments((prev) => [created, ...prev]);
      setPost((prev) => (prev ? { ...prev, commentCount: prev.commentCount + 1 } : prev));
      setNewComment('');
    } catch {
      // 실패하면 입력값을 유지해서 다시 시도할 수 있게 둔다.
    } finally {
      setCommentSubmitting(false);
    }
  };

  const deleteComment = async (commentId: string) => {
    if (!window.confirm('댓글을 삭제할까요?')) return;
    try {
      await apiClient.delete(`/api/v1/posts/${postId}/comments/${commentId}`);
      setComments((prev) => prev.filter((c) => c.commentId !== commentId));
      setPost((prev) => (prev ? { ...prev, commentCount: Math.max(0, prev.commentCount - 1) } : prev));
    } catch {
      // 실패하면 그대로 둔다.
    }
  };

  const applyDisplaySettings = async () => {
    setManageBusy(true);
    setManageError(null);
    try {
      await apiClient.patch(`/api/v1/posts/${postId}/display`, {
        visibility: manageVisibility,
        showLocation: manageShowLocation,
      });
      setManaging(false);
      onChanged?.();
    } catch (err) {
      setManageError(err instanceof ApiError ? err.message : '변경에 실패했어요.');
    } finally {
      setManageBusy(false);
    }
  };

  const handleDelete = async () => {
    if (!window.confirm('게시물을 삭제할까요? 삭제해도 잠시 후 복구할 수 있어요.')) return;
    setManageBusy(true);
    setManageError(null);
    try {
      await apiClient.delete(`/api/v1/posts/${postId}`);
      setDeleted(true);
      setManaging(false);
    } catch (err) {
      setManageError(err instanceof ApiError ? err.message : '삭제에 실패했어요.');
    } finally {
      setManageBusy(false);
    }
  };

  const handleRestore = async () => {
    setRestoring(true);
    try {
      await apiClient.post(`/api/v1/posts/${postId}/restore`);
      setDeleted(false);
      onChanged?.();
    } catch {
      // 실패하면 삭제된 상태로 남겨두고 다시 시도할 수 있게 둔다.
    } finally {
      setRestoring(false);
    }
  };

  const closeAfterChange = () => {
    onChanged?.();
    onClose();
  };

  return (
    <div className="profile-edit-overlay" onClick={onClose}>
      <div className="post-detail-card" onClick={(e) => e.stopPropagation()}>
        <button className="post-detail-close" onClick={onClose}>✕</button>

        {loading && <div className="profile-empty">불러오는 중...</div>}
        {!loading && !post && <div className="profile-empty">게시물을 찾을 수 없어요.</div>}

        {!loading && post && deleted && (
          <div className="profile-empty">
            게시물을 삭제했어요.<br />
            <div style={{ display: 'flex', gap: 8, justifyContent: 'center', marginTop: 12 }}>
              <button className="profile-edit-save" disabled={restoring} onClick={handleRestore}>
                {restoring ? '복구 중...' : '복구하기'}
              </button>
              <button className="profile-edit-cancel" onClick={closeAfterChange}>닫기</button>
            </div>
          </div>
        )}

        {!loading && post && !deleted && (
          <>
            {post.media.length > 0 && (
              <div className="post-detail-media">
                {post.media[0].mediaType === 'VIDEO' ? (
                  <video src={post.media[0].mediaUrl} controls />
                ) : (
                  <img src={post.media[0].mediaUrl} alt="" />
                )}
              </div>
            )}
            <div className="post-detail-body" style={post.media.length === 0 ? { paddingTop: 44 } : undefined}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <p className="post-detail-content">{post.content}</p>
                {post.isMyPost && (
                  <button
                    className="post-detail-like"
                    style={{ flexShrink: 0 }}
                    onClick={() => setManaging((v) => !v)}
                  >⋯ 관리</button>
                )}
              </div>

              {managing && (
                <div style={{ border: '1px solid var(--border)', borderRadius: 10, padding: 12, display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {manageError && <div className="profile-edit-err">{manageError}</div>}
                  <label style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--im)' }}>
                    공개 범위
                    <select
                      value={manageVisibility}
                      onChange={(e) => setManageVisibility(e.target.value as PostVisibility)}
                      style={{ display: 'block', width: '100%', marginTop: 6, padding: '8px 10px', borderRadius: 8, border: '1px solid var(--border)' }}
                    >
                      <option value="PUBLIC">전체 공개</option>
                      <option value="FOLLOWERS_ONLY">팔로워만</option>
                      <option value="PRIVATE">비공개</option>
                    </select>
                  </label>
                  <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                    <input type="checkbox" checked={manageShowLocation} onChange={(e) => setManageShowLocation(e.target.checked)} />
                    동네 위치 표시하기
                  </label>
                  <div style={{ display: 'flex', gap: 8 }}>
                    <button className="post-detail-like act" disabled={manageBusy} onClick={applyDisplaySettings}>
                      {manageBusy ? '적용 중...' : '적용하기'}
                    </button>
                    <button className="post-detail-like" disabled={manageBusy} onClick={handleDelete} style={{ color: '#b3402b' }}>
                      삭제하기
                    </button>
                  </div>
                </div>
              )}

              <div className="post-detail-actions">
                <button
                  className={`post-detail-like ${liked ? 'act' : ''}`}
                  disabled={likeBusy}
                  onClick={toggleLike}
                >
                  ♥ {liked ? '좋아요 취소' : '좋아요'} {likeCount}
                </button>
                <span className="post-detail-comment-count">댓글 {post.commentCount}</span>
              </div>

              <div className="post-detail-comments">
                {comments.length === 0 && !commentsLoading && (
                  <div className="profile-empty">아직 댓글이 없어요.</div>
                )}
                {comments.map((c) => (
                  <div key={c.commentId} className="post-detail-comment" style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <span><b>{c.author.nickname}</b> {c.content}</span>
                    {c.isDeletable && (
                      <button
                        onClick={() => deleteComment(c.commentId)}
                        style={{ background: 'none', border: 'none', color: 'var(--id)', cursor: 'pointer', fontSize: 11.5, flexShrink: 0 }}
                      >삭제</button>
                    )}
                  </div>
                ))}
                {commentsHasNext && (
                  <div
                    style={{ textAlign: 'center', fontSize: 12.5, color: 'var(--im)', cursor: 'pointer', padding: '4px 0' }}
                    onClick={() => commentsCursor && loadComments(commentsCursor)}
                  >
                    {commentsLoading ? '불러오는 중...' : '댓글 더 보기'}
                  </div>
                )}
              </div>

              <form onSubmit={submitComment} style={{ display: 'flex', gap: 8 }}>
                <input
                  type="text" placeholder="댓글을 남겨보세요" maxLength={200}
                  value={newComment} onChange={(e) => setNewComment(e.target.value)}
                  style={{ flex: 1, fontFamily: 'var(--fk)', fontSize: 13, padding: '9px 12px', border: '1px solid var(--border)', borderRadius: 10, outline: 'none' }}
                />
                <button type="submit" className="profile-edit-save" style={{ flex: 'none', padding: '9px 16px' }} disabled={commentSubmitting || !newComment.trim()}>
                  등록
                </button>
              </form>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
