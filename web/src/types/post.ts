export type PostVisibility = 'PUBLIC' | 'FOLLOWERS_ONLY' | 'PRIVATE';

export interface PostItem {
  postId: string;
  thumbnailUrl: string | null;
  mediaType: string | null;
  contentSummary: string | null;
}

export interface MediaItem {
  id: string;
  mediaUrl: string;
  mediaType: string;
  sortOrder: number;
}

export interface PostAuthor {
  userId: string;
  nickname: string;
}

export interface PostDetail {
  isMyPost: boolean;
  postId: string;
  content: string;
  likeCount: number;
  commentCount: number;
  media: MediaItem[];
}

export interface CommentSummary {
  commentId: string;
  postId: string;
  author: PostAuthor;
  content: string;
  isMyComment: boolean;
  isDeletable: boolean;
  createdAt: string;
}

export interface CommentListResponse {
  comments: CommentSummary[];
  nextCursor: string | null;
  hasNext: boolean;
}

export interface FeedItem {
  postId: string;
  author: PostAuthor;
  content: string;
  mediaFiles: { s3Key: string; mediaUrl: string; mediaType: string; sortOrder: number }[];
  tags: string[];
  neighborhood: string | null;
  likeCount: number;
  isLiked: boolean;
  commentCount: number;
  createdAt: string;
}

export interface FeedPageResponse {
  feeds: FeedItem[];
  nextCursor: string | null;
  hasNext: boolean;
}

export interface UploadUrlFileRequest {
  fileName: string;
  fileType: string;
  fileSize: number;
}

export interface UploadUrlFileResponse {
  presignedUrl: string;
  s3Key: string;
}

export interface UploadUrlResponse {
  files: UploadUrlFileResponse[];
}

export interface PostMediaFile {
  s3Key: string;
  mediaType: string;
  sortOrder: number;
}

export interface CreatePostRequest {
  content: string;
  tags: string[];
  latitude: number;
  longitude: number;
  visibility: PostVisibility;
  showLocation: boolean;
  mediaFiles: PostMediaFile[];
}

export interface UpdatePostDisplayRequest {
  showLocation: boolean | null;
  visibility: PostVisibility | null;
}

export interface UpdatePostDisplayResponse {
  postId: string;
  showLocation: boolean | null;
  visibility: PostVisibility | null;
}
