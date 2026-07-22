import { useEffect, useState } from 'react';
import { apiClient, ApiError } from '../lib/apiClient';
import type { PostVisibility, UploadUrlResponse, PostMediaFile } from '../types/post';
import './CreatePostModal.css';

interface Props {
  onClose: () => void;
  onCreated: () => void;
}

const MAX_FILES = 5;

export default function CreatePostModal({ onClose, onCreated }: Props) {
  const [content, setContent] = useState('');
  const [tagsInput, setTagsInput] = useState('');
  const [visibility, setVisibility] = useState<PostVisibility>('PUBLIC');
  const [showLocation, setShowLocation] = useState(true);
  const [files, setFiles] = useState<File[]>([]);
  const [location, setLocation] = useState<{ latitude: number; longitude: number } | null>(null);
  // 초기 상태를 위치 API 지원 여부로 미리 계산해두면 effect 안에서 동기적으로 setState할 필요가 없다.
  const [locStatus, setLocStatus] = useState<'idle' | 'loading' | 'ok' | 'denied'>(
    () => (navigator.geolocation ? 'loading' : 'denied'),
  );
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocation({ latitude: pos.coords.latitude, longitude: pos.coords.longitude });
        setLocStatus('ok');
      },
      () => setLocStatus('denied'),
    );
  }, []);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const selected = Array.from(e.target.files || []).slice(0, MAX_FILES);
    setFiles(selected);
  };

  const parseTags = () =>
    Array.from(new Set(
      tagsInput.split(/[,\s]+/).map((t) => t.trim()).filter(Boolean),
    ));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    const tags = parseTags();
    if (tags.length === 0 || tags.length > 10) {
      setError('태그는 1개 이상, 10개 이하로 입력해주세요.');
      return;
    }
    if (!location) {
      setError('위치 정보가 필요해요. 브라우저 위치 권한을 허용해주세요.');
      return;
    }

    setSubmitting(true);
    try {
      let mediaFiles: PostMediaFile[] = [];
      if (files.length > 0) {
        const uploadRes = await apiClient.post<UploadUrlResponse>('/api/v1/feeds/upload-url', {
          files: files.map((f) => ({ fileName: f.name, fileType: f.type, fileSize: f.size })),
        });
        await Promise.all(
          uploadRes.files.map((u, i) =>
            fetch(u.presignedUrl, { method: 'PUT', headers: { 'Content-Type': files[i].type }, body: files[i] }),
          ),
        );
        mediaFiles = uploadRes.files.map((u, i) => ({
          s3Key: u.s3Key,
          mediaType: files[i].type.startsWith('video') ? 'VIDEO' : 'IMAGE',
          sortOrder: i,
        }));
      }

      await apiClient.post('/api/v1/posts', {
        content,
        tags,
        latitude: location.latitude,
        longitude: location.longitude,
        visibility,
        showLocation,
        mediaFiles,
      });

      onCreated();
      onClose();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '게시물을 올리지 못했어요.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="profile-edit-overlay" onClick={onClose}>
      <div className="create-post-card" onClick={(e) => e.stopPropagation()}>
        <h2>오늘의 결 남기기</h2>
        <p>느낀 점, 사진이나 영상을 자유롭게 남겨보세요.</p>

        {error && <div className="create-post-err">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="field">
            <label htmlFor="post-content">내용</label>
            <textarea
              id="post-content" rows={4} maxLength={500}
              placeholder="무슨 결을 느꼈나요?"
              value={content} onChange={(e) => setContent(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="post-tags">태그 (쉼표로 구분, 1~10개)</label>
            <input
              id="post-tags" type="text" placeholder="예: 여행, 카페, 러닝"
              value={tagsInput} onChange={(e) => setTagsInput(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="post-media">사진/영상 (선택, 최대 {MAX_FILES}개)</label>
            <input id="post-media" type="file" accept="image/*,video/*" multiple onChange={handleFileChange} />
          </div>
          <div className="field">
            <label htmlFor="post-visibility">공개 범위</label>
            <select id="post-visibility" value={visibility} onChange={(e) => setVisibility(e.target.value as PostVisibility)}>
              <option value="PUBLIC">전체 공개</option>
              <option value="FOLLOWERS_ONLY">팔로워만</option>
              <option value="PRIVATE">비공개</option>
            </select>
          </div>
          <div className="field">
            <label className="checkbox-row">
              <input type="checkbox" checked={showLocation} onChange={(e) => setShowLocation(e.target.checked)} />
              지금 이 순간의 위치 표시하기
            </label>
            <div className={`create-post-loc-status ${locStatus}`}>
              {locStatus === 'loading' && '위치 확인 중...'}
              {locStatus === 'ok' && '✓ 위치를 확인했어요.'}
              {locStatus === 'denied' && '위치 권한이 필요해요. 브라우저 설정에서 허용해주세요.'}
            </div>
          </div>
          <div className="create-post-actions">
            <button type="button" className="create-post-cancel" onClick={onClose} disabled={submitting}>취소</button>
            <button type="submit" className="create-post-submit" disabled={submitting}>
              {submitting ? '올리는 중...' : '게시하기'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
