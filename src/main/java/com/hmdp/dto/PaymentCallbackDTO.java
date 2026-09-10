package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentCallbackDTO {
    private Long orderId;
    private String paymentNo;
    private LocalDateTime paidAt;
}
