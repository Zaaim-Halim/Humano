package com.humano.service.hr;

import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreatePerformanceReviewRequest;
import com.humano.dto.hr.requests.PerformanceReviewSearchRequest;
import com.humano.dto.hr.requests.UpdatePerformanceReviewRequest;
import com.humano.dto.hr.responses.PerformanceReviewResponse;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.hr.specification.PerformanceReviewSpecification;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.security.SecurityUtils;
import com.humano.service.errors.BadRequestAlertException;
import com.humano.service.errors.EntityNotFoundException;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performance reviews as records: HR writes reviews outside a cycle directly, and reads every review.
 * Reviews that belong to a running cycle are written through
 * {@link com.humano.service.hr.workflow.PerformanceReviewCycleService} and can't be edited or deleted here.
 */
@Service
public class PerformanceReviewService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceReviewService.class);

    private final PerformanceReviewRepository performanceReviewRepository;
    private final EmployeeRepository employeeRepository;

    public PerformanceReviewService(PerformanceReviewRepository performanceReviewRepository, EmployeeRepository employeeRepository) {
        this.performanceReviewRepository = performanceReviewRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional("tenantTransactionManager")
    public PerformanceReviewResponse createPerformanceReview(CreatePerformanceReviewRequest request) {
        log.debug("Request to create PerformanceReview: {}", request);

        Employee employee = employeeRepository
            .findById(request.employeeId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.employeeId()));

        Employee reviewer = employeeRepository
            .findById(request.reviewerId())
            .orElseThrow(() -> EntityNotFoundException.create("Employee", request.reviewerId()));

        if (employee.getId().equals(reviewer.getId())) {
            throw new BadRequestAlertException("An employee can't review themselves", "performanceReview", "selfreview");
        }
        requireNotOwnReview(employee.getId());

        PerformanceReview review = new PerformanceReview();
        review.setEmployee(employee);
        review.setReviewer(reviewer);
        review.setReviewDate(request.reviewDate());
        review.setComments(request.comments());
        review.setRating(request.rating());
        review.setStatus(PerformanceReviewStatus.SHARED);

        PerformanceReview savedReview = performanceReviewRepository.save(review);
        log.info("Created performance review with ID: {}", savedReview.getId());

        return mapToResponse(savedReview);
    }

    @Transactional("tenantTransactionManager")
    public PerformanceReviewResponse updatePerformanceReview(UUID id, UpdatePerformanceReviewRequest request) {
        log.debug("Request to update PerformanceReview: {}", id);

        return performanceReviewRepository
            .findById(id)
            .map(review -> {
                requireOutsideCycle(review);
                requireNotOwnReview(review.getEmployee().getId());
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

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public PerformanceReviewResponse getPerformanceReviewById(UUID id) {
        log.debug("Request to get PerformanceReview by ID: {}", id);

        return performanceReviewRepository
            .findById(id)
            .map(this::mapToResponse)
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", id));
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<PerformanceReviewResponse> getAllPerformanceReviews(Pageable pageable) {
        log.debug("Request to get all PerformanceReviews");

        return performanceReviewRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<PerformanceReviewResponse> getPerformanceReviewsByEmployee(UUID employeeId, Pageable pageable) {
        log.debug("Request to get PerformanceReviews by Employee: {}", employeeId);

        return performanceReviewRepository.findByEmployeeId(employeeId, pageable).map(this::mapToResponse);
    }

    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<PerformanceReviewResponse> getPerformanceReviewsByReviewer(UUID reviewerId, Pageable pageable) {
        log.debug("Request to get PerformanceReviews by Reviewer: {}", reviewerId);

        return performanceReviewRepository.findByReviewerId(reviewerId, pageable).map(this::mapToResponse);
    }

    @Transactional("tenantTransactionManager")
    public void deletePerformanceReview(UUID id) {
        log.debug("Request to delete PerformanceReview: {}", id);

        PerformanceReview review = performanceReviewRepository
            .findById(id)
            .orElseThrow(() -> EntityNotFoundException.create("PerformanceReview", id));
        requireOutsideCycle(review);
        requireNotOwnReview(review.getEmployee().getId());
        performanceReviewRepository.delete(review);
        log.info("Deleted performance review with ID: {}", id);
    }

    /**
     * The reviews an employee may read about themselves — written outside a cycle or shared with
     * them — as the employee sees them.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<PerformanceReviewResponse> getReviewsSharedWith(UUID employeeId, Pageable pageable) {
        Specification<PerformanceReview> specification = PerformanceReviewSpecification.hasEmployeeId(employeeId).and((root, query, cb) ->
            cb.equal(root.get("status"), PerformanceReviewStatus.SHARED)
        );
        return performanceReviewRepository.findAll(specification, pageable).map(review -> toResponse(review, true));
    }

    /**
     * Search performance reviews using multiple criteria with pagination.
     *
     * @param searchRequest the search criteria
     * @param pageable pagination information
     * @return page of performance review responses matching the criteria
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public Page<PerformanceReviewResponse> searchPerformanceReviews(PerformanceReviewSearchRequest searchRequest, Pageable pageable) {
        log.debug("Request to search PerformanceReviews with criteria: {}", searchRequest);

        Specification<PerformanceReview> specification = PerformanceReviewSpecification.withCriteria(
            searchRequest.employeeId(),
            searchRequest.reviewerId(),
            searchRequest.reviewDateFrom(),
            searchRequest.reviewDateTo(),
            searchRequest.minRating(),
            searchRequest.maxRating(),
            searchRequest.comments(),
            searchRequest.createdBy(),
            searchRequest.createdDateFrom(),
            searchRequest.createdDateTo(),
            searchRequest.cycleId(),
            searchRequest.status()
        );

        return performanceReviewRepository.findAll(specification, pageable).map(this::mapToResponse);
    }

    /**
     * Whether {@code managerId} is above {@code employeeId} in the reporting line (direct or
     * indirect). Employees' materialized paths end with their own id, so a report's path starts
     * with their manager's path.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public boolean isInReportingLine(UUID managerId, UUID employeeId) {
        if (managerId == null || employeeId == null || managerId.equals(employeeId)) {
            return false;
        }
        Employee manager = employeeRepository.findById(managerId).orElse(null);
        Employee employee = employeeRepository.findById(employeeId).orElse(null);
        return (
            manager != null &&
            employee != null &&
            manager.getPath() != null &&
            employee.getPath() != null &&
            employee.getPath().startsWith(manager.getPath() + "/")
        );
    }

    /** HR's view of a review — except one's own, which reads as its employee sees it, HR rights or not. */
    private PerformanceReviewResponse mapToResponse(PerformanceReview review) {
        UUID currentUser = SecurityUtils.getCurrentUserId().orElse(null);
        return toResponse(review, review.getEmployee().getId().equals(currentUser));
    }

    /**
     * Maps a review. With {@code forReviewedEmployee} (the employee reading their own review), a cycle
     * review not yet shared hides the reviewer's side — rating, comments, assessment and feedback
     * notes — and a shared one still hides what stays with HR and management: the reviewer's
     * pre-calibration rating, the promotion recommendation and the calibration note.
     */
    public PerformanceReviewResponse toResponse(PerformanceReview review, boolean forReviewedEmployee) {
        Employee employee = review.getEmployee();
        Employee reviewer = review.getReviewer();
        ReviewCycle cycle = review.getCycle();
        boolean hideReviewerSide = forReviewedEmployee && cycle != null && review.getStatus() != PerformanceReviewStatus.SHARED;

        PerformanceReviewResponse.SelfAssessment self = review.getSelfSubmittedAt() == null
            ? null
            : new PerformanceReviewResponse.SelfAssessment(
                review.getSelfAchievements(),
                review.getSelfChallenges(),
                review.getSelfDevelopmentGoals(),
                review.getSelfComments(),
                review.getSelfSubmittedAt()
            );
        PerformanceReviewResponse.ManagerAssessment manager = review.getManagerSubmittedAt() == null || hideReviewerSide
            ? null
            : new PerformanceReviewResponse.ManagerAssessment(
                forReviewedEmployee ? null : review.getManagerRating(),
                review.getStrengths(),
                review.getAreasForImprovement(),
                review.getDevelopmentRecommendations(),
                forReviewedEmployee ? null : review.isRecommendForPromotion(),
                forReviewedEmployee ? null : review.getPromotionJustification(),
                forReviewedEmployee ? null : review.getProbationRecommendation(),
                forReviewedEmployee ? null : review.getCalibrationNote(),
                forReviewedEmployee ? null : review.getPotential(),
                review.getManagerSubmittedAt()
            );

        PerformanceReviewResponse.Acknowledgement acknowledgement = review.getAcknowledgedAt() == null
            ? null
            : new PerformanceReviewResponse.Acknowledgement(
                review.getAcknowledgedAt(),
                review.getEmployeeComment(),
                review.isDisputed(),
                review.getDisputeResolution(),
                review.getDisputeResolvedAt(),
                review.getDisputeResolvedBy() != null ? fullName(review.getDisputeResolvedBy()) : null
            );

        PerformanceReviewResponse.SignOff signOff = forReviewedEmployee || review.getSignOffStatus() == null
            ? null
            : new PerformanceReviewResponse.SignOff(
                review.getSignOffStatus(),
                review.getSignOffComment(),
                review.getSignOffDecidedAt(),
                review.getSignOffDecidedBy() != null ? fullName(review.getSignOffDecidedBy()) : null
            );

        return new PerformanceReviewResponse(
            review.getId(),
            employee.getId(),
            fullName(employee),
            employee.getJobTitle(),
            employee.getDepartment() != null ? employee.getDepartment().getName() : null,
            reviewer != null ? reviewer.getId() : null,
            reviewer != null ? fullName(reviewer) : null,
            review.getReviewDate(),
            hideReviewerSide ? null : review.getComments(),
            hideReviewerSide ? null : review.getRating(),
            cycle != null ? cycle.getId() : null,
            cycle != null ? cycle.getName() : null,
            cycle != null ? cycle.getType() : null,
            cycle != null ? cycle.getPhase() : null,
            cycle != null ? phaseDeadline(cycle) : null,
            review.getStatus(),
            self,
            manager,
            hideReviewerSide ? null : review.getFeedbackNotes(),
            review.getSharedAt(),
            acknowledgement,
            signOff,
            forReviewedEmployee || review.getExcludedAt() == null
                ? null
                : new PerformanceReviewResponse.Exclusion(
                    review.getExcludedAt(),
                    review.getExclusionReason(),
                    review.getExcludedBy() != null ? fullName(review.getExcludedBy()) : null
                ),
            review.getCreatedBy(),
            review.getCreatedDate(),
            review.getLastModifiedBy(),
            review.getLastModifiedDate(),
            null
        );
    }

    private static LocalDate phaseDeadline(ReviewCycle cycle) {
        if (cycle.getPhase() == null) {
            return null;
        }
        return switch (cycle.getPhase()) {
            case SELF_ASSESSMENT -> cycle.getSelfAssessmentDeadline();
            case MANAGER_REVIEW -> cycle.getManagerReviewDeadline();
            case CALIBRATION -> cycle.getCalibrationDeadline();
            case FEEDBACK_DELIVERY -> cycle.getFeedbackDeadline();
            case GOAL_SETTING -> cycle.getEndDate();
            default -> null;
        };
    }

    private static String fullName(Employee employee) {
        return employee.getFirstName() + " " + employee.getLastName();
    }

    /** Nobody writes, changes or deletes a review about themselves — HR rights included. */
    private static void requireNotOwnReview(UUID employeeId) {
        if (employeeId.equals(SecurityUtils.getCurrentUserId().orElse(null))) {
            throw new AccessDeniedException("You can't act on your own review");
        }
    }

    /**
     * Cycle reviews change only through the cycle, even once it closed: their rating changes are
     * logged, the employee's acknowledgement locks them, and colleagues' feedback hangs off them.
     */
    private static void requireOutsideCycle(PerformanceReview review) {
        if (review.getCycle() != null) {
            throw new BadRequestAlertException(
                "This review belongs to a review cycle and is changed through the cycle",
                "performanceReview",
                "incycle"
            );
        }
    }
}
