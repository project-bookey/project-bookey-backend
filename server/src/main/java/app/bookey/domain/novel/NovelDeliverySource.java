package app.bookey.domain.novel;

/**
 * 열매가 온 길 — 홈의 열매를 골라 자리를 맡은 것(CLAIM), 햇살을 내고 내 회차를 나에게 보낸 것(SELF),
 * 예전에 무작위로 고른 사람에게 보낸 것(RANDOM, 무작위 배달을 없앤 뒤로는 새로 생기지 않는다).
 */
public enum NovelDeliverySource { RANDOM, SELF, CLAIM }
