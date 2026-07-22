import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../lib/apiClient';
import type { PublicProfile } from '../types/user';

// 팔로워/팔로잉 목록은 여러 화면(프로필, 채팅방 초대 패널 등)에서 같은 유저 기준으로 반복 조회된다.
// react-query 캐시를 공유해서 화면을 옮겨다녀도 fresh한 동안은 재요청하지 않는다.
export function useFollowers(userId: string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['followers', userId],
    queryFn: () => apiClient.get<PublicProfile[]>(`/api/v1/users/${userId}/followers`),
    enabled: options?.enabled ?? true,
  });
}

export function useFollowings(userId: string, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: ['followings', userId],
    queryFn: () => apiClient.get<PublicProfile[]>(`/api/v1/users/${userId}/followings`),
    enabled: options?.enabled ?? true,
  });
}

interface ToggleFollowVars {
  currentUserId: string;
  targetId: string;
  currentlyFollowing: boolean;
  /** 목록에 이미 표시 중인 프로필이 있으면 낙관적 업데이트에서 재사용(빈 자리표시자 대신 실제 닉네임/이미지 표시) */
  knownProfile?: PublicProfile;
}

export function useToggleFollow() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ targetId, currentlyFollowing }: ToggleFollowVars) =>
      currentlyFollowing
        ? apiClient.delete(`/api/v1/users/${targetId}/follow`)
        : apiClient.post(`/api/v1/users/${targetId}/follow`),

    onMutate: async ({ currentUserId, targetId, currentlyFollowing, knownProfile }) => {
      const key = ['followings', currentUserId];
      await queryClient.cancelQueries({ queryKey: key });
      const previous = queryClient.getQueryData<PublicProfile[]>(key);

      queryClient.setQueryData<PublicProfile[]>(key, (prev = []) => {
        if (currentlyFollowing) return prev.filter((f) => f.id !== targetId);
        if (prev.some((f) => f.id === targetId)) return prev;
        return [...prev, knownProfile ?? { id: targetId, nickname: '', profileImageUrl: null, bio: null }];
      });

      return { previous, key };
    },

    onError: (_err, _vars, context) => {
      if (context) queryClient.setQueryData(context.key, context.previous);
    },

    onSettled: (_data, _err, { currentUserId, targetId }) => {
      // 내가 팔로우한 목록과, 상대방의 팔로워 목록만 실제로 바뀐다.
      queryClient.invalidateQueries({ queryKey: ['followings', currentUserId] });
      queryClient.invalidateQueries({ queryKey: ['followers', targetId] });
    },
  });
}
