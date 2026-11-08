package com.retail.product.service;

import com.retail.product.domain.Category;
import com.retail.product.dto.CategoryRequest;
import com.retail.product.dto.CategoryResponse;
import com.retail.product.exception.ConflictException;
import com.retail.product.exception.InvalidRequestException;
import com.retail.product.repository.CategoryRepository;
import com.retail.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categories;
    @Mock ProductRepository products;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categories, products);
    }

    private static Category category(long id, String name, Long parentId) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setParentId(parentId);
        c.setSlug(name.toLowerCase());
        return c;
    }

    @Test
    void slugIsGeneratedFromTheName() {
        when(categories.existsBySlug("men-s-shoes")).thenReturn(false);
        when(categories.saveAndFlush(any(Category.class))).thenAnswer(returnsFirstArg());

        CategoryResponse response = service.create(new CategoryRequest("  Men's Shoes ", null, null, null));

        assertThat(response.slug()).isEqualTo("men-s-shoes");
        assertThat(response.name()).isEqualTo("Men's Shoes");
        assertThat(response.active()).isTrue();
    }

    @Test
    void duplicateSlugIsRejected() {
        when(categories.existsBySlug("clothing")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CategoryRequest("Clothing", null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void categoryCannotMoveUnderItsOwnChild() {
        Category root = category(1L, "Root", null);
        Category child = category(2L, "Child", 1L);
        when(categories.findById(1L)).thenReturn(Optional.of(root));
        when(categories.existsById(2L)).thenReturn(true);
        when(categories.findAll()).thenReturn(List.of(root, child));

        assertThatThrownBy(() -> service.update(1L, new CategoryRequest("Root", 2L, null, true)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("sub-categories");
    }

    @Test
    void categoryWithSubCategoriesCannotBeDeleted() {
        when(categories.findById(1L)).thenReturn(Optional.of(category(1L, "Root", null)));
        when(categories.existsByParentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void descendantsIncludeTheCategoryItself() {
        Category root = category(1L, "Root", null);
        Category child = category(2L, "Child", 1L);
        Category grandChild = category(3L, "Grand", 2L);
        Category other = category(4L, "Other", null);
        when(categories.findAll()).thenReturn(List.of(root, child, grandChild, other));

        assertThat(service.idsWithDescendants(1L)).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(service.idsWithDescendants(99L)).containsExactly(99L);   // unknown id: still a usable filter
    }
}