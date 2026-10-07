package com.haodaone.attendance.service;

import com.haodaone.attendance.dto.AttendanceRecordDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Same pattern proven in the standalone attendance POC: keeps track of
 * every browser tab watching live attendance and pushes new punches to
 * all of them the moment they're saved, via Server-Sent Events.
 */
@Service
public class AttendanceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AttendanceEventPublisher.class);
    private static final long EMITTER_TIMEOUT = 0L;

    private final List<Subscriber> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(Long companyId, boolean organizationScope, Set<Long> employeeIds) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        Subscriber subscriber = new Subscriber(emitter, companyId, organizationScope, Set.copyOf(employeeIds));
        emitters.add(subscriber);
        emitter.onCompletion(() -> emitters.remove(subscriber));
        emitter.onTimeout(() -> emitters.remove(subscriber));
        emitter.onError(ex -> emitters.remove(subscriber));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ex) {
            emitters.remove(subscriber);
        }
        log.debug("New attendance stream subscriber. Active: {}", emitters.size());
        return emitter;
    }

    public void publish(Long companyId, AttendanceRecordDTO record) {
        for (Subscriber subscriber : emitters) {
            if (!subscriber.companyId().equals(companyId)
                    || (!subscriber.organizationScope()
                    && (record.getEmployeeId() == null || !subscriber.employeeIds().contains(record.getEmployeeId())))) {
                continue;
            }
            try {
                subscriber.emitter().send(SseEmitter.event().name("attendance").data(record));
            } catch (IOException | IllegalStateException ex) {
                subscriber.emitter().complete();
                emitters.remove(subscriber);
            }
        }
    }

    private record Subscriber(SseEmitter emitter, Long companyId, boolean organizationScope, Set<Long> employeeIds) {}
}
