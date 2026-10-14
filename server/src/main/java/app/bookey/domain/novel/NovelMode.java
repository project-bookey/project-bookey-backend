package app.bookey.domain.novel;

/** 릴레이를 이어 가는 방식. OPEN 은 회차마다 무작위로 고른 사람에게 열매로 배달하고 받은 사람이 가지를 뻗는다.
 *  CIRCLE 은 만든 사람이 고른(또는 승인한) 작가들이 차례대로 한 줄기로 쓴다. */
public enum NovelMode { OPEN, CIRCLE }
