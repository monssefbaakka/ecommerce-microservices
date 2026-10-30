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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full application context (controllers, services, mappers, security, springdoc) without a database:
// the JPA auto-configuration is left out and the repositories are mocks
@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration")
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
}
