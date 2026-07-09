import { useState, useEffect, useRef, useCallback } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import '../components/Chat.css';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws-chat';

interface ChatMessage {
    clientMessageId?: string;
    messageId?: string;
    senderId: string;
    content: string;
    messageType: 'TEXT' | 'SYSTEM_ENTER' | 'SYSTEM_LEAVE' | string;
    createdAt?: string;
}

interface Conversation {
    roomId: string;
    name: string;
    avatarClass: string;
}

// 데모용 대화 목록 — 실제로는 GET /api/v1/chat/rooms 같은 API로 대체
const CONVERSATIONS: Conversation[] = [
    { roomId: 'room-1', name: '선진', avatarClass: 'g1' },
    { roomId: 'room-2', name: '다은', avatarClass: 'g2' },
];

export default function Chat() {
    const [activeRoom, setActiveRoom] = useState<Conversation>(CONVERSATIONS[0]);
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [input, setInput] = useState('');
    const [connected, setConnected] = useState(false);
    const stompRef = useRef<Client | null>(null);
    const myUserId = localStorage.getItem('userId') || '';
    const token = localStorage.getItem('accessToken') || '';

    const connect = useCallback((roomId: string) => {
        if (!token) return;

        const brokerURL = `${WS_URL}?token=${encodeURIComponent(token)}`;
        const client = new Client({
            brokerURL,
            reconnectDelay: 4000,
            onConnect: () => {
                setConnected(true);
                // 방 메시지 구독
                client.subscribe(`/sub/chat/rooms/${roomId}`, (message: IMessage) => {
                    const data: ChatMessage = JSON.parse(message.body);
                    setMessages((prev) => [...prev, data]);
                });
                // 에러 채널 구독
                client.subscribe('/user/sub/chat/errors', (message: IMessage) => {
                    const data = JSON.parse(message.body);
                    console.error('채팅 에러:', data.message || data.errorType);
                });
                // 입장 presence 전송
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
    }, [token]);

    const disconnect = useCallback(() => {
        stompRef.current?.deactivate();
        stompRef.current = null;
        setConnected(false);
    }, []);

    // 방 전환 시 재연결
    useEffect(() => {
        setMessages([]);
        disconnect();
        connect(activeRoom.roomId);
        return () => disconnect();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [activeRoom.roomId]);

    const sendMessage = () => {
        const content = input.trim();
        if (!content || !stompRef.current?.connected) return;

        const clientMessageId = crypto.randomUUID();
        stompRef.current.publish({
            destination: `/pub/chat/rooms/${activeRoom.roomId}/messages`,
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ clientMessageId, messageType: 'TEXT', content }),
        });
        setInput('');
    };

    return (
        <div className="chat-layout">
            <div className="conv-list">
                {CONVERSATIONS.map((c) => (
                    <div
                        key={c.roomId}
                        className={`conv-item ${c.roomId === activeRoom.roomId ? 'act' : ''}`}
                        onClick={() => setActiveRoom(c)}
                    >
                        <div className={`post-av ${c.avatarClass}`} />
                        <div className="conv-info">
                            <div className="conv-name">{c.name}</div>
                            <div className="conv-preview">{connected && c.roomId === activeRoom.roomId ? '연결됨' : '탭하여 대화 열기'}</div>
                        </div>
                    </div>
                ))}
            </div>

            <div className="conv-thread">
                <div className="chat-log">
                    {messages.length === 0 && (
                        <div className="msg bot" style={{ opacity: 0.6 }}>메시지가 없어요. 대화를 시작해보세요.</div>
                    )}
                    {messages.map((m, i) => {
                        if (m.messageType !== 'TEXT') {
                            return <div key={i} className="msg-system">{m.content}</div>;
                        }
                        const mine = m.senderId === myUserId;
                        return (
                            <div key={m.clientMessageId || i} className={`msg ${mine ? 'user' : 'bot'}`}>
                                {m.content}
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
            </div>
        </div>
    );
}