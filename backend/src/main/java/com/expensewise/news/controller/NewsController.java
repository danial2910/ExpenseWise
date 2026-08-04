package com.expensewise.news.controller;

import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.RequiresFeature;
import com.expensewise.news.dto.ArticleResponse;
import com.expensewise.news.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Not paginated — deliberately, despite CLAUDE.md's "list endpoints are
 * always paginated" default. This returns a small, shared, cached batch
 * from one upstream call (not a per-user DB-backed resource), so there is
 * no real list to page through. Confirmed with the project owner; see
 * DECISIONS.md.
 */
@RestController
@RequestMapping("/api/v1/news")
@RequiresFeature(Feature.NEWS)
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public List<ArticleResponse> getNews() {
        return newsService.getLatestNews();
    }
}
