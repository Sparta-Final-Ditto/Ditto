// atob()은 base64를 Latin1로 디코딩해 한글 등 non-ASCII 클레임이 깨지므로 UTF-8로 재해석해야 한다.
export function decodeJwtPayload(token: string): { sub?: string; nickname?: string; [key: string]: unknown } {
  const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(base64);
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return JSON.parse(new TextDecoder('utf-8').decode(bytes));
}
