import { useState } from 'react';
import { apiClient, ApiError } from '../lib/apiClient';
import type { ReportType } from '../types/user';
import './CreatePostModal.css';

interface Props {
  targetUserId: string;
  onClose: () => void;
  onSubmitted: () => void;
}

const REPORT_LABELS: Record<ReportType, string> = {
  SPAM: '스팸/광고',
  HATE_SPEECH: '혐오 발언',
  SEXUAL_CONTENT: '성적인 콘텐츠',
  VIOLENCE: '폭력적인 콘텐츠',
  MISINFORMATION: '허위 정보',
  OTHER: '기타',
};

export default function ReportModal({ targetUserId, onClose, onSubmitted }: Props) {
  const [reportType, setReportType] = useState<ReportType>('SPAM');
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await apiClient.post(`/api/v1/users/${targetUserId}/report`, { reportType, content: content || null });
      onSubmitted();
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '신고를 접수하지 못했어요.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="profile-edit-overlay" onClick={onClose}>
      <div className="create-post-card" onClick={(e) => e.stopPropagation()}>
        <h2>사용자 신고하기</h2>
        <p>신고 내용은 운영팀이 검토 후 처리해요.</p>

        {error && <div className="create-post-err">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="report-type">신고 유형</label>
            <select id="report-type" value={reportType} onChange={(e) => setReportType(e.target.value as ReportType)}>
              {Object.entries(REPORT_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="report-content">상세 내용 (선택)</label>
            <textarea id="report-content" rows={4} maxLength={500} value={content} onChange={(e) => setContent(e.target.value)} />
          </div>
          <div className="create-post-actions">
            <button type="button" className="create-post-cancel" onClick={onClose} disabled={submitting}>취소</button>
            <button type="submit" className="create-post-submit" disabled={submitting}>
              {submitting ? '신고하는 중...' : '신고하기'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
