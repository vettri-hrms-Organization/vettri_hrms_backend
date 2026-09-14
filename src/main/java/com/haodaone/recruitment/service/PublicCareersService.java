package com.haodaone.recruitment.service;

import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.recruitment.dto.CandidateDTO;
import com.haodaone.recruitment.dto.PublicJobOpeningDTO;
import com.haodaone.recruitment.entity.JobOpening;
import com.haodaone.recruitment.repository.JobOpeningRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Backs the public, unauthenticated Careers page (GET /api/careers/**).
 * Deliberately kept separate from JobOpeningService/CandidateController's
 * admin surface so the "no auth required" boundary is a single obvious
 * file, not something to remember while editing the admin service.
 */
@Service
public class PublicCareersService {

    private final JobOpeningRepository jobOpeningRepository;
    private final CandidateService candidateService;

    public PublicCareersService(JobOpeningRepository jobOpeningRepository, CandidateService candidateService) {
        this.jobOpeningRepository = jobOpeningRepository;
        this.candidateService = candidateService;
    }

    /**
     * @Transactional(readOnly = true) is required: PublicJobOpeningDTO.from()
     * calls department.getName() and designation.getTitle(), both lazy
     * relations (see CandidateService.listAll()'s javadoc for the full
     * explanation). This is the public Careers page - the same bug here is
     * user-facing to job applicants, not just HR.
     */
    @Transactional(readOnly = true)
    public List<PublicJobOpeningDTO> listOpenJobs() {
        return jobOpeningRepository.findAllByStatusAndDeletedFalseOrderByPostedDateDesc("OPEN").stream()
                .map(PublicJobOpeningDTO::from)
                .toList();
    }

    /** Same lazy-relation issue as listOpenJobs() above. */
    @Transactional(readOnly = true)
    public PublicJobOpeningDTO getOpenJob(Long id) {
        JobOpening opening = jobOpeningRepository.findById(id)
                .filter(o -> !o.isDeleted() && "OPEN".equals(o.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException("This job opening is not available."));
        return PublicJobOpeningDTO.from(opening);
    }

    @Transactional
    public CandidateDTO apply(CandidateDTO.PublicApplicationRequest request, MultipartFile resume) {
        return candidateService.apply(request, resume);
    }
}
