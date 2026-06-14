package com.loantrans.egs;

import com.loantrans.model.DomainEvent;
import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface EgsActivities {
    void publish(DomainEvent event);
}
