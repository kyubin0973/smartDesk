-- 데모 시드 데이터. 모든 계정 비밀번호: Passw0rd!
-- password_hash = SHA-256 hex (REQ-N-003). 운영 전환 시 BCrypt 권장 (README 참고)

INSERT INTO department (id, name) VALUES
    (1, '인프라운영팀'),
    (2, '애플리케이션지원팀'),
    (3, '보안팀');

INSERT INTO app_user (id, department_id, name, email, password_hash, role) VALUES
    (1, 1, '김규빈', 'admin@smartdesk.io',  'e66860546f18cdbbcd86b35e18b525bffc67f772c650cedfe3ff7a0026fa1dee', 'MANAGER'),
    (2, 1, '이현우', 'infra@smartdesk.io',  'e66860546f18cdbbcd86b35e18b525bffc67f772c650cedfe3ff7a0026fa1dee', 'AGENT'),
    (3, 2, '박서연', 'app@smartdesk.io',    'e66860546f18cdbbcd86b35e18b525bffc67f772c650cedfe3ff7a0026fa1dee', 'AGENT'),
    (4, 3, '최민지', 'sec@smartdesk.io',    'e66860546f18cdbbcd86b35e18b525bffc67f772c650cedfe3ff7a0026fa1dee', 'AGENT');

INSERT INTO client (id, name) VALUES
    (1, 'A고객사'),
    (2, 'B고객사'),
    (3, 'C고객사');

INSERT INTO client_user (id, client_id, name, email, password_hash) VALUES
    (1, 1, '정우성', 'user@a-corp.com', 'e66860546f18cdbbcd86b35e18b525bffc67f772c650cedfe3ff7a0026fa1dee'),
    (2, 2, '한지민', 'user@b-corp.com', 'e66860546f18cdbbcd86b35e18b525bffc67f772c650cedfe3ff7a0026fa1dee');

INSERT INTO contract (id, client_id, start_date, end_date, sla_response_min, sla_resolution_min, maintenance_scope, status) VALUES
    (1, 1, DATE '2026-01-01', DATE '2026-12-31', 30, 480, '전 시스템 장애/문의 대응, 월 2회 정기점검', 'ACTIVE'),
    (2, 2, DATE '2026-03-01', DATE '2026-09-30', 60, 960, '애플리케이션 한정 유지보수', 'EXPIRING');

INSERT INTO system_asset (id, client_id, name, type) VALUES
    (1, 1, 'ERP', 'Application'),
    (2, 1, 'VPN 게이트웨이', 'Network'),
    (3, 1, '파일 스토리지', 'Storage'),
    (4, 2, '이커머스 포털', 'Application');

INSERT INTO category (id, name) VALUES
    (1, 'Hardware'),
    (2, 'Access'),
    (3, 'Storage'),
    (4, 'Purchase'),
    (5, 'Application');

-- REQ-F-003: SI 직원 담당 고객사
INSERT INTO user_client (user_id, client_id) VALUES
    (1, 1), (1, 2), (2, 1), (3, 1), (3, 2);

-- REQ-F-010: 카테고리별 처리 부서 (규칙 기반 자동배정 라우팅)
INSERT INTO category_routing (category_id, department_id) VALUES
    (1, 1),   -- Hardware   → 인프라운영팀
    (2, 3),   -- Access     → 보안팀
    (3, 1),   -- Storage    → 인프라운영팀
    (4, 2),   -- Purchase   → 애플리케이션지원팀
    (5, 2);   -- Application → 애플리케이션지원팀

INSERT INTO ticket (id, client_id, contract_id, system_id, category_id, requester_id, assignee_id, title, content, priority, status, sla_due_at, created_at) VALUES
    (1042, 1, 1, 2, 2, 1, 2, 'VPN 접속이 간헐적으로 끊깁니다', '오전부터 사내에서 VPN 접속 시 10분마다 연결이 끊어집니다.', 'HIGH', 'IN_PROGRESS', now() + INTERVAL '2 hours', now() - INTERVAL '6 hours'),
    (1043, 1, 1, 1, 5, 1, 3, 'ERP 월마감 배치 오류', '월마감 배치가 NullPointerException 으로 실패합니다.', 'CRITICAL', 'RECEIVED', now() + INTERVAL '5 hours', now() - INTERVAL '3 hours'),
    (1044, 2, 2, 4, NULL, 2, NULL, '상품 이미지 업로드 실패', '관리자 페이지에서 상품 이미지를 올리면 500 에러가 납니다.', 'MEDIUM', 'RECEIVED', now() + INTERVAL '12 hours', now() - INTERVAL '1 hours');

INSERT INTO document (id, client_id, category_id, created_by, title, content, version, scope) VALUES
    (1, NULL, 2, 2, 'VPN 장애 1차 대응 가이드', '1) 게이트웨이 세션 수 확인 2) MTU 조정 3) ISP 경로 점검', 2, 'SI_INTERNAL'),
    (2, 1, 5, 3, 'A고객사 ERP 배치 스케줄표', '월마감 배치는 매월 1일 02:00 KST 실행됩니다.', 1, 'CLIENT_SHARED');

INSERT INTO document_share (document_id, client_id) VALUES (2, 1);

INSERT INTO document_version (document_id, version, title, content, edited_by) VALUES
    (1, 1, 'VPN 장애 1차 대응 가이드', '1) 게이트웨이 세션 수 확인', 2),
    (1, 2, 'VPN 장애 1차 대응 가이드', '1) 게이트웨이 세션 수 확인 2) MTU 조정 3) ISP 경로 점검', 2),
    (2, 1, 'A고객사 ERP 배치 스케줄표', '월마감 배치는 매월 1일 02:00 KST 실행됩니다.', 3);

INSERT INTO comment (ticket_id, author_type, author_id, content) VALUES
    (1042, 'CLIENT_USER', 1, '재택 근무자도 동일 증상입니다.'),
    (1042, 'USER', 2, '게이트웨이 로그 확인 중입니다. 15시까지 중간 회신드리겠습니다.');

INSERT INTO ticket_history (ticket_id, field, old_value, new_value, actor_type, actor_id) VALUES
    (1042, 'status', 'RECEIVED', 'IN_PROGRESS', 'USER', 2),
    (1042, 'assignee', NULL, '2', 'USER', 1);

-- 시퀀스 동기화 (수동 id 삽입 후)
SELECT setval('department_id_seq',      (SELECT MAX(id) FROM department));
SELECT setval('app_user_id_seq',        (SELECT MAX(id) FROM app_user));
SELECT setval('client_id_seq',          (SELECT MAX(id) FROM client));
SELECT setval('client_user_id_seq',     (SELECT MAX(id) FROM client_user));
SELECT setval('contract_id_seq',        (SELECT MAX(id) FROM contract));
SELECT setval('system_asset_id_seq',    (SELECT MAX(id) FROM system_asset));
SELECT setval('category_id_seq',        (SELECT MAX(id) FROM category));
SELECT setval('ticket_id_seq',          (SELECT MAX(id) FROM ticket));
SELECT setval('document_id_seq',        (SELECT MAX(id) FROM document));
SELECT setval('document_version_id_seq',(SELECT MAX(id) FROM document_version));
