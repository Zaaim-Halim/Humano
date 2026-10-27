package com.humano.domain.enumeration.hr;

/**
 * The reviewer's view of how far a goal was achieved. Its own scale, separate from the five-point
 * performance rating: the overall rating is never computed from it.
 */
public enum GoalAchievement {
    NOT_MET,
    PARTIALLY_MET,
    MET,
    EXCEEDED,
}
