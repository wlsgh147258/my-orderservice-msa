package com.playdata.orderingservice.client;

import com.playdata.orderingservice.common.dto.CommonResDto;
import com.playdata.orderingservice.ordering.dto.ProductResDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "product-service")
public interface ProductServiceClient {

    @GetMapping("/product/{prodId}")
    CommonResDto<ProductResDto> findById(@PathVariable Long prodId);

    @PatchMapping("/product/updateQuantity")
    ResponseEntity<?> updateQuantity(@RequestBody ProductResDto productResDto);

}









