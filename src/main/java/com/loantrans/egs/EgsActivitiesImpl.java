package com.loantrans.egs;

import com.loantrans.model.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EgsActivitiesImpl implements EgsActivities {
    private static final Logger log = LoggerFactory.getLogger(EgsActivitiesImpl.class);

    private final EventBus bus;

    public EgsActivitiesImpl(EventBus bus) {
        this.bus = bus;
    }

    @Override
    public void publish(DomainEvent event) {
        bus.publish(event);
        log.info("[EGS] published {}", event.getClass().getSimpleName());
    }
}
