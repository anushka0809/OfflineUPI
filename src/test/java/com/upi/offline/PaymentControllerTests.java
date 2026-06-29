package com.upi.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.offline.dto.PaymentRequest;
import com.upi.offline.entity.Transaction;
import com.upi.offline.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void testSendPaymentAuthenticated() throws Exception {
        PaymentRequest request = new PaymentRequest();
        request.setSender("Alice");
        request.setReceiver("Bob");
        request.setAmount(100.0);

        Transaction mockTx = new Transaction();
        mockTx.setTransactionId("mock-uuid");
        mockTx.setStatus("PENDING");
        mockTx.setHopCount(2);

        when(paymentService.save(any(Transaction.class), anyString())).thenReturn(mockTx);

        mockMvc.perform(post("/api/payment/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.transactionId").value("mock-uuid"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void testSendPaymentUnauthenticatedReturns401() throws Exception {
        PaymentRequest request = new PaymentRequest();
        request.setSender("Alice");
        request.setReceiver("Bob");
        request.setAmount(100.0);

        mockMvc.perform(post("/api/payment/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    void testGetStatsForbiddenForUser() throws Exception {
        mockMvc.perform(get("/api/payment/stats"))
                .andExpect(status().isForbidden());
    }
}
