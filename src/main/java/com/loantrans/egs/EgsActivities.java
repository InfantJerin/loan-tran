package com.loantrans.egs;

import com.loantrans.egs.clearpar.ClearParOutboundMessage;
import com.loantrans.model.DomainEvent;
import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface EgsActivities {
    void publish(DomainEvent event);

    void publishToClearPar(ClearParOutboundMessage message);
}
