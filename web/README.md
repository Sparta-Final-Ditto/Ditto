# Ditto 웹 프론트엔드

Ditto 서비스의 웹 클라이언트입니다. React + TypeScript + Vite로 작성되었으며, 랜딩/회원가입/로그인 및 피드·매칭·채팅·알림·프로필·설정 탭으로 구성된 대시보드를 제공합니다.

## 사전 준비

- Node.js 20 이상
- 백엔드: `api-gateway`가 `http://localhost:8080`에서 떠 있어야 합니다(로그인, 피드, 매칭, 채팅 등 모든 API 호출이 게이트웨이를 경유합니다). 게이트웨이 및 각 마이크로서비스 실행 방법은 저장소 루트의 `docker-compose.yml` / `docker-compose.infra.yml`을 참고하세요.

## 환경 변수

`web/.env` 파일에 다음 값을 설정합니다(`.gitignore` 대상이라 각자 로컬에 생성):

```
VITE_API_BASE_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws-chat
```

## 실행

```bash
cd web
npm install
npm run dev
```

기본적으로 `http://localhost:5173`에서 열립니다. `vite.config.ts`에 `/api`, `/ws-chat` 요청을 `http://localhost:8080`(api-gateway)으로 프록시하는 설정이 포함되어 있습니다.

## 주요 라우트

| 경로 | 화면 |
|---|---|
| `/` | 랜딩 페이지 |
| `/login` | 로그인 |
| `/signup` | 회원가입 |
| `/app` | 대시보드(피드 / 매칭 / 채팅 / 알림 / 프로필 / 설정 탭) |

## 빌드 / 프리뷰

```bash
npm run build     # tsc -b && vite build
npm run preview   # 빌드 결과물 로컬 프리뷰
```

## 참고

- 인증 토큰(`accessToken`, `refreshToken`, `userId`)은 `localStorage`에 저장됩니다.
- 챗봇 위젯은 `assistant-service`(게이트웨이 경유)를 호출합니다.
