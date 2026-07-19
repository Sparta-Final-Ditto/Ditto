import { useCallback, useState, type ReactElement } from 'react';
import { useNavigate } from 'react-router-dom';
import FeedTab from '../components/FeedTab';
import MatchingTab from '../components/MatchingTab';
import Chat from '../components/Chat';
import NotificationsTab from '../components/NotificationsTab';
import ProfileView from '../components/ProfileView';
import SettingsTab from '../components/SettingsTab';
import ChatbotWidget from '../components/ChatbotWidget';
import { useUnreadCount } from '../lib/useUnreadCount';
import './Dashboard.css';

type TabKey = 'feed' | 'matching' | 'chat' | 'notifications' | 'profile' | 'settings';

const NAV_ITEMS: { key: TabKey; label: string }[] = [
  { key: 'feed', label: '피드' },
  { key: 'matching', label: '매칭' },
  { key: 'chat', label: '채팅' },
  { key: 'profile', label: '프로필' },
  { key: 'notifications', label: '알림' },
];

const ICONS: Record<TabKey, ReactElement> = {
  feed: <svg viewBox="0 0 24 24"><path d="M4 4h16v4H4zM4 10h16v10H4z" /></svg>,
  matching: <svg viewBox="0 0 24 24"><circle cx="9" cy="12" r="6" /><circle cx="15" cy="12" r="6" /></svg>,
  chat: <svg viewBox="0 0 24 24"><path d="M4 5h16v11H8l-4 4z" /></svg>,
  notifications: <svg viewBox="0 0 24 24"><path d="M6 9a6 6 0 0 1 12 0v5l2 3H4l2-3z" /><path d="M10 20a2 2 0 0 0 4 0" /></svg>,
  profile: <svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4" /><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" /></svg>,
  settings: <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}><circle cx="12" cy="12" r="3" /><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h0a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51h0a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82v0a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" /></svg>,
};

export default function Dashboard() {
  const [activeTab, setActiveTab] = useState<TabKey>('feed');
  const [viewingUserId, setViewingUserId] = useState<string | null>(null);
  const navigate = useNavigate();
  const { unreadCount, refresh: refreshUnreadCount } = useUnreadCount();

  const userName = localStorage.getItem('userName') || '소윤';
  const myUserId = localStorage.getItem('userId') || '';

  const handleLogout = useCallback(() => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('userId');
    navigate('/login');
  }, [navigate]);

  const goToTab = useCallback((key: TabKey) => {
    setActiveTab(key);
    setViewingUserId(null);
  }, []);

  // 팔로워/팔로잉 목록, 채팅방 참여자 등 어디서든 이 콜백으로 프로필 탭으로 드릴다운한다.
  const handleNavigateToUser = useCallback((userId: string) => {
    setViewingUserId(userId === myUserId ? null : userId);
    setActiveTab('profile');
  }, [myUserId]);

  const handleBackToOwnProfile = useCallback(() => {
    setViewingUserId(null);
  }, []);

  return (
    <div className="app">
      {/* SIDEBAR */}
      <div className="sidebar">
        <div className="side-brand">
          <svg viewBox="0 0 32 32">
            <circle cx="12" cy="16" r="10" fill="#1f6f4a" fillOpacity=".85" />
            <circle cx="20" cy="16" r="10" fill="#35c281" fillOpacity=".85" style={{ mixBlendMode: 'multiply' }} />
          </svg>
          Ditto
        </div>

        <div className="side-nav">
          {NAV_ITEMS.map((item) => {
            const badge = item.key === 'notifications' && unreadCount > 0 ? String(unreadCount) : undefined;
            return (
              <div
                key={item.key}
                className={`nav-item ${activeTab === item.key ? 'act' : ''}`}
                onClick={() => goToTab(item.key)}
              >
                {ICONS[item.key]}
                {item.label}
                {badge && <span className="nav-badge">{badge}</span>}
              </div>
            );
          })}
        </div>

        <div className="side-user">
          <div
            className="side-user-clickable"
            onClick={() => goToTab('profile')}
            title="내 프로필로 이동"
          >
            <div className="side-avatar" />
            <div className="side-user-info">
              <div className="side-user-name">{userName}</div>
              <div className="side-user-sub">테스트 계정</div>
            </div>
          </div>
          <div className="side-logout side-tooltip" data-tooltip="설정" onClick={() => goToTab('settings')}>
            {ICONS.settings}
          </div>
          <div className="side-logout side-tooltip" data-tooltip="로그아웃" onClick={handleLogout}>
            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8}>
              <path d="M9 4H5v16h4M15 8l4 4-4 4M19 12H9" />
            </svg>
          </div>
        </div>
      </div>

      {/* MAIN */}
      <div className="main">
        <div className="view-container">
          {activeTab === 'feed' && <FeedTab />}
          {activeTab === 'matching' && <MatchingTab />}
          {activeTab === 'chat' && <Chat onNavigateToUser={handleNavigateToUser} />}
          {activeTab === 'notifications' && <NotificationsTab onRead={refreshUnreadCount} />}
          {activeTab === 'profile' && (
            <ProfileView
              userId={viewingUserId ?? myUserId}
              currentUserId={myUserId}
              onNavigateToUser={handleNavigateToUser}
              onBack={viewingUserId ? handleBackToOwnProfile : undefined}
            />
          )}
          {activeTab === 'settings' && <SettingsTab />}
        </div>
      </div>

      {/* 챗봇은 탭이 아니라 항상 떠있는 플로팅 위젯 */}
      <ChatbotWidget />
    </div>
  );
}
