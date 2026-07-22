import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../lib/apiClient';
import type { PublicProfile } from '../types/user';

// 차단 목록은 프로필(차단 여부 확인)과 설정(차단 관리) 화면에서 공통으로 조회된다.
// react-query 캐시를 공유해서 한쪽에서 토글하면 다른 화면도 자동으로 최신 상태를 본다.
export function useBlocks(options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['blocks'],
    queryFn: () => apiClient.get<PublicProfile[]>('/api/v1/users/me/blocks'),
    enabled: options?.enabled ?? true,
  });
}

interface ToggleBlockVars {
  targetId: string;
  currentlyBlocked: boolean;
  /** 목록에 이미 표시 중인 프로필이 있으면 낙관적 업데이트에서 재사용 */
  knownProfile?: PublicProfile;
}

export function useToggleBlock() {
  const queryClient = useQueryClient();
  const key = ['blocks'];

  return useMutation({
    mutationFn: ({ targetId, currentlyBlocked }: ToggleBlockVars) =>
      currentlyBlocked
        ? apiClient.delete(`/api/v1/users/${targetId}/block`)
        : apiClient.post(`/api/v1/users/${targetId}/block`),

    onMutate: async ({ targetId, currentlyBlocked, knownProfile }) => {
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<PublicProfile[]>(key);

      queryClient.setQueryData<PublicProfile[]>(key, (prev = []) => {
        if (currentlyBlocked) return prev.filter((u) => u.id !== targetId);
        if (prev.some((u) => u.id === targetId)) return prev;
        return [...prev, knownProfile ?? { id: targetId, nickname: '', profileImageUrl: null, bio: null }];
      });

      return { previous };
    },

    onError: (_err, _vars, context) => {
      if (context) queryClient.setQueryData(key, context.previous);
    },
  });
}
