package com.monssef.paymentservice.payment;

import com.monssef.paymentservice.client.ServiceUnavailableException;
import com.monssef.paymentservice.order.OrderStatus;
import com.monssef.paymentservice.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Payment of an order placed in order-service (in memory here): set to PAID through its internal endpoint
class PaymentFlowIT extends IntegrationTest {

    private String user;
    private long orderId;

    @BeforeEach
    void setUp() {
        user = userToken("john@example.com");
        orderId = createOrder("john@example.com", "1999.98");
    }

    @Test
    void pay_shouldChargeTheOrderTotalAndSetItPaid() throws Exception {
        MvcResult result = mockMvc.perform(pay(user, orderId, CARD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.amount").value(1999.98))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.cardLast4").value("4242"))
                .andReturn();
        long paymentId = body(result).get("id").asLong();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).endsWith("/api/payments/" + paymentId);

        // order-service was told: GET /api/orders/{id} now shows PAID
        assertThat(statusOf(orderId)).isEqualTo("PAID");
    }

    @Test
    void pay_shouldNeverStoreTheFullCardNumber() throws Exception {
        mockMvc.perform(pay(user, orderId, CARD)).andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject("SELECT card_last4 FROM payments", String.class)).isEqualTo("4242");
        assertThat(jdbcTemplate.queryForList("SELECT * FROM payments").toString()).doesNotContain(CARD);
    }

    @Test
    void declinedCard_shouldKeepOrderPendingAndAllowAnotherTry() throws Exception {
        mockMvc.perform(pay(user, orderId, DECLINED_CARD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DECLINED"));
        assertThat(statusOf(orderId)).isEqualTo("PENDING");

        mockMvc.perform(pay(user, orderId, CARD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(statusOf(orderId)).isEqualTo("PAID");
    }

    @Test
    void pay_shouldReturn409_whenOrderAlreadyPaid() throws Exception {
        mockMvc.perform(pay(user, orderId, CARD)).andExpect(status().isCreated());

        mockMvc.perform(pay(user, orderId, CARD))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order %d is PAID, only a PENDING order can be paid"
                        .formatted(orderId)));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM payments", Integer.class)).isEqualTo(1);
    }

    @Test
    void pay_shouldReturn409_whenOrderCancelled() throws Exception {
        orders.setStatus(orderId, OrderStatus.CANCELLED);

        mockMvc.perform(pay(user, orderId, CARD))
                .andExpect(status().isConflict());
    }

    @Test
    void pay_shouldReturn404_whenOrderOfAnotherUser() throws Exception {
        String other = userToken("jane@example.com");

        mockMvc.perform(pay(other, orderId, CARD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order with id %d not found".formatted(orderId)));

        assertThat(statusOf(orderId)).isEqualTo("PENDING");
    }

    @Test
    void pay_shouldReturn404_whenOrderUnknownInOrderService() throws Exception {
        mockMvc.perform(pay(user, 99L, CARD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order with id 99 not found"));
    }

    @Test
    void pay_shouldReturn503AndSaveNothing_whenOrderServiceIsDown() throws Exception {
        // doThrow: when(orderClient.find(...)) would run the FakeOrderService answer, which throws a 404 for id 0
        doThrow(new ServiceUnavailableException("Order service"))
                .when(orderClient).find(anyLong());

        mockMvc.perform(pay(user, orderId, CARD))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Order service unavailable"));

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM payments", Integer.class)).isZero();
    }

    @Test
    void pay_shouldIgnoreAnAmountSentByTheClient() throws Exception {
        mockMvc.perform(auth(json(post("/api/payments"),
                        Map.of("orderId", orderId, "cardNumber", CARD, "amount", "0.01")), user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(1999.98));
    }

    @Test
    void pay_shouldReturn400_whenCardNumberInvalid() throws Exception {
        mockMvc.perform(pay(user, orderId, "1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cardNumber"));
    }

    @Test
    void history_shouldListOnlyOwnPaymentsNewestFirst() throws Exception {
        long first = paymentId(pay(user, orderId, DECLINED_CARD));
        long second = paymentId(pay(user, orderId, CARD));
        String other = userToken("jane@example.com");
        long otherPayment = paymentId(pay(other, createOrder("jane@example.com", "19.90"), CARD));

        mockMvc.perform(auth(get("/api/payments"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[1].id").value(first))
                .andExpect(jsonPath("$.page.totalElements").value(2));

        // someone else's payment looks like an unknown one
        mockMvc.perform(auth(get("/api/payments/{id}", otherPayment), user))
                .andExpect(status().isNotFound());
        mockMvc.perform(auth(get("/api/payments/{id}", otherPayment), other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(19.90));
    }

    private long paymentId(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isCreated()).andReturn();
        return body(result).get("id").asLong();
    }
}
