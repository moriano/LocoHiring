package com.locohiring.service.services;

import com.locohiring.api.model.Event;
import com.locohiring.service.dao.EventDao;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventService {

    private final EventDao eventDao;

    public EventService(EventDao eventDao) {
        this.eventDao = eventDao;
    }

    public List<Event> listLatest(int limit) {
        return eventDao.findLatest(limit);
    }
}
