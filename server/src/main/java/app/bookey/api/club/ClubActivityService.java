package app.bookey.api.club;

import app.bookey.api.club.dto.ClubCommunityDtos.*;
import app.bookey.common.config.BookeyProperties;
import app.bookey.common.error.*;
import app.bookey.common.storage.*;
import app.bookey.domain.club.*;
import app.bookey.domain.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ClubActivityService {
 private static final int SNIFF_BYTES=64*1024;
 private final ClubService clubs; private final ClubActivitySessionRepository sessions; private final ClubActivityCardRepository cards; private final UserRepository users; private final StorageService storage; private final BookeyProperties properties; private final Clock clock;
 @Transactional(readOnly=true) public ActivitySessionView current(Long u,Long c){clubs.activeMember(c,u);return sessions.findByClubIdAndUserIdAndEndedAtIsNull(c,u).map(this::sessionView).orElse(null);}
 @Transactional public ActivitySessionView start(Long u,Long c){clubs.activeMember(c,u);return sessionView(sessions.findByClubIdAndUserIdAndEndedAtIsNull(c,u).orElseGet(()->sessions.save(new ClubActivitySession(c,u))));}
 @Transactional public ActivityCardView end(Long u,Long c){clubs.activeMember(c,u);var s=sessions.findByClubIdAndUserIdAndEndedAtIsNull(c,u).orElseThrow(()->new ApiException(ErrorCode.INVALID_REQUEST,"진행 중인 모임 스탑워치가 없습니다."));s.end(clock.instant());var card=cards.save(new ClubActivityCard(c,s.getId(),u));return cardView(card,s);}
 @Transactional(readOnly=true) public List<ActivityCardView> list(Long u,Long c){clubs.activeMember(c,u);return cards.findAllByClubIdOrderByIdDesc(c).stream().map(x->cardView(x,sessions.findById(x.getSessionId()).orElseThrow())).toList();}
 @Transactional public ActivityCardView decorate(Long u,Long c,Long cardId,String caption,String decorations,MultipartFile file){clubs.activeMember(c,u);if(caption!=null&&caption.length()>500)throw ApiException.of(ErrorCode.INVALID_REQUEST);if(decorations!=null&&decorations.length()>4000)throw ApiException.of(ErrorCode.INVALID_REQUEST);var card=cards.findById(cardId).orElseThrow(()->ApiException.of(ErrorCode.INVALID_REQUEST));if(!card.getClubId().equals(c)||!card.getUserId().equals(u))throw ApiException.of(ErrorCode.FORBIDDEN);String url=card.getPhotoUrl(),key=card.getPhotoKey();if(file!=null&&!file.isEmpty()){var stored=store(c,u,file);url=stored[0];key=stored[1];}card.decorate(caption,decorations,url,key);return cardView(card,sessions.findById(card.getSessionId()).orElseThrow());}
 private String[] store(Long c,Long u,MultipartFile f){if(!storage.enabled())throw ApiException.of(ErrorCode.STORAGE_DISABLED);if(f.getSize()>properties.storage().image().maxBytes())throw ApiException.of(ErrorCode.IMAGE_TOO_LARGE);try(InputStream in=f.getInputStream()){var type=ImageSniffer.sniff(in.readNBytes(SNIFF_BYTES));if(type==null)throw ApiException.of(ErrorCode.UNSUPPORTED_IMAGE_TYPE);String key=StorageKeys.forClubActivityCard(c,u,clock.instant(),type.extension());try(InputStream body=f.getInputStream()){return new String[]{storage.store(key,body,f.getSize(),type.contentType()),key};}}catch(IOException e){throw ApiException.of(ErrorCode.STORAGE_ERROR);}}
 private ActivitySessionView sessionView(ClubActivitySession s){return new ActivitySessionView(s.getId(),s.getStartedAt(),s.getEndedAt(),s.getDurationSec());}
 private ActivityCardView cardView(ClubActivityCard c,ClubActivitySession s){String name=users.findById(c.getUserId()).map(User::getNickname).orElse("멤버");return new ActivityCardView(c.getId(),c.getSessionId(),c.getUserId(),name,Optional.ofNullable(s.getDurationSec()).orElse(0),c.getCaption(),c.getDecorationsJson(),c.getPhotoUrl());}
}
