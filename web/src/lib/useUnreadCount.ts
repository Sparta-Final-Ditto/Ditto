import { useCallback, useEffect, useState } from 'react';
import { apiClient } from './apiClient';
import type { UnreadCountResponse } from '../types/notification';

const POLL_INTERVAL_MS = 25000;

/** SSE 대신 폴링으로 읽지 않은 알림 개수를 주기적으로 갱신한다. */
export function useUnreadCount() {
  const [unreadCount, setUnreadCount] = useState(0);

  const refresh = useCallback(() => {
    apiClient.get<UnreadCountResponse>('/api/v1/notifications/unread-count')
      .then((res) => setUnreadCount(res.unreadCount))
      .catch(() => {});
  }, []);

  useEffect(() => {
    let cancelled = false;
    const fetchCount = () => {
      apiClient.get<UnreadCountResponse>('/api/v1/notifications/unread-count')
        .then((res) => { if (!cancelled) setUnreadCount(res.unreadCount); })
        .catch(() => {});
    };
    fetchCount();
    const interval = setInterval(fetchCount, POLL_INTERVAL_MS);
    return () => { cancelled = true; clearInterval(interval); };
  }, []);

  return { unreadCount, refresh };
}
