import { memo, useState, useRef, useEffect } from 'react';
import './ChatbotWidget.css';


interface BotMessage {
  role: 'bot' | 'user';
  content: string;
}

function ChatbotWidget() {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<BotMessage[]>([
    { role: 'bot', content: '안녕하세요! 저는 Ditto 도우미예요. 매칭 관련해서 궁금한 점 있으면 편하게 물어보세요 🙂' },
  ]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const logRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    logRef.current?.scrollTo({ top: logRef.current.scrollHeight, behavior: 'smooth' });
  }, [messages]);

  const send = async () => {
    const text = input.trim();
    if (!text || loading) return;

    setMessages((prev) => [...prev, { role: 'user', content: text }]);
    setInput('');
    setLoading(true);

    try {
      const res = await fetch('/api/v1/assistant/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question: text }),
      });

      if (res.ok) {
        const data = await res.json();
        setMessages((prev) => [...prev, { role: 'bot', content: data.data?.answer || '답변을 가져오지 못했어요.' }]);
      } else {
        throw new Error('assistant_service 응답 실패');
      }
    } catch {
      // 백엔드 연동 전 데모 fallback
      setMessages((prev) => [
        ...prev,
        { role: 'bot', content: '좋은 질문이에요! (데모 화면이라 실제 응답은 assistant_service 연동 후 나와요)' },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="chatbot-fab" onClick={() => setOpen((v) => !v)}>
        <svg viewBox="0 0 24 24"><path d="M4 5h16v11H8l-4 4z" /></svg>
        <span>챗봇</span>
      </div>

      {open && (
        <div className="chatbot-panel">
          <div className="chatbot-panel-head">
            <span>Ditto 챗봇</span>
            <span className="chatbot-close" onClick={() => setOpen(false)}>×</span>
          </div>
          <div className="chat-log" ref={logRef}>
            {messages.map((m, i) => (
              <div key={i} className={`msg ${m.role === 'user' ? 'user' : 'bot'}`}>{m.content}</div>
            ))}
            {loading && <div className="msg bot msg-loading">입력 중...</div>}
          </div>
          <div className="chat-input">
            <input
              type="text"
              placeholder="메시지를 입력하세요..."
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && send()}
            />
            <button className="chat-send" onClick={send} disabled={loading}>
              <svg viewBox="0 0 24 24"><path d="M4 12l16-8-6 8 6 8z" /></svg>
            </button>
          </div>
        </div>
      )}
    </>
  );
}

export default memo(ChatbotWidget);
