-- 모임을 연 사람은 늘 참여자다(2026-10-05). 같이 읽기·노트 쓰기가 참여자만 되므로, 이미 연 모임에도
-- 연 사람을 참여자로 넣어 자기 모임의 노트를 쓰지 못하는 일이 없게 한다.
-- 취소한 모임, 클럽을 떠난 사람, 정원이 이미 찬 모임은 건드리지 않는다.
INSERT INTO club_meeting_attendees (meeting_id, user_id)
SELECT m.id, m.created_by
FROM club_meetings m
JOIN club_members cm ON cm.club_id = m.club_id AND cm.user_id = m.created_by AND cm.status = 'ACTIVE'
WHERE m.status = 'OPEN'
  AND m.created_by IS NOT NULL
  AND (m.max_attendees IS NULL
       OR (SELECT count(*) FROM club_meeting_attendees a WHERE a.meeting_id = m.id) < m.max_attendees)
ON CONFLICT (meeting_id, user_id) DO NOTHING;
