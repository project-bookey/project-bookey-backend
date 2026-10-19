package app.bookey.domain.novel;

/** 회차 상태 — 나무에 있음(ACTIVE) → 수거함(PRUNED) → 지워짐(REMOVED). 수거함의 가지는 살리면 다시 ACTIVE 다. */
public enum NovelChapterState { ACTIVE, PRUNED, REMOVED }
