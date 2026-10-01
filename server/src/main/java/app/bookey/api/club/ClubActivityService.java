package app.bookey.api.club;

import app.bookey.api.club.dto.ClubCommunityDtos.*;
import app.bookey.common.error.*;
import app.bookey.domain.club.*;
import app.bookey.domain.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ClubActivityService {
 private final ClubService clubs; private final ClubRepository clubRepository; private final ClubMeetingRepository meetings; private final ClubActivitySessionRepository sessions; private final ClubActivityCardRepository cards; private final UserRepository users; private final Clock clock;
 @Transactional(readOnly=true) public ActivitySessionView current(Long u,Long c){clubs.activeMember(c,u);return sessions.findByClubIdAndUserIdAndEndedAtIsNull(c,u).map(this::sessionView).orElse(null);}
 @Transactional public ActivitySessionView start(Long u,Long c,Long meetingId){clubs.activeMember(c,u);if(meetingId!=null){var m=meetings.findById(meetingId).orElseThrow(()->ApiException.of(ErrorCode.CLUB_MEETING_NOT_FOUND));if(!m.getClubId().equals(c))throw ApiException.of(ErrorCode.CLUB_MEETING_NOT_FOUND);}return sessionView(sessions.findByClubIdAndUserIdAndEndedAtIsNull(c,u).orElseGet(()->sessions.save(new ClubActivitySession(c,meetingId,u))));}
 @Transactional public ActivityCardView end(Long u,Long c){clubs.activeMember(c,u);var s=sessions.findByClubIdAndUserIdAndEndedAtIsNull(c,u).orElseThrow(()->new ApiException(ErrorCode.INVALID_REQUEST,"진행 중인 모임 스탑워치가 없습니다."));s.end(clock.instant());var card=cards.save(new ClubActivityCard(c,s.getId(),u));return cardView(card,s);}
 /** 내 기록 카드 — 모든 클럽, 최근 50장. 노트 스티커 고르기에서 쓴다. */
 @Transactional(readOnly=true) public List<ActivityCardView> mine(Long u){return cards.findTop50ByUserIdOrderByIdDesc(u).stream().map(x->cardView(x,sessions.findById(x.getSessionId()).orElseThrow())).toList();}
 private ActivitySessionView sessionView(ClubActivitySession s){return new ActivitySessionView(s.getId(),s.getMeetingId(),s.getStartedAt(),s.getEndedAt(),s.getDurationSec());}
 private ActivityCardView cardView(ClubActivityCard c,ClubActivitySession s){String name=users.findById(c.getUserId()).map(User::getNickname).orElse("멤버");String club=clubRepository.findById(c.getClubId()).map(Club::getName).orElse("클럽");String meeting=s.getMeetingId()==null?null:meetings.findById(s.getMeetingId()).map(ClubMeeting::getTitle).orElse(null);return new ActivityCardView(c.getId(),c.getClubId(),club,meeting,name,Optional.ofNullable(s.getDurationSec()).orElse(0),s.getEndedAt());}
}
