export type NotificationType = 'LIKE' | 'COMMENT' | 'CHAT_MESSAGE';

export interface NotificationItem {
  notificationId: string;
  type: NotificationType;
  actorId: string | null;
  targetType: NotificationType;
  targetId: string;
  message: string;
  isRead: boolean;
  metaData: string | null;
  createdAt: string;
  roomUnreadCount: number | null;
}

export interface NotificationListResponse {
  unreadCount: number;
  notifications: NotificationItem[];
  nextCursor: string | null;
  hasNext: boolean;
}

export interface UnreadCountResponse {
  unreadCount: number;
}
