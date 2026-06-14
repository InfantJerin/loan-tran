package com.loantrans.egs;

import com.loantrans.model.DomainEvent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class InMemoryEventBus implements EventBus {
    private final BlockingQueue<DomainEvent> queue = new LinkedBlockingQueue<>();

    @Override
    public void publish(DomainEvent event) {
        queue.offer(event);
    }

    @Override
    public DomainEvent take() throws InterruptedException {
        return queue.take();
    }
}
