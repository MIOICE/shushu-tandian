package com.hmdp.dto;

import lombok.Data;

@Data
public class StudentVerificationReviewDTO {
    private Boolean approved;
    private String rejectReason;
}
