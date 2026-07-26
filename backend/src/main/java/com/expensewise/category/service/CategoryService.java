package com.expensewise.category.service;

import com.expensewise.category.dto.CategoryRequest;
import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.category.dto.PatchCategoryRequest;
import com.expensewise.category.entity.Category;
import com.expensewise.category.mapper.CategoryMapper;
import com.expensewise.category.repository.CategoryRepository;
import com.expensewise.exception.CategoryInUseException;
import com.expensewise.exception.DuplicateCategoryException;
import com.expensewise.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategoryRepository categoryRepository, CategoryMapper categoryMapper) {
        this.categoryRepository = categoryRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional(readOnly = true)
    public Page<CategoryResponse> listCategories(Long userId, String type, Pageable pageable) {
        Page<Category> page = StringUtils.hasText(type)
                ? categoryRepository.findVisibleToUserByType(userId, type, pageable)
                : categoryRepository.findVisibleToUser(userId, pageable);
        return page.map(categoryMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(Long userId, Long id) {
        return categoryMapper.toResponse(findVisibleOrThrow(userId, id));
    }

    @Transactional
    public CategoryResponse createCategory(Long userId, CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByUserIdAndNameAndType(userId, name, request.type())) {
            throw new DuplicateCategoryException("You already have a category with this name and type");
        }

        Category category = new Category();
        category.setUserId(userId);
        category.setName(name);
        category.setType(request.type());
        category.setIcon(request.icon());
        category.setSystem(false);

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(Long userId, Long id, CategoryRequest request) {
        Category category = findOwnedOrThrow(userId, id);
        String name = request.name().trim();

        if (nameOrTypeChanged(category, name, request.type())
                && categoryRepository.existsByUserIdAndNameAndTypeAndIdNot(userId, name, request.type(), id)) {
            throw new DuplicateCategoryException("You already have a category with this name and type");
        }

        category.setName(name);
        category.setType(request.type());
        category.setIcon(request.icon());

        return categoryMapper.toResponse(category);
    }

    @Transactional
    public CategoryResponse patchCategory(Long userId, Long id, PatchCategoryRequest request) {
        Category category = findOwnedOrThrow(userId, id);
        String name = request.name() != null ? request.name().trim() : category.getName();
        String type = request.type() != null ? request.type() : category.getType();

        if (nameOrTypeChanged(category, name, type)
                && categoryRepository.existsByUserIdAndNameAndTypeAndIdNot(userId, name, type, id)) {
            throw new DuplicateCategoryException("You already have a category with this name and type");
        }

        category.setName(name);
        category.setType(type);
        if (request.icon() != null) {
            category.setIcon(request.icon());
        }

        return categoryMapper.toResponse(category);
    }

    @Transactional
    public void deleteCategory(Long userId, Long id) {
        Category category = findOwnedOrThrow(userId, id);
        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new CategoryInUseException("This category is used by existing transactions and cannot be deleted");
        }
    }

    private boolean nameOrTypeChanged(Category category, String name, String type) {
        return !name.equals(category.getName()) || !type.equals(category.getType());
    }

    /**
     * System categories (userId null) and the caller's own custom categories
     * are both visible for reads.
     */
    private Category findVisibleOrThrow(Long userId, Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        boolean visible = category.getUserId() == null || category.getUserId().equals(userId);
        if (!visible) {
            throw new ResourceNotFoundException("Category not found");
        }
        return category;
    }

    /**
     * Only the caller's own custom categories can be mutated — a system
     * category or another user's category is reported as 404, not 403, so
     * its existence isn't leaked.
     */
    private Category findOwnedOrThrow(Long userId, Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        boolean owned = category.getUserId() != null && category.getUserId().equals(userId);
        if (!owned) {
            throw new ResourceNotFoundException("Category not found");
        }
        return category;
    }
}
