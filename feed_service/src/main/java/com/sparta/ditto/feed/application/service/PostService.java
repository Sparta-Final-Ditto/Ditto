package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.dto.command.CreatePostCommand;
import com.sparta.ditto.feed.application.dto.command.CreatePostCommand.MediaFileItem;
import com.sparta.ditto.feed.application.dto.command.UpdatePostDisplayCommand;
import com.sparta.ditto.feed.application.dto.query.GetUserPostsQuery;
import com.sparta.ditto.feed.application.dto.result.PostDetailResult;
import com.sparta.ditto.feed.application.dto.result.PostResult;
import com.sparta.ditto.feed.application.dto.result.UpdatePostDisplayResult;
import com.sparta.ditto.feed.application.dto.result.UserPostItemResult;
import com.sparta.ditto.feed.application.dto.result.UserPostsResult;
import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.entity.PostTag;
import com.sparta.ditto.feed.domain.exception.ForbiddenException;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import com.sparta.ditto.feed.domain.repository.CommentRepository;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.service.PostValidator;
import com.sparta.ditto.feed.domain.type.Visibility;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPort outboxEventPort;

    @Value("${app.cloudfront.domain}")
    private String cloudfrontDomain;

    @Transactional(readOnly = true)
    public PostDetailResult getPostDetail(UUID postId, UUID requesterId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(PostNotFoundException::new);
        List<Comment> comments = commentRepository.findByPostIdAndDeletedAtIsNull(postId);
        return PostDetailResult.from(post, requesterId, comments, cloudfrontDomain);
    }

    @Transactional(readOnly = true)
    public UserPostsResult getUserPosts(GetUserPostsQuery query) {
        List<Visibility> allowedScopes = query.requesterId().equals(query.targetUserId())
                ? List.of(Visibility.PUBLIC, Visibility.FOLLOWERS_ONLY, Visibility.PRIVATE)
                : List.of(Visibility.PUBLIC);

        Instant cursorAt = null;
        UUID cursorId = null;
        if (query.cursor() != null) {
            Post cursorPost = postRepository.findById(query.cursor()).orElse(null);
            if (cursorPost != null) {
                cursorAt = cursorPost.getCreatedAt();
                cursorId = cursorPost.getId();
            }
        }

        List<Post> fetched = postRepository.findByUserIdAndScopesWithCursor(
                query.targetUserId(), allowedScopes, cursorAt, cursorId, query.size() + 1);

        boolean hasNext = fetched.size() > query.size();
        List<Post> page = hasNext ? fetched.subList(0, query.size()) : fetched;
        UUID nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;

        List<UserPostItemResult> items = page.stream()
                .map(post -> UserPostItemResult.from(post, cloudfrontDomain))
                .toList();

        return new UserPostsResult(items, nextCursor, hasNext);
    }

    @Transactional
    public UpdatePostDisplayResult updatePostDisplay(UpdatePostDisplayCommand command) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(command.postId())
                .orElseThrow(PostNotFoundException::new);

        if (!command.requesterId().equals(post.getUserId())) {
            throw new ForbiddenException();
        }

        if (command.visibility() != null) {
            post.changeVisibility(Visibility.from(command.visibility()));
        }
        post.changeShowLocation(command.showLocation());

        postRepository.save(post);
        return UpdatePostDisplayResult.from(post);
    }

    @Transactional
    public void deletePost(UUID postId, UUID requesterId, String requesterRole) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(PostNotFoundException::new);

        boolean isAuthor = requesterId.equals(post.getUserId());
        boolean isAdmin = "ADMIN".equals(requesterRole);

        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException();
        }

        post.delete(requesterId);
        // PostMedia, PostTag는 Post aggregate 소속이며, Post.deletedAt으로 접근이 차단되므로 별도 soft delete 불필요.
        commentRepository.softDeleteAllByPostId(postId, requesterId);
        likeRepository.softDeleteAllByPostId(postId, requesterId);
        outboxEventRepository.save(outboxEventPort.buildPostDeleted(post, requesterId));
    }

    @Transactional
    public PostResult createPost(CreatePostCommand command, String neighborhood, String nickname) {
        List<MediaFileItem> mediaFiles =
                command.mediaFiles() != null ? command.mediaFiles() : List.of();

        Visibility visibility = Visibility.from(command.visibility());

        PostValidator.validateContentOrMedia(command.content(), !mediaFiles.isEmpty());

        boolean showLocation = command.showLocation() != null ? command.showLocation() : true;

        List<String> distinctTags = command.tags().stream().distinct().toList();

        Post post = new Post(command.userId(), nickname, command.content(), neighborhood,
                command.latitude(), command.longitude(), visibility, showLocation);

        distinctTags.forEach(tag -> post.getTags().add(new PostTag(post, tag)));

        mediaFiles.forEach(
                m -> post.addMedia(new PostMedia(post, m.s3Key(), m.mediaType(), m.sortOrder())));

        Post savedPost = postRepository.save(post);

        outboxEventRepository.save(
                outboxEventPort.buildPostCreated(savedPost, command.userId(), distinctTags));

        return PostResult.from(savedPost, nickname, cloudfrontDomain);
    }
}
