package com.playdata.orderingservice.ordering.service;

import com.playdata.orderingservice.common.auth.TokenUserInfo;
import com.playdata.orderingservice.common.dto.CommonResDto;
import com.playdata.orderingservice.ordering.dto.OrderingSaveReqDto;
import com.playdata.orderingservice.ordering.dto.ProductResDto;
import com.playdata.orderingservice.ordering.dto.UserResDto;
import com.playdata.orderingservice.ordering.entity.OrderDetail;
import com.playdata.orderingservice.ordering.entity.Ordering;
import com.playdata.orderingservice.ordering.repository.OrderingRepository;
import com.playdata.orderingservice.product.entity.Product;
import com.playdata.orderingservice.product.repository.ProductRepository;
import com.playdata.orderingservice.user.entity.User;
import com.playdata.orderingservice.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class OrderingService {

    private final OrderingRepository orderingRepository;
    private final RestTemplate restTemplate;

    // 유레카에 등록된 서비스명으로 요청을 보낼 수 있게 url 틀을 만들어 놓자.
    private final String USER_API = "http://user-service/";
    private final String PRODUCT_API = "http://product-service/";

    public Ordering createOrder(List<OrderingSaveReqDto> dtoList,
                                TokenUserInfo userInfo) {

        // Ordering 객체를 생성하기 위해 회원 정보를 얻어오자.
        // 우리가 가진 유일한 정보는 토큰 안에 들어있던 이메일 뿐입니다...
        // 이메일을 가지고 요청을 보내자 -> user-service
        ResponseEntity<CommonResDto> responseEntity = restTemplate.exchange(
                USER_API + "user/findByEmail?email=" + userInfo.getEmail(), // 요청 url
                HttpMethod.GET, // 요청 방식
                null, // 요청과 함께 전달할 데이터
                CommonResDto.class // 응답받을 데이터의 형태
        );
        CommonResDto commonDto = responseEntity.getBody();
        UserResDto userDto = (UserResDto) commonDto.getResult();


        // Ordering(주문) 객체 생성
        Ordering ordering = Ordering.builder()
                .userId(userDto.getId())
                .orderDetails(new ArrayList<>()) // 아직 주문 상세 들어가기 전.
                .build();

        // 주문 상세 내역에 대한 처리를 반복해서 지정.
        for (OrderingSaveReqDto dto : dtoList) {

            // dto 안에 있는 상품 id를 이용해서 상품 정보 얻어오자.
            // product 객체를 조회하자 -> product-service에게 요청해야 함!
            ResponseEntity<CommonResDto> prodResponse = restTemplate.exchange(
                    PRODUCT_API + "product/" + dto.getProductId(),
                    HttpMethod.GET,
                    null,
                    CommonResDto.class
            );
            CommonResDto commonResDto = prodResponse.getBody();
            ProductResDto productResDto = (ProductResDto) commonResDto.getResult();


            // 재고 넉넉하게 있는지 확인
            int quantity = dto.getProductQuantity();
            if (productResDto.getStockQuantity() < quantity) {
                throw new IllegalArgumentException("재고 부족!");
            }

            // 재고가 부족하지 않다면 재고 수량을 주문 수량만큼 빼주자
            // product-service에게 재고 수량이 변경되었다고 알려주자.
            // 상품 id와 변경되어야 할 수량을 함께 보내주자.

            // LinkedMultiValueMap은 키 하나에 여러개의 값을 리스트 형태로 저장 가능합니다.
            // 데이터 삽입 순서를 보장할 수 있습니다.
            // 웹 관련 작업에 적합합니다.
            LinkedMultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("productId", String.valueOf(dto.getProductId()));
            map.add("stockQuantity", String.valueOf(productResDto.getStockQuantity() - quantity));

            // RestTemplate으로 요청 보낼 때 요청 관련 필요한 데이터 및 헤더 정보를 세팅하는 객체.
            HttpEntity<Object> httpEntity = new HttpEntity<>(map);

            restTemplate.exchange(
                    PRODUCT_API + "product/updateQuantity",
                    HttpMethod.PATCH,
                    httpEntity,
                    CommonResDto.class
            );

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
}







