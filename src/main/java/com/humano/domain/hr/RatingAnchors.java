package com.humano.domain.hr;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What each level of the five-point rating looks like for one competency — the behaviour a reviewer
 * should see to give a 1, a 2, … a 5. Any level may be left blank.
 */
@Embeddable
public class RatingAnchors {

    public static final int LEVELS = 5;
    public static final int MAX_LENGTH = 500;

    @Column(name = "anchor_1", length = MAX_LENGTH)
    private String anchor1;

    @Column(name = "anchor_2", length = MAX_LENGTH)
    private String anchor2;

    @Column(name = "anchor_3", length = MAX_LENGTH)
    private String anchor3;

    @Column(name = "anchor_4", length = MAX_LENGTH)
    private String anchor4;

    @Column(name = "anchor_5", length = MAX_LENGTH)
    private String anchor5;

    /** Levels 1 to 5 in order; blank levels are {@code null}. */
    public List<String> toList() {
        return new ArrayList<>(Arrays.asList(anchor1, anchor2, anchor3, anchor4, anchor5));
    }

    /** From levels 1 to 5 in order; missing levels are blank. */
    public static RatingAnchors of(List<String> levels) {
        RatingAnchors anchors = new RatingAnchors();
        anchors.anchor1 = level(levels, 0);
        anchors.anchor2 = level(levels, 1);
        anchors.anchor3 = level(levels, 2);
        anchors.anchor4 = level(levels, 3);
        anchors.anchor5 = level(levels, 4);
        return anchors;
    }

    /** A copy, for a cycle's snapshot. */
    public RatingAnchors copy() {
        return of(toList());
    }

    private static String level(List<String> levels, int index) {
        if (levels == null || index >= levels.size() || levels.get(index) == null) {
            return null;
        }
        String value = levels.get(index).trim();
        return value.isEmpty() ? null : value;
    }
}
