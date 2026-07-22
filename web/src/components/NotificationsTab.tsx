import { memo } from 'react';
import type { ReactElement } from 'react';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../lib/apiClient';
import type { NotificationItem, NotificationListResponse, NotificationType } from '../types/notification';
import './NotificationsTab.css';

const ICONS: Record<NotificationType, ReactElement> = {
  LIKE: <svg viewBox="0 0 24 24"><path d="M12 21s-7-4.6-9.5-9C.6 8.4 3 5 6.5 5 8.6 5 10.4 6.2 12 8c1.6-1.8 3.4-3 5.5-3 3.5 0 5.9 3.4 4 7-2.5 4.4-9.5 9-9.5 9z" /></svg>,
  COMMENT: <svg viewBox="0 0 24 24"><path d="M4 5h16v11H8l-4 4z" /></svg>,
  CHAT_MESSAGE: <svg viewBox="0 0 24 24"><path d="M4 5h16v11H8l-4 4z" /></svg>,
};

const formatTime = (iso: string) => {
  const diffMs = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diffMs / 60000);
  if (min < 1) return '방금 전';
  if (min < 60) return `${min}분 전`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}시간 전`;
  const day = Math.floor(hour / 24);
  return day === 1 ? '어제' : `${day}일 전`;
};

interface Props {
  onRead?: () => void;
}

function NotificationsTab({ onRead }: Props) {
  const queryClient = useQueryClient();
  const queryKey = ['notifications'];

  const {
    data,
    isLoading: loading,
    error: listError,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage: loadingMore,
  } = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => {
      const query = pageParam ? `?cursor=${pageParam}&size=20` : '?size=20';
      return apiClient.get<NotificationListResponse>(`/api/v1/notifications${query}`);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
  });

  const notifications = data?.pages.flatMap((p) => p.notifications) ?? [];
  const error = listError ? '알림을 불러오지 못했어요.' : null;

  const markRead = async (n: NotificationItem) => {
    if (n.isRead) return;
    queryClient.setQueryData(queryKey, (old?: typeof data) => old && {
      ...old,
      pages: old.pages.map((page) => ({
        ...page,
        notifications: page.notifications.map((item) => (item.notificationId === n.notificationId ? { ...item, isRead: true } : item)),
      })),
    });
    try {
      await apiClient.patch(`/api/v1/notifications/${n.notificationId}/read`);
      onRead?.();
    } catch {
      // 실패하면 다음 새로고침 때 실제 상태로 다시 반영된다.
    }
  };

  return (
    <div>
      <div className="view-head"><h1>알림</h1><p>새 매칭, 대화, 시스템 알림을 모아봤어요.</p></div>

      {loading && notifications.length === 0 && <div className="profile-empty">불러오는 중...</div>}
      {error && <div className="profile-empty">{error}</div>}
      {!loading && !error && notifications.length === 0 && <div className="profile-empty">아직 알림이 없어요.</div>}

      {notifications.map((n) => (
        <div
          className="noti-item"
          key={n.notificationId}
          style={{ opacity: n.isRead ? 0.5 : 1, cursor: 'pointer' }}
          onClick={() => markRead(n)}
        >
          <div className="noti-icon">{ICONS[n.type]}</div>
          <div className="noti-body">
            <div className="noti-text">{n.message}</div>
            <div className="noti-time">{formatTime(n.createdAt)}</div>
          </div>
          {!n.isRead && <div className="noti-dot" />}
        </div>
      ))}

      {hasNextPage && (
        <div
          style={{ textAlign: 'center', fontSize: 13, color: 'var(--im)', cursor: 'pointer', padding: '12px 0' }}
          onClick={() => !loadingMore && fetchNextPage()}
        >
          {loadingMore ? '불러오는 중...' : '더 보기'}
        </div>
      )}
    </div>
  );
}

export default memo(NotificationsTab);
