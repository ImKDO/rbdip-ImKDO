package com.rbdip.bookstore.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Письмо-подтверждение клиенту. Срабатывает только после коммита транзакции,
 * поэтому при откате создания заказа письмо не уходит. Реальный почтовый
 * транспорт в учебном проекте не настроен - отправка эмулируется записью в лог.
 */
@Component
public class OrderConfirmationSender {

    private static final Logger LOG = LoggerFactory.getLogger(OrderConfirmationSender.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        if (LOG.isInfoEnabled()) {
            LOG.info("[email] Dear {}, your order #{} for {} has been placed.",
                    event.customerFullName(), event.orderId(), event.total());
        }
    }
}
