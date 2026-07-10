import { useState, useEffect, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import * as THREE from 'three';
import './Login.css';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export default function Login() {
    const navigate = useNavigate();
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    // ── 좌측 브랜드 패널 파티클 (랜딩과 동일 톤, 축소 버전) ──
    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const parent = canvas.parentElement!;
        const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
        renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
        const fit = () => renderer.setSize(parent.clientWidth, parent.clientHeight);
        fit();

        const scene = new THREE.Scene();
        const camera = new THREE.PerspectiveCamera(55, parent.clientWidth / parent.clientHeight, 0.1, 1000);
        camera.position.z = 8;

        const C = 1800;
        const pGeo = new THREE.BufferGeometry();
        const pPos = new Float32Array(C * 3);
        for (let i = 0; i < C; i++) {
            const t = Math.random() * Math.PI * 2;
            const p = Math.acos(2 * Math.random() - 1);
            const r = 1 + Math.random() * 0.4;
            pPos[i * 3] = r * Math.sin(p) * Math.cos(t);
            pPos[i * 3 + 1] = r * Math.sin(p) * Math.sin(t);
            pPos[i * 3 + 2] = r * Math.cos(p);
        }
        pGeo.setAttribute('position', new THREE.BufferAttribute(pPos, 3));
        const mat = new THREE.PointsMaterial({
            color: 0x35c281, size: 0.045, transparent: true, opacity: 0.55, sizeAttenuation: true,
        });
        const pts = new THREE.Points(pGeo, mat);
        scene.add(pts);

        const clock = new THREE.Clock();
        let raf = 0;
        const loop = () => {
            raf = requestAnimationFrame(loop);
            const t = clock.getElapsedTime();
            pts.rotation.y = t * 0.06;
            pts.rotation.x = t * 0.03;
            renderer.render(scene, camera);
        };
        loop();

        const onResize = () => {
            fit();
            camera.aspect = parent.clientWidth / parent.clientHeight;
            camera.updateProjectionMatrix();
        };
        window.addEventListener('resize', onResize);

        return () => {
            cancelAnimationFrame(raf);
            window.removeEventListener('resize', onResize);
            pGeo.dispose();
            mat.dispose();
            renderer.dispose();
        };
    }, []);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError(null);
        setLoading(true);
        try {
            const res = await fetch(`${API_BASE}/api/v1/auth/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password }),
            });

            if (!res.ok) {
                throw new Error('이메일 또는 비밀번호가 올바르지 않아요.');
            }

            const data = await res.json();
            // ApiResponse<AuthTokenResponse> 형태 가정: { data: { accessToken, refreshToken, userId } }
            const { accessToken, refreshToken, userId } = data.data;

            localStorage.setItem('accessToken', accessToken);
            if (refreshToken) localStorage.setItem('refreshToken', refreshToken);
            if (userId) localStorage.setItem('userId', userId);

            navigate('/app');
        } catch (err) {
            setError(err instanceof Error ? err.message : '로그인에 실패했어요.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="wrap">
            {/* LEFT BRAND */}
            <div className="brand-side">
                <canvas ref={canvasRef} id="brand-canvas" />
                <div className="brand-mark" style={{ position: 'relative', zIndex: 1 }}>
                    <svg viewBox="0 0 32 32">
                        <circle cx="12" cy="16" r="10" fill="#fff" fillOpacity=".95" />
                        <circle cx="20" cy="16" r="10" fill="#0d3d29" fillOpacity=".55" />
                    </svg>
                    Ditto
                </div>
                <div className="brand-copy" style={{ position: 'relative', zIndex: 1 }}>
                    <h2>
                        당신과 <em>같은 파장</em>을 가진<br />사람을 다시 만나보세요
                    </h2>
                    <p>취향과 관심사의 결을 읽어 매일 한 명, 진짜 통할 사람을 연결합니다.</p>
                </div>
                <div className="brand-foot" style={{ position: 'relative', zIndex: 1 }}>© 2026 Ditto</div>
            </div>

            {/* RIGHT FORM */}
            <div className="form-side">
                <div className="form-box">
                    <div className="form-head">
                        <h1>다시 만나서 반가워요</h1>
                        <p>계정 정보를 입력하고 오늘의 매칭을 확인하세요.</p>
                    </div>

                    {error && <div className="err show">{error}</div>}

                    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                        <div className="field">
                            <label htmlFor="email">이메일</label>
                            <input
                                id="email" type="email" placeholder="you@example.com" required
                                value={email} onChange={(e) => setEmail(e.target.value)}
                            />
                        </div>
                        <div className="field">
                            <label htmlFor="password">비밀번호</label>
                            <input
                                id="password" type="password" placeholder="비밀번호 입력" required
                                value={password} onChange={(e) => setPassword(e.target.value)}
                            />
                        </div>
                        <div className="form-opts">
                            <label className="chk"><input type="checkbox" />로그인 상태 유지</label>
                            <a href="#">비밀번호 찾기</a>
                        </div>
                        <button className="submit" type="submit" disabled={loading}>
                            {loading ? '로그인 중...' : '로그인'}
                        </button>
                    </form>

                    <div className="divider">또는</div>
                    <div className="signup-hint">
                        계정이 없으신가요? <Link to="/signup">회원가입</Link>
                    </div>
                </div>
            </div>
        </div>
    );
}