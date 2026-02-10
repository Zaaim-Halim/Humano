package com.humano.service.hr;

import com.humano.domain.hr.Employee;
import com.humano.domain.hr.PerformanceReview;
import com.humano.repository.hr.EmployeeRepository;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.service.errors.EntityNotFoundException;
import com.humano.service.hr.dto.requests.CreatePerformanceReviewRequest;
import com.humano.service.hr.dto.requests.UpdatePerformanceReviewRequest;
import com.humano.service.hr.dto.responses.PerformanceReviewResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PerformanceReviewService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceReviewService.class);

    private final PerformanceReviewRepository performanceReviewRepository;
    private final EmployeeRepository employeeRepository;

    public PerformanceReviewService(PerformanceReviewRepository performanceReviewRepository, EmployeeRepository employeeRepository) {
        this.performanceReviewRepository = performanceReviewRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public PerformanceReviewResponse createPerformanceReview(CreatePerformanceReviewRequest request) {
        log.debug("Request to create PerformanceReview: {}", request);

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        Employee reviewer = employeeRepository
            .findById(request.reviewerId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.reviewerId()));

        PerformanceReview review = new PerformanceReview();
        review.setEmployee(employee);
        review.setReviewer(reviewer);
        review.setReviewDate(request.reviewDate());
        review.setComments(request.comments());
        review.setRating(request.rating());

        PerformanceReview savedReview = performanceReviewRepository.save(review);
        log.info("Created performance review with ID: {}", savedReview.getId());

        return mapToResponse(savedReview);
    }

    @Transactional
    public PerformanceReviewResponse updatePerformanceReview(UUID id, UpdatePerformanceReviewRequest request) {
        log.debug("Request to update PerformanceReview: {}", id);

        return performanceReviewRepository
            .findById(id)
            .map(review -> {
                if (request.reviewDate() != null) {
                    review.setReviewDate(request.reviewDate());
                }
                if (request.comments() != null) {
                    review.setComments(request.comments());
                }
                if (request.rating() != null) {
                    review.setRating(request.rating());
                }
                return mapToResponse(performanceReviewRepository.save(review));
            })
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", id));
    }

    @Transactional(readOnly = true)
    public PerformanceReviewResponse getPerformanceReviewById(UUID id) {
        log.debug("Request to get PerformanceReview by ID: {}", id);

        return performanceReviewRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", id));
    }

    @Transactional(readOnly = true)
    public Page<PerformanceReviewResponse> getAllPerformanceReviews(Pageable pageable) {
        log.debug("Request to get all PerformanceReviews");

        return performanceReviewRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<PerformanceReviewResponse> getPerformanceReviewsByEmployee(UUID employeeId, Pageable pageable) {
        log.debug("Request to get PerformanceReviews by Employee: {}", employeeId);

        return performanceReviewRepository.findByEmployeeId(employeeId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<PerformanceReviewResponse> getPerformanceReviewsByReviewer(UUID reviewerId, Pageable pageable) {
        log.debug("Request to get PerformanceReviews by Reviewer: {}", reviewerId);

        return performanceReviewRepository.findByReviewerId(reviewerId, pageable).map(this::mapToResponse);
    }

    @Transactional
    public void deletePerformanceReview(UUID id) {
        log.debug("Request to delete PerformanceReview: {}", id);

        if (!performanceReviewRepository.existsById(id)) {
            throw EntityNotFoundException.create("PerformanceReview", id);
        }
        performanceReviewRepository.deleteById(id);
        log.info("Deleted performance review with ID: {}", id);
    }

    private PerformanceReviewResponse mapToResponse(PerformanceReview review) {
        String employeeName = review.getEmployee().getFirstName() + " " + review.getEmployee().getLastName();
        String reviewerName = review.getReviewer().getFirstName() + " " + review.getReviewer().getLastName();

        return new PerformanceReviewResponse(
            review.getId(),
            review.getEmployee().getId(),
            employeeName,
            review.getReviewer().getId(),
            reviewerName,
            review.getReviewDate(),
            review.getComments(),
            review.getRating(),
            review.getCreatedBy(),
            review.getCreatedDate(),
            review.getLastModifiedBy(),
            review.getLastModifiedDate()
        );
    }
}
