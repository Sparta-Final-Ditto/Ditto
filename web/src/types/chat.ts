export type RoomType = 'DIRECT' | 'GROUP';
export type RoomStatus = 'ACTIVE' | 'INACTIVE';
export type ParticipantRole = 'OWNER' | 'MEMBER';

export interface ChatParticipant {
  userId: string;
  role: ParticipantRole;
  joinedAt: string;
  leftAt: string | null;
}

export interface ChatRoomDetail {
  roomId: string;
  roomType: RoomType;
  roomName: string | null;
  status: RoomStatus;
  participants: ChatParticipant[];
  notificationEnabled: boolean;
}

export interface ChatDirectRoomResponse {
  roomId: string;
  status: RoomStatus;
  reactivated: boolean;
}

export interface ChatGroupRoomResponse {
  roomId: string;
  roomType: RoomType;
  roomName: string;
  status: RoomStatus;
}

export interface ChatNotificationSettingResponse {
  roomId: string;
  notificationEnabled: boolean;
}

export interface ChatRoomOwnerTransferResponse {
  roomId: string;
  newOwnerId: string;
  previousOwnerId: string;
}

export interface ChatRoomInviteResponse {
  roomId: string;
  invitedUserIds: string[];
}

export interface ChatRoomKickResponse {
  roomId: string;
  status: RoomStatus;
  kickedUserId: string;
  leftAt: string;
  lastVisibleMessageId: string | null;
}

export interface ChatRoomLeaveResponse {
  roomId: string;
  status: RoomStatus;
  leftAt: string;
  lastVisibleMessageId: string | null;
}
