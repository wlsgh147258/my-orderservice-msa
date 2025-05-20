package com.playdata.orderingservice.common.dto;

import com.playdata.orderingservice.ordering.entity.OrderStatus;
import lombok.*;

@Getter @Setter @ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderingLisResDto {
    // 하나의 주문에 대한 내용
    private Long id;
    private String userEmail;
    private OrderStatus orderStatus;

    // 주문 상세 내용
    public static class OrderDetailResDto {
        private Long Id;
        private String productName;
        private int count;

    }
}
