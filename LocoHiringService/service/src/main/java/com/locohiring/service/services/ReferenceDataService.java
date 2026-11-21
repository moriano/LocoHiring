package com.locohiring.service.services;

import com.locohiring.api.model.InterviewType;
import com.locohiring.api.model.InterviewTypeCreate;
import com.locohiring.api.model.JobFamily;
import com.locohiring.api.model.Position;
import com.locohiring.api.model.PositionCreate;
import com.locohiring.service.ApiException;
import com.locohiring.service.dao.EventDao;
import com.locohiring.service.dao.ReferenceDataDao;
import com.locohiring.service.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Job families, positions and interview types. */
@Service
public class ReferenceDataService {

    private final ReferenceDataDao dao;
    private final EventDao eventDao;

    public ReferenceDataService(ReferenceDataDao dao, EventDao eventDao) {
        this.dao = dao;
        this.eventDao = eventDao;
    }

    public List<JobFamily> listJobFamilies() {
        return dao.listJobFamilies();
    }

    @Transactional
    public JobFamily createJobFamily(AuthUser user, String name) {
        if (dao.jobFamilyNameExists(name)) {
            throw ApiException.conflict("Job family '" + name + "' already exists");
        }
        JobFamily created = dao.createJobFamily(name);
        eventDao.record(user.email(), null, null, "Created job family '" + name + "'");
        return created;
    }

    public List<Position> listPositions() {
        return dao.listPositions();
    }

    @Transactional
    public Position createPosition(AuthUser user, PositionCreate request) {
        if (!dao.jobFamilyExists(request.getJobFamilyId())) {
            throw ApiException.badRequest("Job family " + request.getJobFamilyId() + " does not exist");
        }
        long id = dao.createPosition(request.getName(), request.getDescription(), request.getLevel(),
                request.getJobFamilyId());
        eventDao.record(user.email(), null, null,
                "Created position '" + request.getName() + "' (level " + request.getLevel() + ")");
        return dao.findPosition(id).orElseThrow();
    }

    public List<InterviewType> listInterviewTypes() {
        return dao.listInterviewTypes();
    }

    @Transactional
    public InterviewType createInterviewType(AuthUser user, InterviewTypeCreate request) {
        if (dao.interviewTypeNameExists(request.getName())) {
            throw ApiException.conflict("Interview type '" + request.getName() + "' already exists");
        }
        InterviewType created = dao.createInterviewType(request.getName(), request.getDescription());
        eventDao.record(user.email(), null, null, "Created interview type '" + request.getName() + "'");
        return created;
    }
}
