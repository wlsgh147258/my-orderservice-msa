package com.playdata.orderingservice.ordering.service;

import com.playdata.orderingservice.ordering.entity.PendingOrder;
import com.playdata.orderingservice.ordering.repository.PendingOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// 보류된 주문을 나중에 재처리 해주는 로직을 가진 클래스
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingOrderProcessor {

    private final PendingOrderRepository pendingOrderRepository;

    // 어떤 작업에 대해서 지정된 기간 혹은 특정 날짜나 시간에 동작하도록 예약하는 기능.
    @Scheduled(fixedDelay = 300000) // 5분마다 실행
//    @Scheduled(cron = "0 * * * * *") "초 분 시 일 월 요일"
    public void processPendingOrder() {
        List<PendingOrder> pendingOrders
                = pendingOrderRepository.findAll();

        // 보류된 주문에 대한 요청 처리를 이곳에서 다시 처리해 보자.
    }

}









