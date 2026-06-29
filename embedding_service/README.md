# embedding_service

게시글 텍스트를 임베딩 벡터로 변환하고 EMA 기반 유저 프로필을 관리하는 Python 서비스.

## 기술 스택

| 항목 | 기술 |
|---|---|
| 프레임워크 | FastAPI + uvicorn |
| 임베딩 모델 | `jhgan/ko-sroberta-multitask` (768차원) |
| 벡터 DB | PostgreSQL + pgvector |
| DB 마이그레이션 | Alembic |
| 메시지 큐 | Kafka |
| 배치 스케줄러 | APScheduler |

---

## 서비스 의존 관계

```
user_service  ──(Kafka: USER_REGISTERED)──►  embedding_service
feed_service  ──(Kafka: post-events)──────►  embedding_service
embedding_service  ──(REST: OpenFeign)────►  match_service
```

---

## 디렉토리 구조

```
app/
├── common/              # 공통 유틸 (DB, Kafka, 미들웨어, 예외)
├── config/              # 환경변수 설정 (settings.py)
└── embedding/
    ├── application/
    │   ├── port/        # 외부 모델 포트 인터페이스
    │   └── service/     # EmbeddingService (핵심 비즈니스 로직)
    ├── domain/
    │   ├── algorithm/   # EMA 계산, 시간 감쇠, 텍스트 빌더
    │   ├── model/       # 도메인 모델 (PostEmbedding, UserProfile)
    │   └── repository/  # 레포지터리 추상화
    ├── infrastructure/
    │   ├── batch/       # 일배치 · 월배치
    │   ├── kafka/       # PostConsumer, UserRegisteredConsumer
    │   ├── model/       # 임베딩 모델 로더
    │   └── repository/  # PostgreSQL 구현체
    └── presentation/
        ├── dto/         # 요청/응답 스키마
        └── router/      # embedding · internal · test 라우터
```

---

## 전체 처리 흐름

```
USER_CREATED 이벤트
  └─ user_profile_embeddings stub 행 생성 (vector=NULL, active=false)

USER_INTERESTS_REGISTERED 이벤트
  └─ 관심사 해시태그 → 초기 프로필 벡터 생성 → user_profile_embeddings 갱신

POST_CREATED 이벤트
  └─ 게시글 텍스트(content + hashtags) → 임베딩 벡터 생성
  └─ user_posts_embeddings 저장 (status=DONE)
  └─ user_profile_embeddings record_count 증가, active 갱신

POST_DELETED 이벤트
  └─ KST 당일 게시글인 경우만 status=DELETED 처리
  └─ record_count 감소, active 재확인
  └─ 새벽 배치 EMA 계산에서 자동 제외 (WHERE status='DONE')

일배치 (매일 KST 03:00)
  └─ FAILED → DONE 복구
  └─ 전체 유저 EMA 증분 재계산 → user_profile_embeddings 갱신

월배치 (매월 1일 KST 04:00)
  └─ 전체 유저 시간 감쇠 가중 평균 재계산
  └─ weight = exp(-age_days / 7)
  └─ 게시글 없는 유저는 기존 임베딩 유지
```

### 임베딩 처리 방식

**일배치 — EMA 증분 처리** (매일 KST 03:00)

```
전체 유저 순회
  │
  ├─ last_processed_record_id 있음 (이전 배치 이력 존재)
  │    └─ 해당 id 이후 신규 DONE 게시글만 조회
  │         ├─ 신규 없음 → skip
  │         └─ 신규 있음 → EMA 누적 계산
  │                          v_new = α × v_post + (1-α) × v_profile
  │                          α = 0.1  (최신 게시글 10% 반영)
  │
  └─ last_processed_record_id 없음 (첫 배치 또는 프로필 초기 상태)
       └─ 전체 DONE 게시글 조회
            └─ 첫 벡터부터 순서대로 EMA 전체 재생(replay)

→ user_profile_embeddings 갱신: vector, record_count, active, last_processed_record_id
```

**월배치 — 시간 감쇠 전체 재계산** (매월 1일 KST 04:00)

```
전체 유저 순회
  │
  └─ 전체 DONE 게시글 조회 (DELETED 제외, embedded_at ASC)
       │
       ├─ 게시글 없음 → skip (기존 임베딩 유지)
       │
       └─ 게시글 있음 → 시간 감쇠 가중 평균 계산
                          age_days = (now - embedded_at) / 86400
                          weight   = exp(-age_days / 7)
                          result   = Σ(weight_i × v_i) / Σ(weight_i)
                          → L2 정규화

→ user_profile_embeddings 갱신: vector, record_count, active, last_processed_record_id
```

> 월배치 실행 후 `last_processed_record_id`가 최신 post_id로 갱신되므로, 다음 일배치는 그 이후 신규 게시글부터 증분 처리한다.

