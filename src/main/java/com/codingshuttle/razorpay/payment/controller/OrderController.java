package com.codingshuttle.razorpay.payment.controller;

import com.codingshuttle.razorpay.payment.dto.request.CreateOrderRequest;
import com.codingshuttle.razorpay.payment.dto.response.OrderResponse;
import com.codingshuttle.razorpay.payment.dto.response.PaymentResponse;
import com.codingshuttle.razorpay.payment.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/orders")
public class OrderController {

    private final OrderService orderService;
    UUID merchantId= UUID.fromString("90d5b401-ab84-463f-a825-60c5f078c635"); // For Testing now , later will add Merchant Context

    @PostMapping
    public ResponseEntity<OrderResponse> create (@Valid @RequestBody CreateOrderRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.create(merchantId,request));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getById(@PathVariable UUID orderId)
    {
        return ResponseEntity.ok(orderService.getById(merchantId,orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancel(@PathVariable UUID orderId)
    {
        return ResponseEntity.ok(orderService.cancel(merchantId,orderId));
    }

    @GetMapping("/{orderId}/payments")
    public ResponseEntity<List<PaymentResponse>> listPayments(@PathVariable UUID orderId)
    {
        return ResponseEntity.ok(orderService.listPayments(merchantId,orderId));
    }

}
