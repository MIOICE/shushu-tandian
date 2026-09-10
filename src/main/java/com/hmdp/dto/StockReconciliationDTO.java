package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class StockReconciliationDTO {
    private Long voucherId;
    private Integer databaseStock;
    private Integer redisStock;
    private Long pendingMessages;
    private Integer stockDifference;
    private String status;
    private LocalDateTime checkedAt;
}
