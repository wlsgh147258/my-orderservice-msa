package com.playdata.orderingservice.ordering.entity;

import com.playdata.orderingservice.ordering.dto.OrderingListResDto;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder

@Entity
public class Ordering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    */

    // 프로젝트가 나눠지면서 Ordering 쪽에서는 User 엔터티에 대한 정보를 확인할 수 없다.
    // 클라이언트 단에서 넘어오는 정보만 저장할 수 있다.
    @JoinColumn
    private Long userId;

    private OrderStatus orderStatus;

    @OneToMany(mappedBy = "ordering", cascade = CascadeType.PERSIST)
    private List<OrderDetail> orderDetails;

    // dto 변환 메서드
    public OrderingListResDto fromEntity(
            String email, Map<Long, String> productIdToNameMap
    ) {
        List<OrderDetail> orderDetailList = this.getOrderDetails();
        List<OrderingListResDto.OrderDetailDto> orderDetailDtos
                = new ArrayList<>();

        // OrderDetail 엔터티를 OrderDetailDto로 변환해야 합니다.
        for (OrderDetail orderDetail : orderDetailList) {
            OrderingListResDto.OrderDetailDto orderDetailDto
                    = orderDetail.fromEntity(productIdToNameMap);
            orderDetailDtos.add(orderDetailDto);
        }


        return OrderingListResDto.builder()
                .id(id)
                .userEmail(email)
                .orderStatus(orderStatus)
                .orderDetails(orderDetailDtos)
                .build();
    }

}










