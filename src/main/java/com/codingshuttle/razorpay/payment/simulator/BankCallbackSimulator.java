package com.codingshuttle.razorpay.payment.simulator;

import com.codingshuttle.razorpay.common.enums.ChaosMode;
import com.codingshuttle.razorpay.common.enums.PaymentStatus;
import com.codingshuttle.razorpay.common.utils.RandomizerUtil;
import com.codingshuttle.razorpay.payment.entity.Payment;
import com.codingshuttle.razorpay.payment.repository.PaymentRepository;
import com.codingshuttle.razorpay.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;
import org.springframework.stereotype.Component;

import java.sql.Struct;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class BankCallbackSimulator {

    private final PaymentRepository paymentRepository;
    private final SimulatorConfig simulatorConfig;
    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${payment.simulator.poll-interval-ms:5000}")
    public void processCallbacks(){

        LocalDateTime globalWindow = LocalDateTime.now().minusSeconds(1);
        List<Payment> payments = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.AUTHORIZING,globalWindow);

        log.info("Simulating payments for {} payment ", payments.size());

        if(payments.isEmpty()) return;

        for(Payment payment:payments)
        {
            simulateCallback(payment);
        }
    }

    private void simulateCallback(Payment payment)
    {
        SimulatorConfig.MethodSimulatorConfig methodConfig = simulatorConfig.configFor(payment.getMethod());
        LocalDateTime dueAt=dueAt(payment,methodConfig);

        if(LocalDateTime.now().isBefore(dueAt))
        {
            return;
        }
        ChaosMode chaosMode=simulatorConfig.getChaosMode();

        switch (chaosMode)
        {
            case SUCCESS -> resolve(payment, true);
            case FAILURE -> resolve(payment, false);
            case TIMEOUT -> {
                log.debug("BankCallBack simulator : Payment Timed out");
            }
            case NORMAL,SLOW -> resolve(payment,shouldApprove(payment,methodConfig));
        }

    }

    private void resolve(Payment payment, boolean approve)
    {
        if(approve)
        {
            String bankRef="SIM_BANK_REF" + RandomizerUtil.randomBase64(8);
            paymentService.resolveAuthorization(payment.getId(),true, bankRef, null ,null);
        }
        else {
            paymentService.resolveAuthorization(payment.getId(),false,null,"SIM_BANK_ERROR_CODE","Simulated Bank Decline");
        }
    }

    private boolean shouldApprove(Payment payment, SimulatorConfig.MethodSimulatorConfig methodSimulatorConfig)
    {
        int bucket = Math.abs(payment.getId().hashCode())%100;
        return bucket < methodSimulatorConfig.getSuccessRate();
    }

    private LocalDateTime dueAt(Payment payment , SimulatorConfig.MethodSimulatorConfig methodConfig)
    {
        int range = methodConfig.getMaxDelaySeconds()-methodConfig.getMinDelaySeconds();
        int delaySeconds= methodConfig.getMinDelaySeconds() + Math.abs(payment.getId().hashCode())%(range +1);

        if(simulatorConfig.getChaosMode()== ChaosMode.SLOW)
        {
            delaySeconds*=2;
        }
        return payment.getCreatedAt().plusSeconds(delaySeconds);
    }
}
