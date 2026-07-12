import { useEffect, useRef, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import * as THREE from 'three';
import './Landing.css';

const PANELS = 4;

export default function Landing() {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const trackRef = useRef<HTMLDivElement>(null);
    const [currentIdx, setCurrentIdx] = useState(0);
    const targetTX = useRef(0);
    const currentTX = useRef(0);
    const revealed = useRef<Set<number>>(new Set([0]));
    const rafRef = useRef(0);

    // ── Three.js 파티클 (Panel 0) ──
    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;

        const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
        renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
        renderer.setSize(window.innerWidth, window.innerHeight);
        renderer.setClearColor(0x000000, 0);

        const scene = new THREE.Scene();
        const camera = new THREE.PerspectiveCamera(55, window.innerWidth / window.innerHeight, 0.1, 1000);
        camera.position.z = 8;

        const C = 4200;
        const pGeo = new THREE.BufferGeometry();
        const pPos = new Float32Array(C * 3);
        const pSz = new Float32Array(C);
        const pPhase = new Float32Array(C);
        for (let i = 0; i < C; i++) {
            const t = Math.random() * Math.PI * 2;
            const p = Math.acos(2 * Math.random() - 1);
            const r = 1.0 + Math.random() * 0.35;
            pPos[i * 3] = r * Math.sin(p) * Math.cos(t);
            pPos[i * 3 + 1] = r * Math.sin(p) * Math.sin(t);
            pPos[i * 3 + 2] = r * Math.cos(p);
            pSz[i] = Math.random() * 5 + 2;
            pPhase[i] = Math.random() * Math.PI * 2;
        }
        pGeo.setAttribute('position', new THREE.BufferAttribute(pPos, 3));
        pGeo.setAttribute('size', new THREE.BufferAttribute(pSz, 1));
        pGeo.setAttribute('aPhase', new THREE.BufferAttribute(pPhase, 1));

        const mat = new THREE.ShaderMaterial({
            uniforms: { uTime: { value: 0 }, uMouse: { value: new THREE.Vector2() } },
            vertexShader: `
                attribute float size; attribute float aPhase; varying vec3 vColor;
                uniform float uTime; uniform vec2 uMouse;
                float hash(vec3 p){p=fract(p*vec3(443.897,397.297,491.187));p+=dot(p.zxy,p.yxz+19.19);return fract(p.x*p.y*p.z);}
                float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}
                float fbm(vec3 p){float v=0.;float a=.5;for(int i=0;i<4;i++){v+=a*noise(p);p*=2.;a*=.5;}return v;}
                void main(){
                    vec3 n=normalize(position);
                    float disp=fbm(n*2.2+uTime*.18)*.35+fbm(n*4.+uTime*.12)*.15;
                    disp+=sin(uTime*.6+aPhase)*.06;
                    vec3 p=position+n*disp;
                    vec2 m=uMouse; float d=length(p.xy-m*3.5);
                    if(d<4.0){float f=smoothstep(4.0,0.,d);p.xy+=normalize(p.xy-m*3.5)*f*.7;p.z+=f*.25;}
                    float t=clamp(disp*1.8,0.,1.);
                    vec3 c1=vec3(.12,.44,.29); vec3 c2=vec3(.21,.76,.51); vec3 c3=vec3(.07,.08,.06);
                    vColor=t<.6?mix(c1,c2,t/.6):mix(c2,c3,(t-.6)/.4);
                    vec4 mv=modelViewMatrix*vec4(p,1.0);
                    gl_PointSize=size*(220.0/-mv.z);
                    gl_Position=projectionMatrix*mv;
                }`,
            fragmentShader: `
                varying vec3 vColor;
                void main(){
                    vec2 uv=gl_PointCoord-.5; float d=length(uv); if(d>.5)discard;
                    float a=exp(-d*d*12.)*1.15; a=clamp(a,0.,1.);
                    gl_FragColor=vec4(vColor,a*.8);
                }`,
            transparent: true,
            depthWrite: false,
        });
        const pts = new THREE.Points(pGeo, mat);
        scene.add(pts);

        const ring = new THREE.Mesh(
            new THREE.TorusGeometry(1.65, 0.0025, 2, 180),
            new THREE.MeshBasicMaterial({ color: 0x1f6f4a, transparent: true, opacity: 0.35 })
        );
        ring.rotation.x = Math.PI * 0.5;
        scene.add(ring);

        const ring2 = new THREE.Mesh(
            new THREE.TorusGeometry(2.1, 0.0015, 2, 180),
            new THREE.MeshBasicMaterial({ color: 0x12140f, transparent: true, opacity: 0.12 })
        );
        ring2.rotation.x = Math.PI * 0.3;
        ring2.rotation.y = Math.PI * 0.2;
        scene.add(ring2);

        const smoothMouse = new THREE.Vector2();
        const targetMouse = new THREE.Vector2();
        const tr = new THREE.Vector2();
        const cr = new THREE.Vector2();

        const onMouseMove = (e: MouseEvent) => {
            const mx = (e.clientX / window.innerWidth) * 2 - 1;
            const my = -((e.clientY / window.innerHeight) * 2 - 1);
            targetMouse.set(mx, my);
            tr.x = my * 0.55;
            tr.y = mx * 0.55;
        };
        document.addEventListener('mousemove', onMouseMove);

        const clock = new THREE.Clock();
        let threeRaf = 0;
        const loop = () => {
            threeRaf = requestAnimationFrame(loop);
            const t = clock.getElapsedTime();
            mat.uniforms.uTime.value = t;
            smoothMouse.x += (targetMouse.x - smoothMouse.x) * 0.04;
            smoothMouse.y += (targetMouse.y - smoothMouse.y) * 0.04;
            mat.uniforms.uMouse.value.copy(smoothMouse);
            cr.x += (tr.x - cr.x) * 0.012;
            cr.y += (tr.y - cr.y) * 0.012;
            pts.rotation.x = cr.x + t * 0.05;
            pts.rotation.y = cr.y + t * 0.07;
            ring.rotation.z = t * 0.07;
            ring2.rotation.z = -t * 0.04;
            ring2.rotation.x = Math.PI * 0.3 + Math.sin(t * 0.3) * 0.08;
            renderer.render(scene, camera);
        };
        loop();

        const onResize = () => {
            renderer.setSize(window.innerWidth, window.innerHeight);
            camera.aspect = window.innerWidth / window.innerHeight;
            camera.updateProjectionMatrix();
        };
        window.addEventListener('resize', onResize);

        return () => {
            cancelAnimationFrame(threeRaf);
            document.removeEventListener('mousemove', onMouseMove);
            window.removeEventListener('resize', onResize);
            pGeo.dispose();
            mat.dispose();
            renderer.dispose();
        };
    }, []);

    // ── 수평 스크롤 엔진 ──
    const triggerReveal = useCallback((idx: number) => {
        if (idx < 0 || idx >= PANELS) return;
        if (revealed.current.has(idx)) return;
        revealed.current.add(idx);
        const panel = document.querySelectorAll('.panel')[idx];
        if (!panel) return;
        panel.querySelectorAll('.rv').forEach((el, i) => {
            setTimeout(() => el.classList.add('on'), i * 90);
        });
    }, []);

    const goTo = useCallback((idx: number) => {
        idx = Math.max(0, Math.min(PANELS - 1, idx));
        targetTX.current = idx * window.innerWidth;
    }, []);

    useEffect(() => {
        const maxTX = () => (PANELS - 1) * window.innerWidth;

        const tick = () => {
            currentTX.current += (targetTX.current - currentTX.current) * 0.08;
            if (Math.abs(currentTX.current - targetTX.current) < 0.5) {
                currentTX.current = targetTX.current;
            }
            if (trackRef.current) {
                trackRef.current.style.transform = `translateX(-${currentTX.current}px)`;
            }

            const idx = Math.round(currentTX.current / window.innerWidth);
            setCurrentIdx(idx);
            triggerReveal(idx);
            if (idx < PANELS - 1) triggerReveal(idx + 1);

            rafRef.current = requestAnimationFrame(tick);
        };
        rafRef.current = requestAnimationFrame(tick);

        const onWheel = (e: WheelEvent) => {
            e.preventDefault();
            targetTX.current = Math.max(0, Math.min(maxTX(), targetTX.current + (e.deltaY + e.deltaX) * 1.2));
        };

        let tx0: number | null = null;
        const onTouchStart = (e: TouchEvent) => { tx0 = e.touches[0].clientX; };
        const onTouchMove = (e: TouchEvent) => {
            if (tx0 === null) return;
            const dx = tx0 - e.touches[0].clientX;
            targetTX.current = Math.max(0, Math.min(maxTX(), targetTX.current + dx * 1.5));
            tx0 = e.touches[0].clientX;
        };

        const onKeyDown = (e: KeyboardEvent) => {
            const cur = Math.round(currentTX.current / window.innerWidth);
            if (e.key === 'ArrowRight' || e.key === 'ArrowDown') goTo(cur + 1);
            if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') goTo(cur - 1);
        };

        const onResize = () => {
            if (trackRef.current) trackRef.current.style.width = `${PANELS * window.innerWidth}px`;
            targetTX.current = Math.round(currentTX.current / (window.innerWidth || 1)) * window.innerWidth;
        };

        window.addEventListener('wheel', onWheel, { passive: false });
        window.addEventListener('touchstart', onTouchStart, { passive: true });
        window.addEventListener('touchmove', onTouchMove, { passive: true });
        window.addEventListener('keydown', onKeyDown);
        window.addEventListener('resize', onResize);

        if (trackRef.current) trackRef.current.style.width = `${PANELS * window.innerWidth}px`;

        return () => {
            cancelAnimationFrame(rafRef.current);
            window.removeEventListener('wheel', onWheel);
            window.removeEventListener('touchstart', onTouchStart);
            window.removeEventListener('touchmove', onTouchMove);
            window.removeEventListener('keydown', onKeyDown);
            window.removeEventListener('resize', onResize);
        };
    }, [goTo, triggerReveal]);

    const past0 = currentIdx > 0;

    return (
        <>
            {/* NAV */}
            <nav className={`landing-nav${past0 ? ' vis' : ''}`}>
                <span className="nl" onClick={() => goTo(0)}>
                    <svg className="mk" viewBox="0 0 32 32">
                        <circle cx="12" cy="16" r="10" fill="#fff" fillOpacity=".95" />
                        <circle cx="20" cy="16" r="10" fill="#0d3d29" fillOpacity=".55" />
                    </svg>
                    Ditto
                </span>
                <ul className="nv">
                    <li><a className={currentIdx === 0 ? 'act' : ''} onClick={() => goTo(0)}>Home</a></li>
                    <li><a className={currentIdx === 1 ? 'act' : ''} onClick={() => goTo(1)}>How it works</a></li>
                    <li><a className={currentIdx === 2 ? 'act' : ''} onClick={() => goTo(2)}>Why Ditto</a></li>
                </ul>
                <Link to="/login" className="nc">시작하기</Link>
            </nav>

            {/* DOTS */}
            <div id="prog" className={past0 ? 'vis' : ''}>
                {Array.from({ length: PANELS }).map((_, i) => (
                    <div key={i} className={`pdot${currentIdx === i ? ' act' : ''}`} onClick={() => goTo(i)} />
                ))}
            </div>

            {/* TRACK */}
            <div id="track" ref={trackRef}>

                {/* 0 ENTRY / HERO */}
                <div className="panel" id="entry">
                    <canvas ref={canvasRef} id="entry-canvas" />
                    <div id="entry-ui">
                        <svg className="e-mark" viewBox="0 0 32 32">
                            <circle cx="12" cy="16" r="10" fill="#1f6f4a" fillOpacity=".85" />
                            <circle cx="20" cy="16" r="10" fill="#35c281" fillOpacity=".85" style={{ mixBlendMode: 'multiply' }} />
                        </svg>
                        <div className="e-logo">D<em>i</em>tto</div>
                        <h1 className="hh">당신과 <em>같은 파장</em>을 가진<br />사람을 찾아드려요</h1>
                        <p className="e-sub">취향과 관심사의 결을 읽어 매일 한 명, 진짜 통할 사람을 연결합니다.<br />지금 이 순간의 나를 기록해서 남기면, 그 결이 매칭에 자연스럽게 쌓여요.</p>
                    </div>
                    <div className="cm tl" /><div className="cm tr" /><div className="cm bl" /><div className="cm br" />
                    <div className="hint">
                        <span>Scroll</span>
                        <div className="hint-line" />
                    </div>
                    <div className="pcnt">01 — Home</div>
                </div>

                {/* 1 HOW IT WORKS */}
                <div className="panel" id="how">
                    <div className="how-inner">
                        <div className="sl rv">How it works</div>
                        <h2 className="st rv" style={{ fontSize: 'clamp(26px,3.4vw,38px)' }}>세 단계면 충분해요</h2>
                        <div className="feat-grid">
                            <div className="feat rv">
                                <div className="feat-n">01</div>
                                <h3>관심사를 남겨요</h3>
                                <p>취향, 활동, 최근 관심사를 자유롭게 기록하면 프로필 벡터가 자동으로 쌓여요.</p>
                            </div>
                            <div className="feat rv">
                                <div className="feat-n">02</div>
                                <h3>정오에 매칭돼요</h3>
                                <p>벡터 유사도 기반으로 오늘 가장 결이 맞는 한 사람을 찾아 연결합니다.</p>
                            </div>
                            <div className="feat rv">
                                <div className="feat-n">03</div>
                                <h3>이유를 확인해요</h3>
                                <p>왜 이 사람과 잘 맞는지, 공통 관심사를 근거로 AI가 직접 설명해드려요.</p>
                            </div>
                        </div>
                    </div>
                    <div className="pcnt">02 — How it works</div>
                </div>

                {/* 2 WHY / EXPLAIN */}
                <div className="panel" id="explain">
                    <div className="exp-grid">
                        <div className="exp-copy">
                            <div className="sl rv">Why Ditto</div>
                            <h2 className="st rv" style={{ fontSize: 'clamp(24px,3vw,34px)' }}>
                                매칭을<br />블랙박스로<br />두지 않아요
                            </h2>
                            <p className="sd rv">
                                검색(과거 사례) → 증강(근거 조립) → 생성(설명 작성), 세 단계를 거쳐 왜 잘 맞는지 사람이 읽어도 납득되게 설명해요.
                            </p>
                            <div className="layers rv">
                                <div className="layer">
                                    <span className="layer-n">01</span>
                                    <div><div className="layer-t">검색</div><div className="layer-d">비슷한 취향의 후보와 과거 매칭 사례를 찾아요</div></div>
                                </div>
                                <div className="layer">
                                    <span className="layer-n">02</span>
                                    <div><div className="layer-t">증강</div><div className="layer-d">찾은 사례를 근거로 맥락을 조립해요</div></div>
                                </div>
                                <div className="layer">
                                    <span className="layer-n">03</span>
                                    <div><div className="layer-t">생성</div><div className="layer-d">납득되는 문장으로 이유를 설명해요</div></div>
                                </div>
                            </div>
                        </div>
                        <div className="card rv">
                            <div className="card-head">
                                <div className="avpair"><div className="av" /><div className="av" /></div>
                                <div className="score">87<span>% 일치</span></div>
                            </div>
                            <div className="tags">
                                <span className="tag">#여행</span>
                                <span className="tag">#사진</span>
                                <span className="tag">#카페투어</span>
                            </div>
                            <div className="exp-text">
                                <b>"여행과 사진을 함께 즐기는 감성이 잘 맞는 두 분이에요."</b><br />
                                최근 방문한 도시, 저장한 카페 리스트, 촬영 스타일까지 세 가지 신호가 겹쳤어요.
                            </div>
                        </div>
                    </div>
                    <div className="pcnt">03 — Why Ditto</div>
                </div>

                {/* 3 CTA */}
                <div className="panel" id="cta">
                    <div className="cta-c">
                        <div className="sl rv">Get started</div>
                        <h2 className="st rv" style={{ textAlign: 'center' }}>오늘, 같은 파장의<br />한 사람을 만나보세요</h2>
                        <Link to="/login" className="btn btn-p rv">무료로 시작하기 →</Link>
                        <div className="stats-mini rv">
                            <div><div className="snum">12,400+</div><div className="slabel">누적 매칭 수</div></div>
                            <div><div className="snum">4.7 / 5</div><div className="slabel">평균 만족도</div></div>
                            <div><div className="snum">83%</div><div className="slabel">첫 대화 성사율</div></div>
                        </div>
                    </div>
                    <div className="pcnt">04 — Start</div>
                </div>

            </div>
        </>
    );
}
