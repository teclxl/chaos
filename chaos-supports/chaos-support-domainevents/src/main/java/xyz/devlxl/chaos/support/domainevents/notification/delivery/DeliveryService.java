package xyz.devlxl.chaos.support.domainevents.notification.delivery;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.binding.BinderAwareChannelResolver;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import xyz.devlxl.chaos.support.domain.StoredDomainEvent;
import xyz.devlxl.chaos.support.domainevents.notification.Notification;
import xyz.devlxl.chaos.support.domainevents.store.JpaStoredDomainEvent;
import xyz.devlxl.chaos.support.domainevents.store.JpaStoredDomainEventRepository;

/**
 * The application service about delivering the notification of the stored domain events or the command domain event
 * 
 * @author Liu Xiaolei
 * @date 2018/09/11
 */
@Service
@EnableConfigurationProperties(DeliveryProperties.class)
@EnableBinding
@Slf4j
public class DeliveryService {

    @Setter(onMethod_ = @Autowired)
    private DeliveryProperties deliveryProperties;

    @Setter(onMethod_ = @Autowired)
    private JpaStoredDomainEventRepository jpaStoredDomainEventRepository;

    @Setter(onMethod_ = @Autowired)
    private DeliveryTrackerRepository deliveryTrackerRepository;

    @Setter(onMethod_ = @Autowired)
    private BinderAwareChannelResolver binderAwareChannelResolver;

    @Setter(onMethod_ = {@Qualifier("objectMapperOfDomainEventsSupport"), @Autowired})
    private ObjectMapper objectMapper;

    @Transactional
    public void deliverUndeliveredStroedEvent() {
        DeliveryTracker deliveryTracker = deliveryTracker();
        List<JpaStoredDomainEvent> undeliveredAndSortedEvents
            = listUndeliveredSmallToLarge(deliveryTracker.mostRecentDeliveredEventId());

        Long maxEventIdJustDelivered = null;
        // Deliver events in order until all events has been delivered, or any event cannot be delivered.
        for (JpaStoredDomainEvent event : undeliveredAndSortedEvents) {
            if (deliverAnStoredEvent(event)) {
                maxEventIdJustDelivered = event.eventId();
            } else {
                break;
            }
        }

        if (maxEventIdJustDelivered != null) {
            trackMostRecentDeliveredEvent(deliveryTracker, maxEventIdJustDelivered);
        }
    }

    public boolean deliveryCommondEvent(String type, Map<String, Object> parameters, Date occurredOn,
        Long notificationId) {
        try {
            Notification notification
                = Notification.fromCommandOriginalInfo(type, parameters, occurredOn, notificationId);
            Message<String> message = MessageBuilder.withPayload(objectMapper.writeValueAsString(notification))
                .setHeader(deliveryProperties.getHeaderKey().getEventType(), notification.getTypeName())
                .setHeader(deliveryProperties.getHeaderKey().getNotificationId(),
                    String.valueOf(notification.getNotificationId()))
                .setHeader(deliveryProperties.getHeaderKey().getOccurredOn(),
                    String.valueOf(notification.getOccurredOn().getTime()))
                .build();

            return binderAwareChannelResolver.resolveDestination(notification.getTypeName()).send(message, 500);
        } catch (Exception e) {
            log.warn("Exception occurs when delivery a command notification!", e);
            return false;
        }
    }

    protected DeliveryTracker deliveryTracker() {
        return deliveryTrackerRepository.findById(DeliveryTracker.ID).orElse(
            new DeliveryTracker()
                .mostRecentDeliveredEventId(DeliveryTracker.MIN_DELIVERED_EVENT_ID));

    }

    protected void trackMostRecentDeliveredEvent(DeliveryTracker deliveryTracker, Long mostRecentDeliveredEventId) {
        deliveryTrackerRepository.save(deliveryTracker.mostRecentDeliveredEventId(mostRecentDeliveredEventId));
    }

    protected List<JpaStoredDomainEvent> listUndeliveredSmallToLarge(Long mostRecentDeliveredEventId) {
        return jpaStoredDomainEventRepository
            .findAllByEventIdGreaterThan(mostRecentDeliveredEventId, Sort.by(Order.asc("eventId")));
    }

    protected boolean deliverAnStoredEvent(StoredDomainEvent event) {
        Notification notification = Notification.fromStoredEvent(objectMapper, event);
        String payload = null;
        try {
            payload = objectMapper.writeValueAsString(notification);
        } catch (Exception e) {
            log.warn("Exception occurs when serializing the notification of domain event!", e);
            return false;
        }
        Message<String> message = MessageBuilder.withPayload(payload)
            .setHeader(deliveryProperties.getHeaderKey().getEventType(), notification.getTypeName())
            .setHeader(deliveryProperties.getHeaderKey().getNotificationId(),
                String.valueOf(notification.getNotificationId()))
            .setHeader(deliveryProperties.getHeaderKey().getOccurredOn(),
                String.valueOf(notification.getOccurredOn().getTime()))
            .build();
        try {
            return wrappingSend(binderAwareChannelResolver.resolveDestination(notification.getTypeName()), message);
        } catch (Exception e) {
            log.warn("Exception occurs when delivering the domain event!", e);
            return false;
        }
    }

    /**
     * Wrapping method
     * <p>
     * Just to mock a send failure during unit testing. It is difficult to mock MessageChannel.
     * <p>
     * TODO It can't use <code>@SpyBean</code>(but can use <code>@MockBean</code>) for the current class because of
     * unknown reason. Therefore, I temporarily use a special way to implement the mock, but this special way is a
     * violation of the coding principle
     * 
     * @param channel
     * @param message
     * @return
     */
    protected boolean wrappingSend(MessageChannel channel, Message<String> message) {
        boolean isMock = false;
        if (this.isMockSend) {
            if (this.mockSendOnlyIds.isPresent()) {
                isMock = LongStream.of(this.mockSendOnlyIds.get()).anyMatch((oneIdToMock) -> {
                    return oneIdToMock == Long
                        .parseLong(
                            message.getHeaders().get(deliveryProperties.getHeaderKey().getNotificationId()).toString());
                });
            } else {
                isMock = true;
            }
        }
        if (isMock) {
            switch (this.mockSendType) {
                case 1:
                    return false;
                case 2:
                    throw this.mockSendThrown;
                default:
                    throw new RuntimeException();
            }
        } else {
            return channel.send(message, 500);
        }
    }

    private boolean isMockSend = false;
    private int mockSendType = 0;
    private RuntimeException mockSendThrown = null;
    private Optional<long[]> mockSendOnlyIds = Optional.empty();

    protected void resetSpeicalWay() {
        this.isMockSend = false;
        this.mockSendType = 0;
    }

    protected void mockSendFalse(Optional<long[]> mockSendOnlyIds) {
        this.isMockSend = true;
        this.mockSendType = 1;
        this.mockSendOnlyIds = mockSendOnlyIds;
    }

    protected void mockSendThrown(RuntimeException thrown, Optional<long[]> mockSendOnlyIds) {
        this.isMockSend = true;
        this.mockSendType = 2;
        this.mockSendThrown = thrown;
        this.mockSendOnlyIds = mockSendOnlyIds;
    }
}
