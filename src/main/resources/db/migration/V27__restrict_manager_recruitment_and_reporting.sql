DELETE FROM role_permission_scope rps
USING role r, permission p
WHERE rps.role_id = r.id
  AND rps.permission_id = p.id
  AND r.name = 'MANAGER'
  AND p.code IN ('RECRUITMENT_VIEW', 'REPORTS_VIEW');

DELETE FROM role_permissions rp
USING role r, permission p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.name = 'MANAGER'
  AND p.code IN ('RECRUITMENT_VIEW', 'REPORTS_VIEW');