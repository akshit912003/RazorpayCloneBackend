package com.codingshuttle.razorpay.payment.service.impl;

import com.codingshuttle.razorpay.common.enums.OrderStatus;
import com.codingshuttle.razorpay.common.exception.BusinessRuleViolationException;
import com.codingshuttle.razorpay.common.exception.DuplicateResourceException;
import com.codingshuttle.razorpay.common.exception.ResourceNotFoundException;
import com.codingshuttle.razorpay.payment.dto.request.CreateOrderRequest;
import com.codingshuttle.razorpay.payment.dto.response.OrderResponse;
import com.codingshuttle.razorpay.payment.dto.response.PaymentResponse;
import com.codingshuttle.razorpay.payment.entity.OrderRecord;
import com.codingshuttle.razorpay.payment.entity.Payment;
import com.codingshuttle.razorpay.payment.mapper.OrderMapper;
import com.codingshuttle.razorpay.payment.mapper.PaymentMapper;
import com.codingshuttle.razorpay.payment.repository.OrderRepository;
import com.codingshuttle.razorpay.payment.repository.PaymentRepository;
import com.codingshuttle.razorpay.payment.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;

    @Value("${payment.order.default-order-expiry-minutes:30}")
    private int defaultOrderExpiryMinutes;

    @Override
    @Transactional
    public OrderResponse create(UUID merchantId ,CreateOrderRequest request) {
         if(request.receipt()!=null && orderRepository.existsByMerchantIdAndReceipt(merchantId,request.receipt())){
             throw new DuplicateResourceException("ORDER_RECEIPT_DUPLICATE", "Order with receipt already exists: " + request.receipt());
         }

        OrderRecord orderRecord=OrderRecord.builder()
                .receipt(request.receipt())
                .amount(request.amount())
                .notes(request.notes())
                .merchantId(merchantId)
                .orderStatus(OrderStatus.CREATED)
                .expiresAt(request.expiresAt()!=null ? request.expiresAt():
                        LocalDateTime.now().plusMinutes(defaultOrderExpiryMinutes))
                .build();

         orderRecord=orderRepository.save(orderRecord);

         //TODO:: Publish Kafka Event

         return orderMapper.toResponse(orderRecord);

    }

    @Override
    public OrderResponse getById(UUID merchantId, UUID orderId) {
         OrderRecord orderRecord= orderRepository.findByIdAndMerchantId(orderId,merchantId)
                 .orElseThrow(()->new ResourceNotFoundException("Order",orderId));
        return orderMapper.toResponse(orderRecord);
    }

    @Override
    @Transactional
    public OrderResponse cancel(UUID merchantId, UUID orderId) {
        OrderRecord orderRecord= orderRepository.findByIdAndMerchantId(orderId,merchantId)
                .orElseThrow(()->new ResourceNotFoundException("Order",orderId));

        if(orderRecord.getOrderStatus()==OrderStatus.CANCELLED || orderRecord.getOrderStatus()==OrderStatus.PAID)
        {
            throw new BusinessRuleViolationException("ORDER_CANNOT_CANCEL",
                    "Cannot cancel order with status: "+orderRecord.getOrderStatus().name());
        }

        orderRecord.setOrderStatus(OrderStatus.CANCELLED);
        orderRecord=orderRepository.save(orderRecord);

        //TODO:: Publish KAFKA Event

        return orderMapper.toResponse(orderRecord);
    }

    @Override
    public List<PaymentResponse> listPayments(UUID merchantId, UUID orderId) {
        OrderRecord orderRecord= orderRepository.findByIdAndMerchantId(orderId,merchantId)
                .orElseThrow(()->new ResourceNotFoundException("Order",orderId));

        List<Payment> paymentList=paymentRepository.findByOrder_Id(orderRecord);

        return paymentMapper.toResponseList(paymentList);

    }
}