---

### embedding_status 전환

| 값 | 설명 |
|---|---|
| `PENDING` | 임베딩 처리 전 초기 상태 |
| `DONE` | 임베딩 완료, 배치 EMA 계산 대상 |
| `FAILED` | 3회 재시도 실패, 다음 일배치에서 DONE으로 복구 후 재계산 |
| `DELETED` | 당일 게시글 삭제 처리, 배치 계산에서 영구 제외 |

### 매칭 활성화 기준

`record_count >= 3` (MIN_RECORDS_FOR_MATCHING) 도달 시 `active = true` → 매칭 대상 포함

---

## 사전 요구 사항

- Python 3.10+
- Docker Desktop

---

## 초기 설정

### 1. 가상환경 생성 및 패키지 설치

```bash
cd embedding_service

python -m venv .venv

# macOS/Linux
source .venv/bin/activate

# Windows
.venv\Scripts\activate

pip install -r requirements.txt
```

### 2. 환경변수 설정

`.env` 파일을 생성하고 아래 값을 설정한다.

```env
DATABASE_URL=postgresql+asyncpg://embedding_user:embedding_pwd@localhost:5434/embedding_db
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_TOPIC_POST_EVENTS=post-events
KAFKA_TOPIC_USER_EVENTS=USER_REGISTERED
KAFKA_TOPIC_DLQ=post-events-dlq
KAFKA_CONSUMER_GROUP=embedding-service-group
EMA_ALPHA=0.1
MIN_RECORDS_FOR_MATCHING=3
```

### 3. DB 컨테이너 실행

```bash
docker compose -f docker-compose-local.yml up -d
```

PostgreSQL 16 + pgvector 이미지를 포트 5434로 실행한다.

### 4. 마이그레이션 적용

```bash
alembic upgrade head
```

테이블 생성 확인:
- `user_posts_embeddings` — 개별 게시글 임베딩 벡터
- `user_profile_embeddings` — 유저 EMA 통합 프로필 벡터

---

## 실행

```bash
uvicorn app.main:app --reload
```

| URL | 설명 |
|---|---|
| http://localhost:8000/docs | Swagger UI |
| http://localhost:8000/redoc | ReDoc |
| http://localhost:8000/health | 헬스체크 |

> 최초 실행 시 임베딩 모델(`jhgan/ko-sroberta-multitask`, 약 440MB)을 다운로드한다.

---

## Swagger 섹션 구조

| 섹션 | 경로 prefix | 설명 |
|---|---|---|
| **Embedding** | `/api/v1/embedding` | 임베딩 상태 조회, 재처리 |
| **Internal** | `/api/v1/internal/embedding` | match_service OpenFeign 연동용 |
| **Test** | `/api/v1/test` | 개발·검증용 수동 트리거 |
| **Health** | `/health` | 서비스 상태 확인 |

### Test 엔드포인트 목록

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/v1/test/batch/daily` | 일배치 즉시 실행 |
| `POST` | `/api/v1/test/batch/monthly` | 월배치 즉시 실행 |
| `POST` | `/api/v1/test/embedding` | 텍스트 직접 임베딩 |
| `POST` | `/api/v1/test/embedding/initial-profile` | 초기 프로필 임베딩 |
| `POST` | `/api/v1/test/embedding/post` | 게시글 임베딩 |
| `POST` | `/api/v1/test/embedding/embed-and-store` | embed_and_store 직접 호출 |

---

## Kafka 토픽

| 토픽 | 방향 | 이벤트 | 설명 |
|---|---|---|---|
| `post-events` | 소비 | `POST_CREATED` | 게시글 임베딩 생성 및 프로필 갱신 |
| `post-events` | 소비 | `POST_DELETED` | 당일 게시글 DELETED 처리 |
| `USER_REGISTERED` | 소비 | `USER_CREATED` | 프로필 stub 행 생성 (vector=NULL) |
| `USER_REGISTERED` | 소비 | `USER_INTERESTS_REGISTERED` | 관심사 기반 초기 프로필 벡터 생성 |
| `post-events-dlq` | 소비·발행 | — | 임베딩 3회 실패 메시지 보관, 일배치 재처리 |

---

## 배치 스케줄

| 배치 | 실행 시각 | 동작 |
|---|---|---|
| 일배치 | 매일 KST 03:00 | FAILED 복구 → EMA 증분 재계산 |
| 월배치 | 매월 1일 KST 04:00 | 시간 감쇠 가중 평균 전체 재계산 |

배치는 서버 기동 시 APScheduler가 자동 등록하며, 개발 환경에서는 Swagger Test 섹션의 수동 트리거로 즉시 실행할 수 있다.

---

## 테스트 실행

```bash
pytest tests/
```
