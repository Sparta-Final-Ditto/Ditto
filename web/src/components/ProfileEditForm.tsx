import { useRef, useState } from 'react';
import { decodeJwtPayload } from '../lib/jwt';
import { apiClient, ApiError } from '../lib/apiClient';
import type { UploadUrlResponse } from '../types/post';

interface EditableProfile {
  nickname: string;
  bio: string | null;
  profileImageUrl: string | null;
}

interface Props {
  profile: EditableProfile;
  onClose: () => void;
  onSaved: (updated: EditableProfile) => void;
}

export default function ProfileEditForm({ profile, onClose, onSaved }: Props) {
  const [nickname, setNickname] = useState(profile.nickname);
  const [bio, setBio] = useState(profile.bio ?? '');
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState(profile.profileImageUrl ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setImageFile(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      let profileImageUrl = profile.profileImageUrl;
      if (imageFile) {
        const uploadRes = await apiClient.post<UploadUrlResponse>('/api/v1/feeds/upload-url', {
          files: [{ fileName: imageFile.name, fileType: imageFile.type, fileSize: imageFile.size }],
        });
        const target = uploadRes.files[0];
        await fetch(target.presignedUrl, { method: 'PUT', headers: { 'Content-Type': imageFile.type }, body: imageFile });
        profileImageUrl = target.presignedUrl.split('?')[0];
      }

      const data = await apiClient.patch<{ tokens?: { accessToken?: string; refreshToken?: string } }>(
        '/api/v1/users/me',
        { nickname, bio: bio || null, profileImageUrl },
      );

      // 닉네임이 바뀌면 JWT 클레임도 갱신되어 새 토큰이 내려온다 — 로컬 상태 동기화
      if (data.tokens?.accessToken) {
        localStorage.setItem('accessToken', data.tokens.accessToken);
        if (data.tokens.refreshToken) localStorage.setItem('refreshToken', data.tokens.refreshToken);
        const payload = decodeJwtPayload(data.tokens.accessToken);
        if (payload.nickname) localStorage.setItem('userName', String(payload.nickname));
      } else {
        localStorage.setItem('userName', nickname);
      }

      onSaved({ nickname, bio: bio || null, profileImageUrl });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '결을 다듬는 데 실패했어요.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="profile-edit-overlay" onClick={onClose}>
      <div className="profile-edit-card" onClick={(e) => e.stopPropagation()}>
        <div className="profile-edit-head">
          <h2>내 결 편집하기</h2>
          <p>다른 사람에게 보여질 나의 결을 다듬어보세요.</p>
        </div>
        {error && <div className="profile-edit-err">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="field" style={{ alignItems: 'center' }}>
            <input ref={fileInputRef} type="file" accept="image/*" onChange={handleFileChange} style={{ display: 'none' }} />
            <div
              className="profile-edit-avatar"
              onClick={() => fileInputRef.current?.click()}
              style={previewUrl ? { backgroundImage: `url(${previewUrl})` } : undefined}
            >
              <span>사진 변경</span>
            </div>
          </div>
          <div className="field">
            <label htmlFor="edit-nickname">닉네임</label>
            <input id="edit-nickname" type="text" required maxLength={50}
              value={nickname} onChange={(e) => setNickname(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="edit-bio">소개 (내 결 한 줄)</label>
            <textarea id="edit-bio" maxLength={200} rows={3}
              placeholder="어떤 결을 가진 사람인지 알려주세요."
              value={bio} onChange={(e) => setBio(e.target.value)} />
          </div>
          <div className="profile-edit-actions">
            <button type="button" className="profile-edit-cancel" onClick={onClose}>취소</button>
            <button type="submit" className="profile-edit-save" disabled={saving}>
              {saving ? '저장 중...' : '저장하기'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
