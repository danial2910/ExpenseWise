package com.expensewise.category.controller;

import com.expensewise.auth.security.AuthPrincipal;
import com.expensewise.entitlement.Feature;
import com.expensewise.entitlement.RequiresFeature;
import com.expensewise.category.dto.CategoryRequest;
import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.category.dto.PatchCategoryRequest;
import com.expensewise.category.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@RequiresFeature(Feature.CATEGORIES)
public class CategoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Page<CategoryResponse> listCategories(@AuthenticationPrincipal AuthPrincipal principal,
                                                   @RequestParam(required = false) String type,
                                                   @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return categoryService.listCategories(principal.userId(), type, clamp(pageable));
    }

    @GetMapping("/{id}")
    public CategoryResponse getCategory(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        return categoryService.getCategory(principal.userId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@AuthenticationPrincipal AuthPrincipal principal,
                                            @Valid @RequestBody CategoryRequest request) {
        return categoryService.createCategory(principal.userId(), request);
    }

    @PutMapping("/{id}")
    public CategoryResponse updateCategory(@AuthenticationPrincipal AuthPrincipal principal,
                                            @PathVariable Long id,
                                            @Valid @RequestBody CategoryRequest request) {
        return categoryService.updateCategory(principal.userId(), id, request);
    }

    @PatchMapping("/{id}")
    public CategoryResponse patchCategory(@AuthenticationPrincipal AuthPrincipal principal,
                                           @PathVariable Long id,
                                           @Valid @RequestBody PatchCategoryRequest request) {
        return categoryService.patchCategory(principal.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable Long id) {
        categoryService.deleteCategory(principal.userId(), id);
    }

    private Pageable clamp(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }
}
