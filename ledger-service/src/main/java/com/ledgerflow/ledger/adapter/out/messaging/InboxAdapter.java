package com.ledgerflow.ledger.adapter.out.messaging;

import com.ledgerflow.ledger.application.port.out.MessageInbox;
import com.ledgerflow.messaging.inbox.Inbox;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class InboxAdapter implements MessageInbox {

    private final Inbox inbox;

    InboxAdapter(Inbox inbox) {
        this.inbox = inbox;
    }

    @Override
    public boolean firstDelivery(UUID messageId) {
        return inbox.firstDelivery(messageId);
    }
}
