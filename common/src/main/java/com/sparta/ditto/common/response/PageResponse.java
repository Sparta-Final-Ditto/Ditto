package com.sparta.ditto.common.response;

import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;
    private final String sort;

    private PageResponse(Page<T> page, String sort) {
        this.content = page.getContent();
        this.page = page.getNumber();
        this.size = page.getSize();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.sort = sort;
    }

    public static <T> PageResponse<T> of(Page<T> page, String sort) {
        return new PageResponse<>(page, sort);
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page, "createdAt,DESC");
    }
}