package com.humano.service.hr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.ReviewCyclePhase;
import com.humano.domain.enumeration.hr.ReviewPotential;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.shared.Employee;
import com.humano.dto.hr.requests.CreatePerformanceReviewRequest;
import com.humano.dto.hr.requests.PerformanceReviewSearchRequest;
import com.humano.dto.hr.requests.UpdatePerformanceReviewRequest;
import com.humano.dto.hr.responses.PerformanceReviewResponse;
import com.humano.repository.hr.PerformanceReviewRepository;
import com.humano.repository.shared.EmployeeRepository;
import com.humano.security.AuthenticatedUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** HR rights never reach one's own review: it reads as its employee sees it, and can't be written. */
class PerformanceReviewServiceTest {

    private final PerformanceReviewRepository reviews = mock(PerformanceReviewRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final PerformanceReviewService service = new PerformanceReviewService(reviews, employees);

    private final Employee hr = employee("Hanna", "Hr");
    private final Employee colleague = employee("Carl", "Colleague");
    private final Employee reviewer = employee("Rita", "Reviewer");

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hrListingACycleSeesTheirOwnReviewAsTheEmployeeDoes() {
        PerformanceReview own = calibratedReview(hr);
        PerformanceReview other = calibratedReview(colleague);
        when(reviews.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(own, other)));
        signInAs(hr);

        List<PerformanceReviewResponse> page = service
            .searchPerformanceReviews(
                new PerformanceReviewSearchRequest(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    own.getCycle().getId(),
                    null
                ),
                Pageable.unpaged()
            )
            .getContent();

        PerformanceReviewResponse.ManagerAssessment ownView = page.get(0).managerAssessment();
        assertThat(ownView.potential()).isNull();
        assertThat(ownView.calibrationNote()).isNull();
        assertThat(ownView.rating()).isNull();
        assertThat(ownView.recommendForPromotion()).isNull();
        assertThat(page.get(0).rating()).isEqualTo(4);

        PerformanceReviewResponse.ManagerAssessment otherView = page.get(1).managerAssessment();
        assertThat(otherView.potential()).isEqualTo(ReviewPotential.HIGH);
        assertThat(otherView.calibrationNote()).isEqualTo("Raised after calibration");
        assertThat(otherView.rating()).isEqualTo(3);
    }

    @Test
    void hrReadingTheirOwnUnsharedReviewByIdSeesNoneOfTheReviewersSide() {
        PerformanceReview own = calibratedReview(hr);
        own.setStatus(PerformanceReviewStatus.MANAGER_SUBMITTED);
        when(reviews.findById(own.getId())).thenReturn(Optional.of(own));
        signInAs(hr);

        PerformanceReviewResponse response = service.getPerformanceReviewById(own.getId());

        assertThat(response.managerAssessment()).isNull();
        assertThat(response.rating()).isNull();
    }

    @Test
    void hrNeverWritesChangesOrDeletesAReviewAboutThemselves() {
        PerformanceReview own = new PerformanceReview();
        own.setId(UUID.randomUUID());
        own.setEmployee(hr);
        own.setReviewer(reviewer);
        own.setStatus(PerformanceReviewStatus.SHARED);
        when(reviews.findById(own.getId())).thenReturn(Optional.of(own));
        when(employees.findById(hr.getId())).thenReturn(Optional.of(hr));
        when(employees.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        signInAs(hr);

        assertThatThrownBy(() ->
            service.createPerformanceReview(new CreatePerformanceReviewRequest(hr.getId(), reviewer.getId(), LocalDate.now(), null, 5))
        ).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.updatePerformanceReview(own.getId(), new UpdatePerformanceReviewRequest(null, null, 5))
        ).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.deletePerformanceReview(own.getId())).isInstanceOf(AccessDeniedException.class);

        verify(reviews, never()).save(any());
        verify(reviews, never()).delete(eq(own));
    }

    /** A shared cycle review the reviewer rated 3 and calibration raised to 4. */
    private PerformanceReview calibratedReview(Employee employee) {
        ReviewCycle cycle = new ReviewCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setName("2026 annual review");
        cycle.setPhase(ReviewCyclePhase.COMPLETED);

        PerformanceReview review = new PerformanceReview();
        review.setId(UUID.randomUUID());
        review.setCycle(cycle);
        review.setEmployee(employee);
        review.setReviewer(reviewer);
        review.setStatus(PerformanceReviewStatus.SHARED);
        review.setManagerRating(3);
        review.setRating(4);
        review.setCalibrationNote("Raised after calibration");
        review.setPotential(ReviewPotential.HIGH);
        review.setRecommendForPromotion(true);
        review.setStrengths("Delivers");
        review.setManagerSubmittedAt(Instant.now());
        return review;
    }

    private static void signInAs(Employee employee) {
        AuthenticatedUser user = new AuthenticatedUser(employee.getFirstName(), "", List.of(), employee.getId(), "test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private static Employee employee(String firstName, String lastName) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setFirstName(firstName);
        e.setLastName(lastName);
        return e;
    }
}
