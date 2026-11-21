package com.locohiring.service.controllers;

import com.locohiring.api.EventsApi;
import com.locohiring.api.model.Event;
import com.locohiring.service.security.Caller;
import com.locohiring.service.services.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EventsController implements EventsApi {

    private final EventService eventService;

    public EventsController(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    @PreAuthorize(Caller.ADMIN)
    public ResponseEntity<List<Event>> listEvents(Integer limit) {
        return ResponseEntity.ok(eventService.listLatest(limit == null ? 200 : limit));
    }
}
