import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import './Login.css';

export default function Signup() {
    const navigate = useNavigate();
    const [form, setForm] = useState({
        email: '',
        password: '',
        nickname: '',
        gender: 'MALE',
        birthdate: '',
    });
    const [location, setLocation] = useState<{ latitude: number; longitude: number } | null>(null);
    const [locMode, setLocMode] = useState<'idle' | 'auto' | 'manual'>('idle');
    const [locStatus, setLocStatus] = useState<'idle' | 'loading' | 'ok' | 'denied'>('idle');
    const [addressInput, setAddressInput] = useState('');
    const [addrSearching, setAddrSearching] = useState(false);
    const [addrMsg, setAddrMsg] = useState<{ text: string; ok: boolean } | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    const getLocation = () => {
        if (!navigator.geolocation) { setLocStatus('denied'); return; }
        setLocStatus('loading');
        navigator.geolocation.getCurrentPosition(
            (pos) => {
                setLocation({ latitude: pos.coords.latitude, longitude: pos.coords.longitude });
                setLocStatus('ok');
            },
            () => setLocStatus('denied'),
        );
    };

    // 시/도/군/구/읍/면/동 등 행정구역 단위 뒤에 공백을 넣어, 공백 없이 입력해도
    // ("성남시분당구" 등) 단어 단위로 잘라 검색을 시도할 수 있게 한다.
    const KOREAN_ADMIN_SUFFIX = /(특별자치시|특별자치도|광역시|특별시|자치시|자치도|시|도|군|구|읍|면|동)(?=\S)/g;
    const splitAddressQuery = (q: string) =>
        (/\s/.test(q) ? q : q.replace(KOREAN_ADMIN_SUFFIX, '$1 ')).trim().split(/\s+/);

    const nominatim = async (q: string) => {
        const res = await fetch(
            `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(q)}&format=json&limit=1&countrycodes=kr`,
            { headers: { 'Accept-Language': 'ko' } }
        );
        return res.json() as Promise<{ lat: string; lon: string; display_name: string }[]>;
    };

    const searchAddress = async () => {
        const query = addressInput.trim();
        if (!query) return;
        setAddrSearching(true);
        setAddrMsg(null);
        setLocation(null);
        setLocStatus('idle');
        try {
            const parts = splitAddressQuery(query);
            let data: { lat: string; lon: string; display_name: string }[] = [];

            // 전체 쿼리 → 앞 두 단어 → 첫 단어 순으로 폴백
            for (let i = parts.length; i >= 1; i--) {
                data = await nominatim(parts.slice(0, i).join(' '));
                if (data.length > 0) break;
            }

            if (data.length === 0) {
                setAddrMsg({ text: '검색 결과가 없어요. 시·구 단위로 입력해보세요. (예: 분당구, 강남구)', ok: false });
                return;
            }
            setLocation({ latitude: parseFloat(data[0].lat), longitude: parseFloat(data[0].lon) });
            setLocStatus('ok');
            const label = data[0].display_name.split(',').slice(0, 2).join(',').trim();
            setAddrMsg({ text: `✓ ${label}`, ok: true });
        } catch {
            setAddrMsg({ text: '주소 검색에 실패했어요. 잠시 후 다시 시도해주세요.', ok: false });
        } finally {
            setAddrSearching(false);
        }
    };

    const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
        setForm((prev) => ({ ...prev, [k]: e.target.value }));

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setLoading(true);
        if (!location) {
            setError('위치를 설정해주세요. 자동 감지 또는 주소 검색을 이용해보세요.');
            setLoading(false);
            return;
        }

        try {
            const res = await fetch('/api/v1/auth/signup', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    email: form.email,
                    password: form.password,
                    nickname: form.nickname,
                    gender: form.gender,
                    birthdate: form.birthdate,
                    latitude: location.latitude,
                    longitude: location.longitude,
                }),
            });

            const data = await res.json().catch(() => null);
            if (!res.ok) {
                throw new Error(data?.message || `서버 오류 (${res.status})`);
            }

            navigate('/login');
        } catch (err: unknown) {
            if (err instanceof TypeError && err.message.includes('fetch')) {
                setError('백엔드 서버에 연결할 수 없어요. localhost:8080이 실행 중인지 확인해주세요.');
            } else {
                setError(err instanceof Error ? err.message : '회원가입에 실패했어요.');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="wrap">
            {/* LEFT BRAND */}
            <div className="brand-side">
                <div className="brand-mark">
                    <svg viewBox="0 0 32 32">
                        <circle cx="12" cy="16" r="10" fill="#fff" fillOpacity=".95" />
                        <circle cx="20" cy="16" r="10" fill="#0d3d29" fillOpacity=".55" />
                    </svg>
                    Ditto
                </div>
                <div className="brand-copy">
                    <h2>
                        같은 파장을 가진<br /><em>사람과의 연결</em>,<br />지금 시작해보세요
                    </h2>
                    <p>취향과 관심사의 결을 읽어 매일 한 명, 진짜 통할 사람을 연결합니다.</p>
                </div>
                <div className="brand-foot">© 2026 Ditto</div>
            </div>

            {/* RIGHT FORM */}
            <div className="form-side">
                <div className="form-box">
                    <div className="form-head">
                        <h1>계정 만들기</h1>
                        <p>몇 가지 정보를 입력하고 Ditto를 시작하세요.</p>
                    </div>

                    {error && <div className="err show">{error}</div>}

                    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                        <div className="field">
                            <label htmlFor="email">이메일</label>
                            <input id="email" type="email" placeholder="you@example.com" required value={form.email} onChange={set('email')} />
                        </div>
                        <div className="field">
                            <label htmlFor="password">비밀번호</label>
                            <input id="password" type="password" placeholder="8자 이상 입력" required minLength={8} value={form.password} onChange={set('password')} />
                        </div>
                        <div className="field">
                            <label htmlFor="nickname">닉네임</label>
                            <input id="nickname" type="text" placeholder="사용할 닉네임" required value={form.nickname} onChange={set('nickname')} />
                        </div>
                        <div className="field">
                            <label htmlFor="gender">성별</label>
                            <select id="gender" value={form.gender} onChange={set('gender')} style={{
                                fontFamily: 'var(--fk)', fontSize: 14, padding: '12px 14px',
                                border: '1px solid var(--border2)', borderRadius: 10, background: '#fff',
                                outline: 'none', color: 'var(--ink)', cursor: 'pointer',
                            }}>
                                <option value="MALE">남성</option>
                                <option value="FEMALE">여성</option>
                            </select>
                        </div>
                        <div className="field">
                            <label htmlFor="birthdate">생년월일</label>
                            <input id="birthdate" type="date" required value={form.birthdate} onChange={set('birthdate')} />
                        </div>
                        <div className="field">
                            <label>위치 <span style={{ color: '#b3402b', fontSize: 11 }}>필수</span></label>
                            <div style={{ display: 'flex', gap: 8 }}>
                                <button type="button" onClick={() => { setLocMode('auto'); setLocStatus('idle'); setLocation(null); getLocation(); }}
                                    style={{
                                        flex: 1, fontFamily: 'var(--fk)', fontSize: 13, padding: '10px 12px',
                                        border: `1px solid ${locMode === 'auto' ? 'var(--green)' : 'var(--border2)'}`,
                                        borderRadius: 10, background: locMode === 'auto' ? 'var(--green-10)' : '#fff',
                                        color: locMode === 'auto' ? 'var(--green)' : 'var(--ink)', cursor: 'pointer',
                                    }}>
                                    📍 자동
                                </button>
                                <button type="button" onClick={() => { setLocMode('manual'); setLocation(null); setLocStatus('idle'); }}
                                    style={{
                                        flex: 1, fontFamily: 'var(--fk)', fontSize: 13, padding: '10px 12px',
                                        border: `1px solid ${locMode === 'manual' ? 'var(--green)' : 'var(--border2)'}`,
                                        borderRadius: 10, background: locMode === 'manual' ? 'var(--green-10)' : '#fff',
                                        color: locMode === 'manual' ? 'var(--green)' : 'var(--ink)', cursor: 'pointer',
                                    }}>
                                    ✏️ 직접 입력
                                </button>
                            </div>

                            {locMode === 'auto' && (
                                <div style={{ fontSize: 12.5, color: locStatus === 'ok' ? 'var(--green)' : 'var(--im)', marginTop: 6 }}>
                                    {locStatus === 'loading' && '위치 수신 중...'}
                                    {locStatus === 'ok' && `✓ 위치 획득됨 (${location!.latitude.toFixed(4)}, ${location!.longitude.toFixed(4)})`}
                                    {locStatus === 'denied' && '위치 권한이 거부됐어요 — 위치 없이 진행됩니다'}
                                </div>
                            )}

                            {locMode === 'manual' && (
                                <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
                                    <input
                                        type="text"
                                        placeholder="예: 분당구, 강남구, 해운대구"
                                        value={addressInput}
                                        onChange={(e) => setAddressInput(e.target.value)}
                                        onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), searchAddress())}
                                        style={{ flex: 1, fontFamily: 'var(--fk)', fontSize: 13.5, padding: '11px 14px', border: '1px solid var(--border2)', borderRadius: 10, outline: 'none', color: 'var(--ink)' }}
                                    />
                                    <button type="button" onClick={searchAddress} disabled={addrSearching}
                                        style={{ fontFamily: 'var(--fk)', fontSize: 13, padding: '11px 16px', border: 'none', borderRadius: 10, background: 'var(--ink)', color: '#fff', cursor: 'pointer', whiteSpace: 'nowrap' }}>
                                        {addrSearching ? '...' : '검색'}
                                    </button>
                                </div>
                            )}
                            {locMode === 'manual' && addrMsg && (
                                <div style={{ fontSize: 12.5, color: addrMsg.ok ? 'var(--green)' : '#b3402b', marginTop: 6 }}>
                                    {addrMsg.text}
                                </div>
                            )}
                        </div>
                        <button className="submit" type="submit" disabled={loading}>
                            {loading ? '가입 중...' : '회원가입'}
                        </button>
                    </form>

                    <div className="divider">또는</div>
                    <div className="signup-hint">
                        이미 계정이 있으신가요? <Link to="/login">로그인</Link>
                    </div>
                </div>
            </div>
        </div>
    );
}
