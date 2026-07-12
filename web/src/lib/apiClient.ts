const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

interface ApiEnvelope<T> {
  status: number;
  code: string | null;
  message: string;
  data: T;
  errors: string[] | null;
}

export class ApiError extends Error {
  status: number;
  code: string | null;
  errors: string[] | null;

  constructor(message: string, status: number, code: string | null, errors: string[] | null) {
    super(message);
    this.status = status;
    this.code = code;
    this.errors = errors;
  }
}

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

function clearSessionAndRedirect() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem('userId');
  localStorage.removeItem('userName');
  if (window.location.pathname !== '/login') window.location.href = '/login';
}

// 여러 요청이 동시에 401을 받아도 재발급은 한 번만 일어나게 진행 중인 요청을 공유한다.
let reissuePromise: Promise<string | null> | null = null;

async function reissueAccessToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) return null;

  if (!reissuePromise) {
    reissuePromise = fetch('/api/v1/auth/reissue', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
      .then((res) => (res.ok ? res.json() : Promise.reject()))
      .then((body: ApiEnvelope<{ accessToken: string; refreshToken?: string }>) => {
        localStorage.setItem(ACCESS_TOKEN_KEY, body.data.accessToken);
        if (body.data.refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, body.data.refreshToken);
        return body.data.accessToken;
      })
      .catch(() => null)
      .finally(() => {
        reissuePromise = null;
      });
  }
  return reissuePromise;
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  /** match_service의 today 엔드포인트처럼 ApiResponse로 래핑되지 않은 응답을 그대로 반환받을 때 사용 */
  raw?: boolean;
  /** 401 재발급 재시도를 건너뛸 때 사용(재발급 호출 자체 등) */
  skipAuthRetry?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, raw, skipAuthRetry, headers, ...rest } = options;

  const doFetch = () =>
    fetch(path, {
      ...rest,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...authHeaders(),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

  let res = await doFetch();

  if (res.status === 401 && !skipAuthRetry) {
    const newToken = await reissueAccessToken();
    if (!newToken) {
      clearSessionAndRedirect();
      throw new ApiError('로그인이 필요해요.', 401, null, null);
    }
    res = await doFetch();
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  const parsed = text ? JSON.parse(text) : null;

  if (!res.ok) {
    const message = parsed?.message || '요청에 실패했어요.';
    throw new ApiError(message, res.status, parsed?.code ?? null, parsed?.errors ?? null);
  }

  return (raw ? parsed : parsed?.data ?? null) as T;
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'POST', body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: 'PATCH', body }),
  delete: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: 'DELETE' }),
};
