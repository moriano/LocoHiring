--liquibase formatted sql

-- All seeded users have the password "admin" (bcrypt hash below).

--changeset locohiring:002-seed-data
INSERT INTO recruiters (email, password, first_name, is_admin) VALUES
    ('alice@company.com', '$2a$10$ny74H5.bpL0i24gly2t7A.3LZIZ3GSxPxPFgsZ5r/pUZFoNlPrkFS', 'Alice', FALSE),
    ('john@company.com',  '$2a$10$ny74H5.bpL0i24gly2t7A.3LZIZ3GSxPxPFgsZ5r/pUZFoNlPrkFS', 'John',  FALSE),
    ('paul@company.com',  '$2a$10$ny74H5.bpL0i24gly2t7A.3LZIZ3GSxPxPFgsZ5r/pUZFoNlPrkFS', 'Paul',  FALSE),
    ('bob@company.com',   '$2a$10$ny74H5.bpL0i24gly2t7A.3LZIZ3GSxPxPFgsZ5r/pUZFoNlPrkFS', 'Bob',   FALSE),
    ('admin@company.com', '$2a$10$ny74H5.bpL0i24gly2t7A.3LZIZ3GSxPxPFgsZ5r/pUZFoNlPrkFS', 'admin', TRUE);

INSERT INTO job_families (name) VALUES
    ('Software Engineer'),
    ('Software Manager'),
    ('Marketing Manager'),
    ('Graphical Designer');

INSERT INTO interviewers (email, password, first_name, level) VALUES
    ('peter@company.com',  '$2a$10$ny74H5.bpL0i24gly2t7A.3LZIZ3GSxPxPFgsZ5r/pUZFoNlPrkFS', 'Peter',  6),
    ('nathan@company.com', '$2a$10$ny74H5.bpL0i24gly2t7A.3LZIZ3GSxPxPFgsZ5r/pUZFoNlPrkFS', 'Nathan', 5),
    ('nick@company.com',   '$2a$10$ny74H5.bpL0i24gly2t7A.3LZIZ3GSxPxPFgsZ5r/pUZFoNlPrkFS', 'Nick',   6);

INSERT INTO interviewers_jobfamily (interviewer_id, jobfamily_id)
SELECT i.id, jf.id FROM interviewers i JOIN job_families jf ON
    (i.email = 'peter@company.com'  AND jf.name = 'Software Engineer') OR
    (i.email = 'nathan@company.com' AND jf.name = 'Marketing Manager') OR
    (i.email = 'nick@company.com'   AND jf.name = 'Graphical Designer');

INSERT INTO positions (name, description, level, jobfamily_id)
SELECT p.name, p.description, p.level, jf.id
FROM (VALUES
    (1, 'Software Engineer Level 5',  'Software Engineer',  5, 'Software Engineer'),
    (2, 'Marketing Manager Level 6',  'Marketing Manager',  6, 'Marketing Manager'),
    (3, 'Graphical Designer Level 6', 'Graphical Designer', 6, 'Graphical Designer')
) AS p (ord, name, description, level, family)
JOIN job_families jf ON jf.name = p.family
ORDER BY p.ord;

INSERT INTO candidates (email, first_name, position_id)
SELECT c.email, c.first_name, p.id
FROM (VALUES
    (1, 'adam@candidates.com',   'Adam',   'Software Engineer Level 5'),
    (2, 'maggie@candidates.com', 'Maggie', 'Software Engineer Level 5'),
    (3, 'jake@candidates.com',   'Jake',   'Graphical Designer Level 6'),
    (4, 'peter@candidates.com',  'Peter',  'Marketing Manager Level 6')
) AS c (ord, email, first_name, position)
JOIN positions p ON p.name = c.position
ORDER BY c.ord;

-- The interview types used as examples in the README, so recruiters can build funnels right away.
INSERT INTO interview_types (name, description) VALUES
    ('Phone Screen',             'Short screening call'),
    ('Coding Interview',         'Hands-on coding exercise'),
    ('Design Interview',         'System design discussion'),
    ('People Manager Interview', 'People management experience'),
    ('Behavioural Interview',    'Past behaviour and values');
