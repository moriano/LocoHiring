package com.locohiring.service.services;

import com.locohiring.api.model.Funnel;
import com.locohiring.api.model.FunnelCreate;
import com.locohiring.service.ApiException;
import com.locohiring.service.dao.EventDao;
import com.locohiring.service.dao.FunnelDao;
import com.locohiring.service.dao.ReferenceDataDao;
import com.locohiring.service.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
public class FunnelService {

    private final FunnelDao funnelDao;
    private final ReferenceDataDao referenceDataDao;
    private final EventDao eventDao;

    public FunnelService(FunnelDao funnelDao, ReferenceDataDao referenceDataDao, EventDao eventDao) {
        this.funnelDao = funnelDao;
        this.referenceDataDao = referenceDataDao;
        this.eventDao = eventDao;
    }

    public List<Funnel> list() {
        return funnelDao.findAll();
    }

    public Funnel get(long id) {
        return funnelDao.findById(id).orElseThrow(() -> ApiException.notFound("Funnel " + id));
    }

    @Transactional
    public Funnel create(AuthUser user, FunnelCreate request) {
        if (!user.isRecruiter()) {
            throw ApiException.forbidden("Funnels are owned by recruiters");
        }
        if (funnelDao.nameExists(request.getName())) {
            throw ApiException.conflict("Funnel '" + request.getName() + "' already exists");
        }
        List<Long> typeIds = request.getInterviewTypeIds();
        if (referenceDataDao.findInterviewTypes(typeIds).size() != new HashSet<>(typeIds).size()) {
            throw ApiException.badRequest("Unknown interview type in " + typeIds);
        }
        long id = funnelDao.create(request.getName(), request.getDescription(), user.id(), typeIds);
        eventDao.record(user.email(), id, null, "Created funnel '" + request.getName() + "'");
        return get(id);
    }
}
