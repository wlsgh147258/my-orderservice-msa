package com.playdata.orderingservice.ordering.service;

import com.playdata.orderingservice.client.ProductServiceClient;
import com.playdata.orderingservice.client.UserServiceClient;
import com.playdata.orderingservice.common.auth.TokenUserInfo;
import com.playdata.orderingservice.common.dto.CommonResDto;
import com.playdata.orderingservice.ordering.dto.OrderingListResDto;
import com.playdata.orderingservice.ordering.dto.OrderingSaveReqDto;
import com.playdata.orderingservice.ordering.dto.ProductResDto;
import com.playdata.orderingservice.ordering.dto.UserResDto;
import com.playdata.orderingservice.ordering.entity.OrderDetail;
import com.playdata.orderingservice.ordering.entity.Ordering;
import com.playdata.orderingservice.ordering.repository.OrderingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class OrderingService {

    private final OrderingRepository orderingRepository;
    private final RestTemplate restTemplate;

    // feign client 구현체 주입 받기
    private final UserServiceClient userServiceClient;
    private final ProductServiceClient productServiceClient;

    public Ordering createOrder(List<OrderingSaveReqDto> dtoList,
                                TokenUserInfo userInfo) {

        // Ordering 객체를 생성하기 위해 회원 정보를 얻어오자.
        // 우리가 가진 유일한 정보는 토큰 안에 들어있던 이메일 뿐입니다...
        // 이메일을 가지고 요청을 보내자 -> user-service
        CommonResDto<UserResDto> byEmail
                = userServiceClient.findByEmail(userInfo.getEmail());

        UserResDto userDto = byEmail.getResult();
        log.info("user-service로부터 전달받은 결과: {}", userDto);

        // Ordering(주문) 객체 생성
        Ordering ordering = Ordering.builder()
                .userId(userDto.getId())
                .orderDetails(new ArrayList<>()) // 아직 주문 상세 들어가기 전.
                .build();

        // 주문 상세 내역에 대한 처리를 반복해서 지정.
        for (OrderingSaveReqDto dto : dtoList) {

            // dto 안에 있는 상품 id를 이용해서 상품 정보 얻어오자.
            // product 객체를 조회하자 -> product-service에게 요청해야 함!
            CommonResDto<ProductResDto> byId
                    = productServiceClient.findById(dto.getProductId());
            ProductResDto prodResDto = byId.getResult();

            log.info("product-service로부터 받아온 결과: {}", prodResDto);
            int stockQuantity = prodResDto.getStockQuantity();

            // 재고 넉넉하게 있는지 확인
            int quantity = dto.getProductQuantity();
            if (stockQuantity < quantity) {
                throw new IllegalArgumentException("재고 부족!");
            }

            // 재고가 부족하지 않다면 재고 수량을 주문 수량만큼 빼주자
            // product-service에게 재고 수량이 변경되었다고 알려주자.
            // 상품 id와 변경되어야 할 수량을 함께 보내주자.
            prodResDto.setStockQuantity(stockQuantity - quantity);
            productServiceClient.updateQuantity(prodResDto);

            // 주문 상세 내역 엔터티 생성
            OrderDetail orderDetail = OrderDetail.builder()
                    .productId(dto.getProductId())
                    .ordering(ordering)
                    .quantity(quantity)
                    .build();

            // 주문 내역 리스트에 상세 내역을 add하기.
            // (cascadeType.PERSIST로 세팅했기 때문에 함께 INSERT가 진행될 것!)
            ordering.getOrderDetails().add(orderDetail);
        }

        return orderingRepository.save(ordering);
    }

    public List<OrderingListResDto> myOrder(final TokenUserInfo userInfo) {
        String email = userInfo.getEmail();

        // 이메일로는 주문 회원 정보를 알 수가 없음. (id로 되어 있으니까)
        CommonResDto<UserResDto> byEmail
                = userServiceClient.findByEmail(email);
        UserResDto userDto = byEmail.getResult();

        // 해당 사용자의 주문 내역 전부 가져오기.
        List<Ordering> orderingList
                = orderingRepository.findByUserId(userDto.getId());

        List<Long> productIdList = new ArrayList<>();
        // 주문 내역에서 모든 상품 ID를 추출한 후
        // product-service에게 상품 정보를 요청.

        /*
         OrderingListResDto -> OrderDetailDto(static 내부 클래스)
         {
            id: 주문번호,
            userEmail: 주문한 사람 이메일,
            orderStatus: 주문 상태
            orderDetails: [
                {
                    id: 주문상세번호,
                    productName: 상품명,
                    count: 수량
                },
                {
                    id: 주문상세번호,
                    productName: 상품명,
                    count: 수량
                },
                {
                    id: 주문상세번호,
                    productName: 상품명,
                    count: 수량
                }
                ...
            ]
         }
         */

//        for (Ordering ordering : orderingList) {
//            List<OrderDetail> orderDetailList = ordering.getOrderDetails();
//            for (OrderDetail orderDetail : orderDetailList) {
//                Long productId = orderDetail.getProductId();
//                if (!productIdList.contains(productId)) {
//                    productIdList.add(productId);
//                }
//            }
//        }

        List<Long> productIds = orderingList.stream() // 스트림 준비
                // flatMap: 하나의 주문 내역에서 상세 주문 내역 리스트를 꺼낸 후 하나의 스트림으로 평탄화
                /* flatMap의 동작 원리
                [
                    Ordering 1 -> [OrderDetail1, OrderDetail2]
                    Ordering 2 -> [OrderDetail3]
                    Ordering 3 -> [OrderDetai4, OrderDetail5, OrderDetail6]
                ]

                [OrderDetail1, OrderDetail2, OrderDetail3, OrderDetail4, OrderDetail5, OrderDetail6]
                 */

                .flatMap(ordering -> ordering.getOrderDetails().stream())
                .map(orderDetail -> orderDetail.getProductId())
                .distinct()
                .collect(Collectors.toList());

        // product-service에게 상품 정보를 달라고 요청해야 함.
        CommonResDto<List<ProductResDto>> products
                = productServiceClient.getProducts(productIds);
        List<ProductResDto> dtoList = products.getResult();

        // product-service에게 받아온 리스트를 필요로 하는 정보로만 맵으로 맵핑.
        Map<Long, String> productIdToNameMap = dtoList.stream()
                .collect(Collectors.toMap(
                        dto -> dto.getId(), // key
                        dto -> dto.getName() // value
                ));

        // Ordering 객체를 DTO로 변환하자. 주문 상세에 대한 변환도 따로 처리.
        return orderingList.stream()
                .map(ordering -> ordering.fromEntity(email, productIdToNameMap))
                .collect(Collectors.toList());
    }
}







