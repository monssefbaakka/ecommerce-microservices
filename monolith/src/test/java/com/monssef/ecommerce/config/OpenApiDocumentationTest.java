package com.monssef.ecommerce.config;

import com.monssef.ecommerce.cart.CartRepository;
import com.monssef.ecommerce.category.CategoryRepository;
import com.monssef.ecommerce.order.OrderRepository;
import com.monssef.ecommerce.product.ProductRepository;
import com.monssef.ecommerce.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full application context (controllers, services, mappers, security, springdoc) without a database:
// the JPA auto-configuration is left out and the repositories are mocks.
// No DataSource means no "db" health indicator, which the readiness group lists: skip that check here
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
        "management.endpoint.health.validate-group-membership=false"
})
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CategoryRepository categoryRepository;

    @MockitoBean
    private ProductRepository productRepository;

    @MockitoBean
    private CartRepository cartRepository;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private EntityManager entityManager;

    @Test
    void apiDocs_shouldBePublicAndListAllEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/categories/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get").exists())
                .andExpect(jsonPath("$.paths['/api/cart'].get").exists())
                .andExpect(jsonPath("$.paths['/api/orders'].post").exists());
    }

    @Test
    void apiDocs_shouldDeclareBearerAuthOnProtectedEndpointsOnly() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/orders'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/products'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/products'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist());
    }

    @Test
    void swaggerUi_shouldBePublic() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
    }

    @Test
    void apiDocs_shouldDescribeTheApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.info.title").value("E-commerce API"))
                .andExpect(jsonPath("$.info.version").value("0.0.1"))
                .andExpect(jsonPath("$.info.description").value(containsString("token JWT")))
                .andExpect(jsonPath("$.info.license.name").value("MIT"));
    }

    @Test
    void apiDocs_shouldDescribeErrorsWithErrorResponse() throws Exception {
        String errorRef = "#/components/schemas/ErrorResponse";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.status").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.errors.items.$ref")
                        .value("#/components/schemas/FieldError"))
                .andExpect(jsonPath("$.paths['/api/products'].post.responses['400'].content['application/json'].schema.$ref")
                        .value(errorRef))
                .andExpect(jsonPath("$.paths['/api/orders/{id}'].get.responses['404'].content['application/json'].schema.$ref")
                        .value(errorRef))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['401'].content['application/json'].schema.$ref")
                        .value(errorRef))
                .andExpect(jsonPath("$.paths['/api/products/{id}'].delete.responses['204'].content").doesNotExist());
    }

    @Test
    void apiDocs_shouldListCategoriesAsAnArray() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/categories'].get.responses['200'].content['application/json'].schema.type")
                        .value("array"))
                .andExpect(jsonPath("$.paths['/api/categories'].get.responses['200'].content['application/json'].schema.items.$ref")
                        .value("#/components/schemas/CategoryDTO"));
    }

    @Test
    void apiDocs_shouldDescribeProductsPage() throws Exception {
        String products = "$.paths['/api/products'].get";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(products + ".parameters[*].name").value(contains("name", "categoryId", "page", "size", "sort")))
                .andExpect(jsonPath(products + ".parameters[?(@.name == 'size')].schema.default").value(contains(10)))
                .andExpect(jsonPath(products + ".responses['200'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/PagedModelProductDTO"))
                .andExpect(jsonPath("$.components.schemas.PagedModelProductDTO.properties.content.items.$ref")
                        .value("#/components/schemas/ProductDTO"))
                .andExpect(jsonPath("$.components.schemas.PagedModelProductDTO.properties.page.$ref")
                        .value("#/components/schemas/PageMetadata"));
    }

    @Test
    void apiDocs_shouldDescribeOrdersPageNewestFirst() throws Exception {
        String orders = "$.paths['/api/orders'].get";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(orders + ".parameters[*].name").value(contains("page", "size", "sort")))
                .andExpect(jsonPath(orders + ".parameters[?(@.name == 'sort')].schema.default[*]")
                        .value(contains("createdAt,DESC", "id,DESC")))
                .andExpect(jsonPath(orders + ".responses['200'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/PagedModelOrderDTO"));
    }

    @Test
    void apiDocs_shouldDocumentStockConflictsOnTheCart() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/cart/items'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/cart/items/{productId}'].put.responses['409']").exists());
    }

    @Test
    void apiDocs_shouldDocumentStockConflictWhenOrdering() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/orders'].post.responses['409'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/ErrorResponse"));
    }

    @Test
    void apiDocs_shouldDescribeDtoFieldsWithExamples() throws Exception {
        String schemas = "$.components.schemas";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath(schemas + ".LoginRequest.properties.email.example").value("john@example.com"))
                .andExpect(jsonPath(schemas + ".LoginRequest.required").value(contains("email", "password")))
                .andExpect(jsonPath(schemas + ".ProductRequest.properties.price.example").value(999.99))
                .andExpect(jsonPath(schemas + ".ProductRequest.properties.price.minimum").value(0.01))
                .andExpect(jsonPath(schemas + ".OrderItemDTO.properties.productId.description")
                        .value(containsString("supprimé")))
                .andExpect(jsonPath(schemas + ".CartDTO.description").value("Panier de l'utilisateur connecté"))
                .andExpect(jsonPath(schemas + ".UserDTO.properties.password").doesNotExist());
    }
}
