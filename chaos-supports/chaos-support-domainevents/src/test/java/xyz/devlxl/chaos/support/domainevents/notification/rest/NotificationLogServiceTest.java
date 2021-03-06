package xyz.devlxl.chaos.support.domainevents.notification.rest;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import xyz.devlxl.chaos.base.test.BeforeAfterClassNonStatic;
import xyz.devlxl.chaos.base.test.BeforeAfterClassNonStaticSpringRunner;
import xyz.devlxl.chaos.support.domainevents.AbstractJpaDomainEvent;
import xyz.devlxl.chaos.support.domainevents.notification.Notification;
import xyz.devlxl.chaos.support.domainevents.store.JpaStoredDomainEvent;
import xyz.devlxl.chaos.support.domainevents.store.JpaStoredDomainEventRepository;

@RunWith(BeforeAfterClassNonStaticSpringRunner.class)
@SpringBootTest
public class NotificationLogServiceTest implements BeforeAfterClassNonStatic {
    @Autowired
    private NotificationLogService service;
    @Autowired
    private JpaStoredDomainEventRepository eventStoreRepo;
    @Autowired
    @Qualifier("objectMapperOfDomainEventsSupport")
    private ObjectMapper objectMapper;

    @Override
    public void setUpBeforeClass() throws Exception {
        eventStoreRepo.deleteAll();
        for (int i = 1; i <= 65; i++) {
            DummyEvent dummyEvent = new DummyEvent("dummy" + i * 100);
            JpaStoredDomainEvent dummyStoredEvent = new JpaStoredDomainEvent()
                .eventBody(objectMapper.writeValueAsString(dummyEvent))
                .occurredOn(dummyEvent.occurredOn())
                .className(dummyEvent.getClass().getName());

            eventStoreRepo.save(dummyStoredEvent);
            if (i == 3 || i == 23 || i == 24 || i == 64) {
                eventStoreRepo.deleteById((long)i);
            }
        }
    }

    @Override
    public void tearDownAfterClass() {
        eventStoreRepo.deleteAll();
    }

    @Test
    public final void testCurrentNotificationLog() {
        NotificationLog currentLog = service.currentNotificationLog();
        assertEquals(currentLog.getPrevious().get(), "41,60");
        assertEquals(currentLog.getSelf(), "61,80");
        assertFalse(currentLog.getNext().isPresent());
        assertEquals(currentLog.getNotifications().size(), 4);
        long[] ids = new long[] {61, 62, 63, 65};
        for (int i = 0; i < ids.length; i++) {
            Notification notification = currentLog.getNotifications().get(i);
            assertTrue(notification.getNotificationId() == ids[i]);
            assertEquals(notification.getTypeName(), DummyEvent.class.getName());
            assertThat(notification.getEvent(), instanceOf(DummyEvent.class));
            assertEquals(((DummyEvent)notification.getEvent()).getDummyField(), "dummy" + ids[i] * 100);
        }
        assertFalse(currentLog.getArchived());
    }

    @Test
    public final void testCurrentNotificationLog_divisible() throws JsonProcessingException {
        for (int i = 66; i <= 80; i++) {
            DummyEvent dummyEvent = new DummyEvent("dummy" + i * 100);
            JpaStoredDomainEvent dummyStoredEvent = new JpaStoredDomainEvent()
                .eventBody(objectMapper.writeValueAsString(dummyEvent))
                .occurredOn(dummyEvent.occurredOn())
                .className(dummyEvent.getClass().getName());

            eventStoreRepo.save(dummyStoredEvent);
        }

        NotificationLog currentLog = service.currentNotificationLog();
        assertEquals(currentLog.getPrevious().get(), "41,60");
        assertEquals(currentLog.getSelf(), "61,80");
        assertFalse(currentLog.getNext().isPresent());
        assertFalse(currentLog.getArchived());

        for (int i = 66; i <= 80; i++) {
            eventStoreRepo.deleteById((long)i);
        }
    }

    @Test
    public final void testEventNotificationLog_1_20() {
        NotificationLog currentLog = service.notificationLog("1,20");
        assertFalse(currentLog.getPrevious().isPresent());
        assertEquals(currentLog.getSelf(), "1,20");
        assertEquals(currentLog.getNext().get(), "21,40");
        assertEquals(currentLog.getNotifications().size(), 19);
        long[] idsStarting = new long[] {1, 2, 4, 5};
        for (int i = 0; i < idsStarting.length; i++) {
            Notification notification = currentLog.getNotifications().get(i);
            assertTrue(notification.getNotificationId() == idsStarting[i]);
            assertEquals(notification.getTypeName(), DummyEvent.class.getName());
            assertThat(notification.getEvent(), instanceOf(DummyEvent.class));
            assertEquals(((DummyEvent)notification.getEvent()).getDummyField(), "dummy" + idsStarting[i] * 100);
        }
        assertTrue(currentLog.getArchived());
    }

    @Test
    public final void testEventNotificationLog_21_40() {
        NotificationLog currentLog = service.notificationLog("21,40");
        assertEquals(currentLog.getPrevious().get(), "1,20");
        assertEquals(currentLog.getSelf(), "21,40");
        assertEquals(currentLog.getNext().get(), "41,60");
        assertEquals(currentLog.getNotifications().size(), 18);
        long[] idsStarting = new long[] {21, 22, 25, 26};
        for (int i = 0; i < idsStarting.length; i++) {
            Notification notification = currentLog.getNotifications().get(i);
            assertTrue(notification.getNotificationId() == idsStarting[i]);
            assertEquals(notification.getTypeName(), DummyEvent.class.getName());
            assertThat(notification.getEvent(), instanceOf(DummyEvent.class));
            assertEquals(((DummyEvent)notification.getEvent()).getDummyField(), "dummy" + idsStarting[i] * 100);
        }
        assertTrue(currentLog.getArchived());
    }

    @Test
    public final void testEventNotificationLog_61_80() {
        NotificationLog currentLog = service.notificationLog("61,80");
        assertEquals(currentLog.getPrevious().get(), "41,60");
        assertEquals(currentLog.getSelf(), "61,80");
        assertFalse(currentLog.getNext().isPresent());
        assertEquals(currentLog.getNotifications().size(), 4);
        long[] ids = new long[] {61, 62, 63, 65};
        for (int i = 0; i < ids.length; i++) {
            Notification notification = currentLog.getNotifications().get(i);
            assertTrue(notification.getNotificationId() == ids[i]);
            assertEquals(notification.getTypeName(), DummyEvent.class.getName());
            assertThat(notification.getEvent(), instanceOf(DummyEvent.class));
            assertEquals(((DummyEvent)notification.getEvent()).getDummyField(), "dummy" + ids[i] * 100);
        }
        assertFalse(currentLog.getArchived());
    }

    @AllArgsConstructor
    @Getter
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    public static class DummyEvent extends AbstractJpaDomainEvent {
        private String dummyField;
    }
}
