package com.humano.service.hr.workflow;

import com.humano.domain.enumeration.hr.GoalStatus;
import com.humano.domain.enumeration.hr.PerformanceReviewStatus;
import com.humano.domain.enumeration.hr.ReviewQuestionAudience;
import com.humano.domain.hr.Competency;
import com.humano.domain.hr.Goal;
import com.humano.domain.hr.PerformanceReview;
import com.humano.domain.hr.ReviewCompetencyRating;
import com.humano.domain.hr.ReviewCycle;
import com.humano.domain.hr.ReviewCycleCompetency;
import com.humano.domain.hr.ReviewCycleQuestion;
import com.humano.domain.hr.ReviewGoalRating;
import com.humano.domain.hr.ReviewQuestionAnswer;
import com.humano.domain.hr.ReviewQuestionDefinition;
import com.humano.domain.hr.ReviewTemplate;
import com.humano.dto.hr.workflow.requests.CompetencyRatingInput;
import com.humano.dto.hr.workflow.requests.GoalRatingInput;
import com.humano.dto.hr.workflow.requests.ReviewAnswerInput;
import com.humano.dto.hr.workflow.responses.ReviewForm;
import com.humano.repository.hr.GoalRepository;
import com.humano.repository.hr.ReviewCompetencyRatingRepository;
import com.humano.repository.hr.ReviewCycleCompetencyRepository;
import com.humano.repository.hr.ReviewCycleQuestionRepository;
import com.humano.repository.hr.ReviewGoalRatingRepository;
import com.humano.repository.hr.ReviewQuestionAnswerRepository;
import com.humano.service.errors.BadRequestAlertException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The part of a review that comes from its cycle's template: the competencies the reviewer rates
 * and the questions the employee and the reviewer answer.
 *
 * <p>When self-assessment opens, the cycle copies its template ({@link #snapshot}); from then on its
 * reviews use the copy, so editing or retiring the template or a competency never changes a running
 * or closed cycle. A cycle without a template uses the standard form only.
 *
 * <p>The employee answers the EMPLOYEE questions with their self-assessment; the reviewer rates every
 * competency and answers the REVIEWER questions with the review. Required questions need an answer.
 * The reviewed employee sees the reviewer's ratings and answers once the review is shared.
 *
 * <p>Goals: the reviewer also rates each of the employee's goals due within the review period (agreed
 * or completed) on the goal scale — never the employee, and never feeding the overall rating. When
 * the review is submitted, each rating keeps the goal's title and weight; from then on the review
 * shows those, and once shared only those, so later changes elsewhere never alter it.
 */
@Service
@Transactional("tenantTransactionManager")
public class ReviewFormService {

    private static final String ENTITY_NAME = "performanceReview";

    private final ReviewCycleCompetencyRepository cycleCompetencyRepository;
    private final ReviewCycleQuestionRepository cycleQuestionRepository;
    private final ReviewCompetencyRatingRepository ratingRepository;
    private final ReviewQuestionAnswerRepository answerRepository;
    private final GoalRepository goalRepository;
    private final ReviewGoalRatingRepository goalRatingRepository;

    public ReviewFormService(
        ReviewCycleCompetencyRepository cycleCompetencyRepository,
        ReviewCycleQuestionRepository cycleQuestionRepository,
        ReviewCompetencyRatingRepository ratingRepository,
        ReviewQuestionAnswerRepository answerRepository,
        GoalRepository goalRepository,
        ReviewGoalRatingRepository goalRatingRepository
    ) {
        this.cycleCompetencyRepository = cycleCompetencyRepository;
        this.cycleQuestionRepository = cycleQuestionRepository;
        this.ratingRepository = ratingRepository;
        this.answerRepository = answerRepository;
        this.goalRepository = goalRepository;
        this.goalRatingRepository = goalRatingRepository;
    }

    /** Copies the cycle's template onto the cycle, once. Nothing to do without a template. */
    public void snapshot(ReviewCycle cycle) {
        ReviewTemplate template = cycle.getTemplate();
        if (
            template == null ||
            cycleCompetencyRepository.existsByCycleId(cycle.getId()) ||
            cycleQuestionRepository.existsByCycleId(cycle.getId())
        ) {
            return;
        }
        cycle.setTemplateName(template.getName());
        List<Competency> competencies = template.getCompetencies();
        for (int i = 0; i < competencies.size(); i++) {
            cycleCompetencyRepository.save(ReviewCycleCompetency.of(cycle, competencies.get(i), i));
        }
        List<ReviewQuestionDefinition> questions = template.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            cycleQuestionRepository.save(ReviewCycleQuestion.of(cycle, questions.get(i), i));
        }
    }

    /**
     * The review's template part with its answers; {@code null} for the standard form. For the
     * reviewed employee, the reviewer's side is left out until the review is shared.
     */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    public ReviewForm formOf(PerformanceReview review, boolean forReviewedEmployee) {
        ReviewCycle cycle = review.getCycle();
        if (cycle == null) {
            return null;
        }
        List<ReviewCycleCompetency> competencies = cycleCompetencyRepository.findByCycleIdOrderBySortOrderAsc(cycle.getId());
        List<ReviewCycleQuestion> questions = cycleQuestionRepository.findByCycleIdOrderBySortOrderAsc(cycle.getId());
        boolean hideReviewerSide = forReviewedEmployee && review.getStatus() != PerformanceReviewStatus.SHARED;
        List<ReviewForm.FormGoal> goals = goalsOf(review)
            .stream()
            .map(g ->
                hideReviewerSide
                    ? new ReviewForm.FormGoal(
                        g.id(),
                        g.title(),
                        g.successMeasure(),
                        g.weight(),
                        g.dueDate(),
                        g.status(),
                        g.progress(),
                        null,
                        null
                    )
                    : g
            )
            .toList();
        if (competencies.isEmpty() && questions.isEmpty() && goals.isEmpty()) {
            return null;
        }

        List<ReviewForm.Answer> answers = answerRepository
            .findByReviewId(review.getId())
            .stream()
            .filter(a -> !hideReviewerSide || a.getQuestion().getDefinition().getAudience() == ReviewQuestionAudience.EMPLOYEE)
            .sorted((a, b) -> Integer.compare(a.getQuestion().getSortOrder(), b.getQuestion().getSortOrder()))
            .map(a -> new ReviewForm.Answer(a.getQuestion().getId(), a.getAnswer()))
            .toList();
        Map<UUID, Integer> order = competencies
            .stream()
            .collect(Collectors.toMap(ReviewCycleCompetency::getId, ReviewCycleCompetency::getSortOrder));
        List<ReviewForm.CompetencyRating> ratings = hideReviewerSide
            ? List.of()
            : ratingRepository
                .findByReviewId(review.getId())
                .stream()
                .sorted((a, b) ->
                    Integer.compare(order.getOrDefault(a.getCompetency().getId(), 0), order.getOrDefault(b.getCompetency().getId(), 0))
                )
                .map(r -> new ReviewForm.CompetencyRating(r.getCompetency().getId(), r.getRating(), r.getComment()))
                .toList();

        return new ReviewForm(
            cycle.getTemplateName() != null ? cycle.getTemplateName() : cycle.getTemplate() != null ? cycle.getTemplate().getName() : null,
            competencies
                .stream()
                .map(c ->
                    new ReviewForm.FormCompetency(c.getId(), c.getName(), c.getDescription(), c.getCategory(), c.getAnchors().toList())
                )
                .toList(),
            questions
                .stream()
                .map(q ->
                    new ReviewForm.FormQuestion(
                        q.getId(),
                        q.getDefinition().getAudience(),
                        q.getDefinition().getText(),
                        q.getDefinition().getHelpText(),
                        q.getDefinition().isRequired()
                    )
                )
                .toList(),
            answers,
            ratings,
            goals
        );
    }

    /** Checks and saves the employee's answers with their self-assessment. */
    public void saveSelfAnswers(PerformanceReview review, List<ReviewAnswerInput> answers) {
        saveAnswers(review, ReviewQuestionAudience.EMPLOYEE, answers);
    }

    /**
     * Checks and saves the reviewer's competency ratings and answers with the review: every
     * competency rated, every required REVIEWER question answered.
     */
    public void saveReviewerInput(
        PerformanceReview review,
        List<CompetencyRatingInput> ratings,
        List<ReviewAnswerInput> answers,
        List<GoalRatingInput> goalRatings
    ) {
        UUID cycleId = review.getCycle().getId();
        List<ReviewCycleCompetency> competencies = cycleCompetencyRepository.findByCycleIdOrderBySortOrderAsc(cycleId);
        Map<UUID, ReviewCycleCompetency> byId = competencies
            .stream()
            .collect(Collectors.toMap(ReviewCycleCompetency::getId, Function.identity()));
        Map<UUID, CompetencyRatingInput> given = new HashMap<>();
        for (CompetencyRatingInput input : ratings != null ? ratings : List.<CompetencyRatingInput>of()) {
            if (!byId.containsKey(input.competencyId())) {
                throw new BadRequestAlertException("This competency isn't part of the review", ENTITY_NAME, "unknowncompetency");
            }
            if (given.put(input.competencyId(), input) != null) {
                throw new BadRequestAlertException("A competency is rated twice", ENTITY_NAME, "duplicatecompetency");
            }
        }
        for (ReviewCycleCompetency competency : competencies) {
            if (!given.containsKey(competency.getId())) {
                throw new BadRequestAlertException(
                    "Rate every competency: " + competency.getName() + " is missing",
                    ENTITY_NAME,
                    "competencymissing"
                );
            }
        }
        saveAnswers(review, ReviewQuestionAudience.REVIEWER, answers);

        Map<UUID, ReviewCompetencyRating> existing = ratingRepository
            .findByReviewId(review.getId())
            .stream()
            .collect(Collectors.toMap(r -> r.getCompetency().getId(), Function.identity()));
        for (ReviewCycleCompetency competency : competencies) {
            CompetencyRatingInput input = given.get(competency.getId());
            ReviewCompetencyRating rating = existing.getOrDefault(competency.getId(), new ReviewCompetencyRating(review, competency));
            rating.setRating(input.rating());
            rating.setComment(blankToNull(input.comment()));
            ratingRepository.save(rating);
        }
        saveGoalRatings(review, goalRatings);
    }

    /**
     * The goals the review rates, with the reviewer's ratings: those rated when it was submitted,
     * plus — until the reviewer first submits — the employee's agreed or completed goals due within the
     * period. From then on the set is frozen.
     */
    private List<ReviewForm.FormGoal> goalsOf(PerformanceReview review) {
        Map<UUID, ReviewForm.FormGoal> goals = new LinkedHashMap<>();
        for (ReviewGoalRating rated : goalRatingRepository.findByReviewId(review.getId())) {
            Goal g = rated.getGoal();
            goals.put(
                g.getId(),
                new ReviewForm.FormGoal(
                    g.getId(),
                    rated.getGoalTitle(),
                    g.getSuccessMeasure(),
                    rated.getGoalWeight(),
                    g.getDueDate(),
                    g.getStatus(),
                    g.getProgress(),
                    rated.getAchievement(),
                    rated.getComment()
                )
            );
        }
        if (goalSetOpen(review)) {
            for (Goal g : goalsInPeriod(review)) {
                goals.putIfAbsent(
                    g.getId(),
                    new ReviewForm.FormGoal(
                        g.getId(),
                        g.getTitle(),
                        g.getSuccessMeasure(),
                        g.getWeight(),
                        g.getDueDate(),
                        g.getStatus(),
                        g.getProgress(),
                        null,
                        null
                    )
                );
            }
        }
        return goals
            .values()
            .stream()
            .sorted(Comparator.comparing(ReviewForm.FormGoal::dueDate).thenComparing(ReviewForm.FormGoal::title))
            .toList();
    }

    /**
     * Whether the review still takes in goals due in its period: until the reviewer first submits it.
     * From then on — resubmitting, or revising after sign-off returned it — the review rates the goals
     * it rated then, so the form and the check on submit always agree.
     */
    private static boolean goalSetOpen(PerformanceReview review) {
        return review.getManagerSubmittedAt() == null;
    }

    private List<Goal> goalsInPeriod(PerformanceReview review) {
        ReviewCycle cycle = review.getCycle();
        return goalRepository.findByEmployeeIdAndStatusInAndDueDateBetweenOrderByDueDateAscTitleAsc(
            review.getEmployee().getId(),
            EnumSet.of(GoalStatus.ACTIVE, GoalStatus.COMPLETED),
            cycle.getReviewPeriodStart(),
            cycle.getReviewPeriodEnd()
        );
    }

    /** Every goal of the review rated; each rating keeps the goal's title and weight as they are now. */
    private void saveGoalRatings(PerformanceReview review, List<GoalRatingInput> inputs) {
        Map<UUID, ReviewGoalRating> existing = goalRatingRepository
            .findByReviewId(review.getId())
            .stream()
            .collect(Collectors.toMap(r -> r.getGoal().getId(), Function.identity()));
        Map<UUID, Goal> goals = new LinkedHashMap<>();
        existing.values().forEach(r -> goals.put(r.getGoal().getId(), r.getGoal()));
        if (goalSetOpen(review)) {
            goalsInPeriod(review).forEach(g -> goals.putIfAbsent(g.getId(), g));
        }

        Map<UUID, GoalRatingInput> given = new HashMap<>();
        for (GoalRatingInput input : inputs != null ? inputs : List.<GoalRatingInput>of()) {
            if (!goals.containsKey(input.goalId())) {
                throw new BadRequestAlertException("This goal isn't part of the review", ENTITY_NAME, "unknowngoal");
            }
            if (given.put(input.goalId(), input) != null) {
                throw new BadRequestAlertException("A goal is rated twice", ENTITY_NAME, "duplicategoal");
            }
        }
        Set<UUID> missing = new HashSet<>(goals.keySet());
        missing.removeAll(given.keySet());
        if (!missing.isEmpty()) {
            throw new BadRequestAlertException(
                "Rate every goal: " + goals.get(missing.iterator().next()).getTitle() + " is missing",
                ENTITY_NAME,
                "goalmissing"
            );
        }
        goals.forEach((id, goal) -> {
            GoalRatingInput input = given.get(id);
            ReviewGoalRating rating = existing.getOrDefault(id, new ReviewGoalRating(review, goal));
            if (!existing.containsKey(id)) {
                rating.setGoalTitle(goal.getTitle());
                rating.setGoalWeight(goal.getWeight());
            }
            rating.setAchievement(input.achievement());
            rating.setComment(blankToNull(input.comment()));
            goalRatingRepository.save(rating);
        });
    }

    /**
     * Replaces the answers to the cycle's {@code audience} questions with {@code answers}: blank or
     * missing answers remove earlier ones; required questions must be answered.
     */
    private void saveAnswers(PerformanceReview review, ReviewQuestionAudience audience, List<ReviewAnswerInput> answers) {
        Map<UUID, ReviewCycleQuestion> questions = cycleQuestionRepository
            .findByCycleIdOrderBySortOrderAsc(review.getCycle().getId())
            .stream()
            .filter(q -> q.getDefinition().getAudience() == audience)
            .collect(Collectors.toMap(ReviewCycleQuestion::getId, Function.identity()));
        Map<UUID, String> given = new HashMap<>();
        for (ReviewAnswerInput input : answers != null ? answers : List.<ReviewAnswerInput>of()) {
            if (!questions.containsKey(input.questionId())) {
                throw new BadRequestAlertException("This question isn't yours to answer in this review", ENTITY_NAME, "unknownquestion");
            }
            String answer = blankToNull(input.answer());
            if (answer != null) {
                given.put(input.questionId(), answer);
            }
        }
        for (ReviewCycleQuestion question : questions.values()) {
            if (question.getDefinition().isRequired() && !given.containsKey(question.getId())) {
                throw new BadRequestAlertException("Answer: " + question.getDefinition().getText(), ENTITY_NAME, "answermissing");
            }
        }

        List<ReviewQuestionAnswer> removed = new ArrayList<>();
        Map<UUID, ReviewQuestionAnswer> existing = new HashMap<>();
        for (ReviewQuestionAnswer answer : answerRepository.findByReviewId(review.getId())) {
            if (answer.getQuestion().getDefinition().getAudience() != audience) {
                continue;
            }
            if (given.containsKey(answer.getQuestion().getId())) {
                existing.put(answer.getQuestion().getId(), answer);
            } else {
                removed.add(answer);
            }
        }
        answerRepository.deleteAll(removed);
        given.forEach((questionId, text) -> {
            ReviewQuestionAnswer answer = existing.getOrDefault(questionId, new ReviewQuestionAnswer(review, questions.get(questionId)));
            answer.setAnswer(text);
            answerRepository.save(answer);
        });
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
