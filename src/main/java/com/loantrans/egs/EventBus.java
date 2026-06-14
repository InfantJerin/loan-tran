package com.loantrans.egs;

import com.loantrans.model.DomainEvent;

public interface EventBus {
    void publish(DomainEvent event);

    DomainEvent take() throws InterruptedException;
}
