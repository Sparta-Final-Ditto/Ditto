import { memo, useState, useEffect, useRef, useCallback } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import { apiClient } from '../lib/apiClient';
import NewChatModal from './NewChatModal';
import RoomManagePanel from './RoomManagePanel';
import './Chat.css';

const WS_URL = `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws-chat`;

interface ChatMessage {
    clientMessageId?: string;
    messageId?: string;
    senderId: string;
    content: string;
    messageType: 'TEXT' | 'IMAGE' | 'SYSTEM_JOIN' | 'SYSTEM_LEAVE' | 'SYSTEM_INVITE' | 'SYSTEM_KICK' | string;
    createdAt?: string;
    deletedAt?: string | null;
}

interface RoomSummary {
    roomId: string;
    roomType: 'DIRECT' | 'GROUP';
    roomName: string | null;
    lastMessage: string | null;
    lastMessageAt: string | null;
    unreadCount: number;
    notificationEnabled: boolean;
}

const authHeaders = () => ({ Authorization: `Bearer ${localStorage.getItem('accessToken')}` });

const formatRoomTime = (iso: string | null) => {
    if (!iso) return '';
    const d = new Date(iso);
    const now = new Date();
    const sameDay = d.toDateString() === now.toDateString();
    return sameDay
        ? d.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
        : `${d.getMonth() + 1}.${d.getDate()}`;
};

interface Props {
    onNavigateToUser: (userId: string) => void;
}

