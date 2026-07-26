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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, categoryMapper);
        lenient().when(categoryMapper.toResponse(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            return new CategoryResponse(category.getId(), category.getName(), category.getType(),
                    category.getIcon(), category.isSystem());
        });
    }

    private Category systemCategory() {
        Category category = new Category();
        category.setId(10L);
        category.setUserId(null);
        category.setName("Food");
        category.setType("EXPENSE");
        category.setIcon("utensils");
        category.setSystem(true);
        return category;
    }

    private Category ownCategory() {
        Category category = new Category();
        category.setId(20L);
        category.setUserId(USER_ID);
        category.setName("Pet Care");
        category.setType("EXPENSE");
        category.setIcon("pet");
        category.setSystem(false);
        return category;
    }

    private Category otherUsersCategory() {
        Category category = new Category();
        category.setId(30L);
        category.setUserId(OTHER_USER_ID);
        category.setName("Side Hustle");
        category.setType("INCOME");
        category.setIcon("freelance");
        category.setSystem(false);
        return category;
    }

    @Test
    void getCategoryReturnsASystemCategoryForAnyUser() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(systemCategory()));

        CategoryResponse response = categoryService.getCategory(USER_ID, 10L);

        assertThat(response.system()).isTrue();
        assertThat(response.name()).isEqualTo("Food");
    }

    @Test
    void getCategoryReturnsTheCallersOwnCustomCategory() {
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(ownCategory()));

        CategoryResponse response = categoryService.getCategory(USER_ID, 20L);

        assertThat(response.name()).isEqualTo("Pet Care");
    }

    @Test
    void getCategoryHidesAnotherUsersCustomCategoryAs404() {
        when(categoryRepository.findById(30L)).thenReturn(Optional.of(otherUsersCategory()));

        assertThatThrownBy(() -> categoryService.getCategory(USER_ID, 30L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createCategoryRejectsADuplicateNameAndType() {
        when(categoryRepository.existsByUserIdAndNameAndType(USER_ID, "Pet Care", "EXPENSE")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(USER_ID, new CategoryRequest("Pet Care", "EXPENSE", "pet")))
                .isInstanceOf(DuplicateCategoryException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createCategorySavesAsANonSystemCategoryOwnedByTheCaller() {
        when(categoryRepository.existsByUserIdAndNameAndType(USER_ID, "Pet Care", "EXPENSE")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.createCategory(USER_ID, new CategoryRequest("Pet Care", "EXPENSE", "pet"));

        assertThat(response.system()).isFalse();
        assertThat(response.name()).isEqualTo("Pet Care");
    }

    @Test
    void updateCategoryOnASystemCategoryIsRejectedAs404() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(systemCategory()));

        assertThatThrownBy(() -> categoryService.updateCategory(USER_ID, 10L, new CategoryRequest("Food 2", "EXPENSE", "utensils")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCategoryOnAnotherUsersCategoryIsRejectedAs404() {
        when(categoryRepository.findById(30L)).thenReturn(Optional.of(otherUsersCategory()));

        assertThatThrownBy(() -> categoryService.updateCategory(USER_ID, 30L, new CategoryRequest("Renamed", "INCOME", "freelance")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCategoryRejectsRenamingIntoAnExistingDuplicate() {
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(ownCategory()));
        when(categoryRepository.existsByUserIdAndNameAndTypeAndIdNot(USER_ID, "Book Club", "EXPENSE", 20L))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(USER_ID, 20L, new CategoryRequest("Book Club", "EXPENSE", "pet")))
                .isInstanceOf(DuplicateCategoryException.class);
    }

    @Test
    void patchCategoryOnlyChangesTheFieldsProvided() {
        Category existing = ownCategory();
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(existing));

        CategoryResponse response = categoryService.patchCategory(USER_ID, 20L, new PatchCategoryRequest(null, null, "paw"));

        assertThat(response.name()).isEqualTo("Pet Care");
        assertThat(response.type()).isEqualTo("EXPENSE");
        assertThat(existing.getIcon()).isEqualTo("paw");
    }

    @Test
    void deleteCategoryOnASystemCategoryIsRejectedAs404() {
        when(categoryRepository.findById(10L)).thenReturn(Optional.of(systemCategory()));

        assertThatThrownBy(() -> categoryService.deleteCategory(USER_ID, 10L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategoryReferencedByTransactionsFailsCleanly() {
        Category existing = ownCategory();
        when(categoryRepository.findById(20L)).thenReturn(Optional.of(existing));
        doNothing().when(categoryRepository).delete(existing);
        doThrow(new DataIntegrityViolationException("FK violation")).when(categoryRepository).flush();

        assertThatThrownBy(() -> categoryService.deleteCategory(USER_ID, 20L))
                .isInstanceOf(CategoryInUseException.class);
    }
}
