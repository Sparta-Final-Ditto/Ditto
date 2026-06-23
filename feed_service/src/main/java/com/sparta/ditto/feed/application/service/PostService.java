package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.dto.CreatePostCommand;
import com.sparta.ditto.feed.application.dto.CreatePostCommand.MediaFileItem;
import com.sparta.ditto.feed.application.dto.PostDetailResult;
import com.sparta.ditto.feed.application.dto.PostResult;
import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.entity.PostTag;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import com.sparta.ditto.feed.domain.repository.CommentRepository;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.service.PostValidator;
import com.sparta.ditto.feed.domain.type.LocationScope;
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
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPort outboxEventPort;

    @Value("${app.cloudfront.domain}")
    private String cloudfrontDomain;

    @Transactional
    public PostDetailResult getPostDetail(UUID postId, UUID requesterId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(PostNotFoundException::new);
        postRepository.incrementViewCount(postId);
        List<Comment> comments = commentRepository.findByPostIdAndDeletedAtIsNull(postId);
        return PostDetailResult.from(post, requesterId, comments, cloudfrontDomain);
    }

    @Transactional
    public PostResult createPost(CreatePostCommand command, String neighborhood, String nickname) {
        List<MediaFileItem> mediaFiles =
                command.mediaFiles() != null ? command.mediaFiles() : List.of();

        LocationScope locationScope = LocationScope.from(command.locationScope());

        PostValidator.validateContentOrMedia(command.content(), !mediaFiles.isEmpty());

        boolean showLocation = command.showLocation() != null ? command.showLocation() : true;

        List<String> distinctTags = command.tags().stream().distinct().toList();

        Post post = new Post(command.userId(), nickname, command.content(), neighborhood,
                command.latitude(), command.longitude(), locationScope, showLocation);

        distinctTags.forEach(tag -> post.getTags().add(new PostTag(post, tag)));

        mediaFiles.forEach(
                m -> post.addMedia(new PostMedia(post, m.s3Key(), m.mediaType(), m.sortOrder())));

        Post savedPost = postRepository.save(post);

        outboxEventRepository.save(
                outboxEventPort.buildPostCreated(savedPost, command.userId(), distinctTags));

        return PostResult.from(savedPost, nickname, cloudfrontDomain);
    }
}