function Chat({ onNavigateToUser }: Props) {
    const myUserId = localStorage.getItem('userId') || '';
    const token = localStorage.getItem('accessToken') || '';

    const [rooms, setRooms] = useState<RoomSummary[]>([]);
    const [roomsLoading, setRoomsLoading] = useState(true);
    const [displayNames, setDisplayNames] = useState<Record<string, string>>({});
    const [activeRoomId, setActiveRoomId] = useState<string | null>(null);

    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [historyLoading, setHistoryLoading] = useState(false);
    const [oldestCursor, setOldestCursor] = useState<string | null>(null);
    const [input, setInput] = useState('');
    const [connected, setConnected] = useState(false);
    const [creating, setCreating] = useState(false);
    const [managing, setManaging] = useState(false);
    const stompRef = useRef<Client | null>(null);

    // 방 이름이 없는 1:1 채팅방은 상대방 닉네임을 조회해서 채워 넣는다.
    const resolveDirectName = useCallback(async (roomId: string) => {
        try {
            const roomRes = await fetch(`/api/v1/chat/rooms/${roomId}`, { headers: authHeaders() });
            if (!roomRes.ok) return;
            const roomData = await roomRes.json();
            const other = roomData.data.participants.find((p: { userId: string }) => p.userId !== myUserId);
            if (!other) return;

            const userRes = await fetch(`/api/v1/users/${other.userId}`, { headers: authHeaders() });
            if (!userRes.ok) return;
            const userData = await userRes.json();
            setDisplayNames((prev) => ({ ...prev, [roomId]: userData.data.nickname }));
        } catch {
            // 실패하면 방 이름 없이 기본 라벨로 표시된다.
        }
    }, [myUserId]);

    const fetchRooms = useCallback(async () => {
        setRoomsLoading(true);
        try {
            const res = await fetch('/api/v1/chat/rooms', { headers: authHeaders() });
            if (!res.ok) throw new Error();
            const data = await res.json();
            const list: RoomSummary[] = data.data;
            setRooms(list);
            if (!activeRoomId && list.length > 0) setActiveRoomId(list[0].roomId);
            list.filter((r) => r.roomType === 'DIRECT' && !r.roomName).forEach((r) => resolveDirectName(r.roomId));
        } catch {
            setRooms([]);
        } finally {
            setRoomsLoading(false);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // fetchRooms는 최초 진입 시와 새 채팅 생성 후 모두에서 쓰는 공용 refetch 함수라
    // effect 안에서만 쓰는 별도 버전으로 쪼개지 않았다 — 마운트 시 1회만 실행된다.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    useEffect(() => { fetchRooms(); }, [fetchRooms]);

    const roomLabel = (room: RoomSummary) =>
        room.roomType === 'GROUP' ? (room.roomName || '그룹 채팅') : (displayNames[room.roomId] || '상대방');

    const markRead = useCallback(async (roomId: string, lastReadMessageId: string) => {
        try {
            await fetch(`/api/v1/chat/rooms/${roomId}/read`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json', ...authHeaders() },
                body: JSON.stringify({ lastReadMessageId }),
            });
            setRooms((prev) => prev.map((r) => (r.roomId === roomId ? { ...r, unreadCount: 0 } : r)));
        } catch {
            // 실패해도 다음 메시지 수신 시 다시 시도된다.
        }
    }, []);

    const connect = useCallback((roomId: string) => {
        if (!token) return;

        const brokerURL = `${WS_URL}?token=${encodeURIComponent(token)}`;
        const client = new Client({
            brokerURL,
            reconnectDelay: 4000,
            onConnect: () => {
                setConnected(true);
                client.subscribe(`/sub/chat/rooms/${roomId}`, (message: IMessage) => {
                    const data: ChatMessage = JSON.parse(message.body);
                    setMessages((prev) => [...prev, data]);
                    setRooms((prev) => {
                        const updated = prev.map((r) => (r.roomId === roomId
                            ? {
                                ...r,
                                lastMessage: data.messageType === 'TEXT' ? data.content : r.lastMessage,
                                lastMessageAt: data.createdAt ?? r.lastMessageAt,
                                unreadCount: 0,
                            }
                            : r));
                        return [...updated].sort((a, b) => (b.lastMessageAt ?? '').localeCompare(a.lastMessageAt ?? ''));
                    });
                    if (data.messageId) markRead(roomId, data.messageId);
                });
                client.subscribe('/user/sub/chat/errors', (message: IMessage) => {
                    const data = JSON.parse(message.body);
                    console.error('채팅 에러:', data.message || data.errorType);
                });
                client.publish({
                    destination: `/pub/chat/rooms/${roomId}/presence`,
                    headers: { 'content-type': 'application/json' },
                    body: JSON.stringify({ status: 'ENTER' }),
                });
            },
            onStompError: (frame) => {
                console.error('STOMP 에러:', frame.headers?.message);
                setConnected(false);
            },
            onWebSocketClose: () => setConnected(false),
        });

        client.activate();
        stompRef.current = client;
    }, [token, markRead]);

    const disconnect = useCallback(() => {
        stompRef.current?.deactivate();
        stompRef.current = null;
        setConnected(false);
    }, []);

    // 방이 바뀌면(다른 대화방 클릭) 이전 방의 메시지/스크롤 상태를 즉시 지운다 — 렌더 중 조정이라 effect가 필요 없다.
    const [prevActiveRoomId, setPrevActiveRoomId] = useState<string | null>(null);
    if (activeRoomId !== prevActiveRoomId) {
        setPrevActiveRoomId(activeRoomId);
        if (activeRoomId) {
            setMessages([]);
            setOldestCursor(null);
            setHistoryLoading(true);
            setManaging(false);
        }
    }

    // 방 전환 시 히스토리 로드 + 실시간 재연결
    useEffect(() => {
        if (!activeRoomId) return;
        let cancelled = false;

        fetch(`/api/v1/chat/rooms/${activeRoomId}/messages?size=30`, { headers: authHeaders() })
            .then((res) => (res.ok ? res.json() : Promise.reject()))
            .then((data) => {
                if (cancelled) return;
                const items: ChatMessage[] = data.data.items;
                setMessages(items);
                setOldestCursor(data.data.hasNext ? data.data.nextCursor : null);
                const last = items[items.length - 1];
                if (last?.messageId) markRead(activeRoomId, last.messageId);
            })
            .catch(() => { if (!cancelled) setMessages([]); })
            .finally(() => { if (!cancelled) setHistoryLoading(false); });

        // 방을 열면 이 방과 관련된 알림(새 메시지 알림 등)도 함께 읽음 처리한다.
        apiClient.post('/api/v1/notifications/read-by-room', { roomId: activeRoomId }).catch(() => {});

        // disconnect()가 setConnected(false)를 동기 호출하지만, 방 전환 시 이전 WebSocket
        // 세션을 즉시 끊고 새로 붙어야 하는 연결 생명주기 관리라 effect 밖으로 옮기기 어렵다.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        disconnect();
        connect(activeRoomId);
        return () => { cancelled = true; disconnect(); };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeRoomId]);

    const loadOlder = async () => {
        if (!activeRoomId || !oldestCursor) return;
        try {
            const res = await fetch(
                `/api/v1/chat/rooms/${activeRoomId}/messages?before=${oldestCursor}&size=30`,
                { headers: authHeaders() },
            );
            if (!res.ok) return;
            const data = await res.json();
            setMessages((prev) => [...data.data.items, ...prev]);
            setOldestCursor(data.data.hasNext ? data.data.nextCursor : null);
        } catch {
            // 실패하면 그냥 버튼을 다시 눌러볼 수 있게 둔다.
        }
    };

    const sendMessage = () => {
        const content = input.trim();
        if (!content || !activeRoomId || !stompRef.current?.connected) return;

        const clientMessageId = crypto.randomUUID();
        stompRef.current.publish({
            destination: `/pub/chat/rooms/${activeRoomId}/messages`,
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ clientMessageId, messageType: 'TEXT', content }),
        });
        setInput('');
    };

    const deleteMessage = async (messageId: string) => {
        if (!activeRoomId || !window.confirm('메시지를 삭제할까요?')) return;
        try {
            await apiClient.delete(`/api/v1/chat/rooms/${activeRoomId}/messages/${messageId}`);
            setMessages((prev) => prev.map((m) => (m.messageId === messageId ? { ...m, deletedAt: new Date().toISOString() } : m)));
        } catch {
            // 실패하면 그대로 둔다 — 다시 시도할 수 있다.
        }
    };

    const handleRoomLeft = () => {
        setManaging(false);
        setRooms((prev) => prev.filter((r) => r.roomId !== activeRoomId));
        setActiveRoomId(null);
    };

    const handleNotificationChanged = (roomId: string, enabled: boolean) => {
        setRooms((prev) => prev.map((r) => (r.roomId === roomId ? { ...r, notificationEnabled: enabled } : r)));
    };

    const activeRoom = rooms.find((r) => r.roomId === activeRoomId);

    return (
        <>
        <div className="chat-layout">
            <div className="conv-list">
                <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--border)' }}>
                    <button
                        onClick={() => setCreating(true)}
                        style={{
                            width: '100%', fontFamily: 'var(--fk)', fontSize: 12.5, fontWeight: 600, color: '#fff',
                            background: 'var(--green)', border: 'none', borderRadius: 10, padding: '9px 0', cursor: 'pointer',
                        }}
                    >+ 새 채팅</button>
                </div>
                {roomsLoading && <div className="conv-empty">불러오는 중...</div>}
                {!roomsLoading && rooms.length === 0 && (
                    <div className="conv-empty">아직 대화방이 없어요.</div>
                )}
                {rooms.map((r) => (
                    <div
                        key={r.roomId}
                        className={`conv-item ${r.roomId === activeRoomId ? 'act' : ''}`}
                        onClick={() => setActiveRoomId(r.roomId)}
                    >
                        <div className="post-av g1" />
                        <div className="conv-info">
                            <div className="conv-name-row">
                                <div className="conv-name">{roomLabel(r)}</div>
                                <div className="conv-time">{formatRoomTime(r.lastMessageAt)}</div>
                            </div>
                            <div className="conv-preview">{r.lastMessage || '아직 메시지가 없어요.'}</div>
                        </div>
                        {r.unreadCount > 0 && <span className="conv-unread">{r.unreadCount}</span>}
                    </div>
                ))}
            </div>

            <div style={{ display: 'flex', minWidth: 0, minHeight: 0 }}>
                <div className="conv-thread" style={{ flex: 1 }}>
                    {!activeRoom ? (
                        <div className="chat-log"><div className="msg bot" style={{ opacity: 0.6 }}>대화방을 선택해주세요.</div></div>
                    ) : (
                        <>
                            <div className="conv-thread-head">
                                <div className="conv-thread-name">{roomLabel(activeRoom)}</div>
                                <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                                    <div className="conv-thread-status">{connected ? '연결됨' : '연결 중...'}</div>
                                    <button
                                        onClick={() => setManaging((v) => !v)}
                                        style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 16, color: 'var(--im)' }}
                                    >⋯</button>
                                </div>
                            </div>
                            <div className="chat-log">
                                {oldestCursor && (
                                    <div className="chat-load-older" onClick={loadOlder}>이전 메시지 더 보기</div>
                                )}
                                {historyLoading && <div className="msg bot" style={{ opacity: 0.6 }}>불러오는 중...</div>}
                                {!historyLoading && messages.length === 0 && (
                                    <div className="msg bot" style={{ opacity: 0.6 }}>메시지가 없어요. 대화를 시작해보세요.</div>
                                )}
                                {messages.map((m, i) => {
                                    if (m.messageType !== 'TEXT' && m.messageType !== 'IMAGE') {
                                        return <div key={m.messageId || i} className="msg-system">{m.content}</div>;
                                    }
                                    const mine = m.senderId === myUserId;
                                    return (
                                        <div
                                            key={m.clientMessageId || m.messageId || i}
                                            className={`msg ${mine ? 'user' : 'bot'}`}
                                            style={{ display: 'flex', alignItems: 'center', gap: 6 }}
                                        >
                                            <span>{m.deletedAt ? '삭제된 메시지예요.' : m.content}</span>
                                            {mine && m.messageId && !m.deletedAt && (
                                                <button
                                                    onClick={() => deleteMessage(m.messageId!)}
                                                    style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 11, color: 'var(--id)', flexShrink: 0 }}
                                                >삭제</button>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                            <div className="chat-input">
                                <input
                                    type="text"
                                    placeholder={connected ? '메시지를 입력하세요...' : '연결 중...'}
                                    value={input}
                                    disabled={!connected}
                                    onChange={(e) => setInput(e.target.value)}
                                    onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
                                />
                                <button className="chat-send" onClick={sendMessage} disabled={!connected}>
                                    <svg viewBox="0 0 24 24"><path d="M4 12l16-8-6 8 6 8z" /></svg>
                                </button>
                            </div>
                        </>
                    )}
                </div>

                {managing && activeRoom && (
                    <RoomManagePanel
                        roomId={activeRoom.roomId}
                        myUserId={myUserId}
                        onClose={() => setManaging(false)}
                        onNotificationChanged={(enabled) => handleNotificationChanged(activeRoom.roomId, enabled)}
                        onLeft={handleRoomLeft}
                        onNavigateToUser={onNavigateToUser}
                    />
                )}
            </div>
        </div>

        {creating && (
            <NewChatModal
                onClose={() => setCreating(false)}
                onCreated={(roomId) => { fetchRooms(); setActiveRoomId(roomId); }}
            />
        )}
        </>
    );
}

export default memo(Chat);
