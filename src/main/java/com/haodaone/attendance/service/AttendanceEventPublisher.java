package com.haodaone.attendance.service;

import com.haodaone.attendance.dto.AttendanceRecordDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ex) {
            emitters.remove(emitter);
        }
        log.debug("New attendance stream subscriber. Active: {}", emitters.size());
        return emitter;
    }

    public void publish(AttendanceRecordDTO record) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("attendance").data(record));
            } catch (IOException | IllegalStateException ex) {
                emitter.complete();
                emitters.remove(emitter);
            }
        }
    }
}
