BEGIN;

SELECT plan(43);

SELECT has_table('public', 'accounts', 'accounts table exists');
SELECT has_table('public', 'member_profiles', 'member profiles table exists');
SELECT has_table('public', 'memberships', 'memberships table exists');
SELECT has_table('public', 'payments', 'payments table exists');
SELECT has_table('public', 'expenses', 'expenses table exists');
SELECT has_table('public', 'announcements', 'announcements table exists');
SELECT has_table('public', 'workouts', 'workouts table exists');
SELECT has_table('public', 'workout_sets', 'workout sets table exists');
SELECT has_table('public', 'body_metrics', 'body metrics table exists');

SELECT col_type_is('public', 'accounts', 'id', 'bigint', 'domain Account identifiers remain numeric');
SELECT col_type_is('public', 'accounts', 'auth_user_id', 'uuid', 'Accounts link to Auth UUIDs');
SELECT col_type_is('public', 'memberships', 'start_date', 'date', 'membership start is a date');
SELECT col_type_is(
    'public',
    'workouts',
    'started_at',
    'timestamp with time zone',
    'workout start is timezone-aware'
);

SELECT has_fk('public', 'accounts', 'accounts reference Auth users');
SELECT has_fk('public', 'member_profiles', 'member profiles reference accounts');
SELECT has_fk('public', 'memberships', 'memberships reference member profiles');
SELECT has_fk('public', 'payments', 'payments have foreign keys');
SELECT has_fk('public', 'expenses', 'expenses reference recording accounts');
SELECT has_fk('public', 'announcements', 'announcements reference creating accounts');
SELECT has_fk('public', 'workouts', 'workouts have foreign keys');
SELECT has_fk('public', 'workout_sets', 'workout sets reference workouts');
SELECT has_fk('public', 'body_metrics', 'body metrics reference member profiles');

SELECT has_function(
    'public',
    'create_member_records',
    'atomic Member creation function exists'
);
SELECT has_function(
    'public',
    'add_membership_with_payment',
    'atomic Membership and Payment function exists'
);

SELECT is(
    has_function_privilege(
        'authenticated',
        'public.create_member_records(uuid,text,text,text,date,date,date,bigint,text,timestamp with time zone,text,bigint)',
        'EXECUTE'
    ),
    false,
    'authenticated clients cannot execute privileged Member creation directly'
);
SELECT is(
    has_function_privilege(
        'service_role',
        'public.create_member_records(uuid,text,text,text,date,date,date,bigint,text,timestamp with time zone,text,bigint)',
        'EXECUTE'
    ),
    true,
    'the server role can execute privileged Member creation'
);

SELECT throws_ok(
    $$UPDATE public.accounts
         SET role = 'OWNER'
       WHERE id = 2$$,
    '22023',
    'Account roles cannot be changed',
    'an existing Member cannot be converted into an Owner'
);

SELECT throws_ok(
    $$UPDATE public.accounts
         SET email = 'owner.local@example.test'
       WHERE id = 2$$,
    '23505',
    NULL,
    'Owner and Member accounts cannot share an email address'
);

INSERT INTO public.memberships (
    member_account_id,
    start_date,
    expiry_date,
    is_active
)
VALUES (
    2,
    '2026-01-01',
    '2026-01-31',
    true
);

SELECT throws_ok(
    $$INSERT INTO public.memberships (
          member_account_id,
          start_date,
          expiry_date,
          is_active
      ) VALUES (
          2,
          '2026-01-31',
          '2026-02-28',
          true
      )$$,
    '23P01',
    NULL,
    'active Membership date ranges cannot overlap'
);

SELECT throws_ok(
    $$INSERT INTO public.memberships (
          member_account_id,
          start_date,
          expiry_date,
          is_active
      ) VALUES (
          3,
          '2026-02-01',
          '2026-01-01',
          true
      )$$,
    '23514',
    NULL,
    'Membership expiry cannot precede its start'
);

SELECT throws_ok(
    $$INSERT INTO public.payments (
          membership_id,
          amount_cents,
          method,
          paid_at,
          recorded_by_account_id
      ) VALUES (
          1,
          0,
          'CARD',
          now(),
          1
      )$$,
    '23514',
    NULL,
    'Payment amounts must be positive'
);

SELECT throws_ok(
    $$INSERT INTO public.expenses (
          expense_date,
          amount_cents,
          method,
          category,
          recorded_by_account_id
      ) VALUES (
          current_date,
          100,
          'CHEQUE',
          'OTHER',
          1
      )$$,
    '23514',
    NULL,
    'Expense payment methods are constrained'
);

SELECT throws_ok(
    $$INSERT INTO public.announcements (
          title,
          content,
          published_at,
          created_by_account_id,
          withdrawn_at
      ) VALUES (
          'Notice',
          'Content',
          '2026-02-02T00:00:00Z',
          1,
          '2026-02-01T00:00:00Z'
      )$$,
    '23514',
    NULL,
    'Announcements cannot be withdrawn before publication'
);

INSERT INTO public.workouts (member_account_id, started_at)
VALUES (
    2,
    '2026-02-01T10:00:00Z'
);

SELECT throws_ok(
    $$INSERT INTO public.workouts (member_account_id, started_at)
      VALUES (
          2,
          '2026-02-01T11:00:00Z'
      )$$,
    '23505',
    NULL,
    'a Member can have only one open Workout'
);

SELECT throws_ok(
    $$INSERT INTO public.workouts (member_account_id, started_at, ended_at)
      VALUES (
          3,
          '2026-02-01T11:00:00Z',
          '2026-02-01T10:00:00Z'
      )$$,
    '23514',
    NULL,
    'a Workout cannot end before it starts'
);

SELECT throws_ok(
    $$INSERT INTO public.workout_sets (
          workout_id,
          position,
          exercise_name,
          repetitions,
          duration_seconds
      ) VALUES (1, 0, 'Row', 10, 30)$$,
    '23514',
    NULL,
    'a Workout Set cannot use repetitions and duration together'
);

INSERT INTO public.body_metrics (
    member_account_id,
    measurement_date,
    weight_grams
)
VALUES (
    2,
    '2026-02-01',
    70000
);

SELECT throws_ok(
    $$INSERT INTO public.body_metrics (
          member_account_id,
          measurement_date,
          weight_grams
      ) VALUES (
          2,
          '2026-02-01',
          71000
      )$$,
    '23505',
    NULL,
    'a Member can have only one body metric per date'
);

DELETE FROM public.accounts
WHERE id = 3;

SELECT throws_ok(
    $$SELECT public.create_member_records(
          '00000000-0000-0000-0000-000000000003',
          'member.b.local@example.test',
          'Local Member B',
          '+65 8000 0002',
          '1996-02-02',
          '2026-03-01',
          '2026-03-31',
          0,
          'CARD',
          '2026-03-01T00:00:00Z',
          NULL,
          1
      )$$,
    '23514',
    NULL,
    'Member creation rejects an invalid Payment'
);

SELECT is(
    (SELECT count(*)::integer
       FROM public.accounts
      WHERE auth_user_id = '00000000-0000-0000-0000-000000000003'),
    0,
    'failed Member creation rolls back the Account and related records'
);

CREATE TEMPORARY TABLE failed_member_number_sequence AS
SELECT last_value
FROM public.member_number_sequence;

SELECT lives_ok(
    $$SELECT public.create_member_records(
          '00000000-0000-0000-0000-000000000003',
          'member.b.local@example.test',
          'Local Member B',
          '+65 8000 0002',
          '1996-02-02',
          '2026-03-01',
          '2026-03-31',
          10000,
          'CARD',
          '2026-03-01T00:00:00Z',
          NULL,
          1
      )$$,
    'valid Member, Membership, and Payment records are created atomically'
);

SELECT is(
    (SELECT member_number
       FROM public.member_profiles
      WHERE account_id = (
          SELECT id
          FROM public.accounts
          WHERE auth_user_id = '00000000-0000-0000-0000-000000000003'
      )),
    (
        SELECT 'M' || lpad((last_value + 1)::text, 4, '0')
        FROM failed_member_number_sequence
    ),
    'rolled-back sequence values are not reused when Member creation is retried'
);

SELECT is(
    (SELECT count(*)::integer
       FROM public.memberships
      WHERE member_account_id = (
          SELECT id
          FROM public.accounts
          WHERE auth_user_id = '00000000-0000-0000-0000-000000000003'
      )),
    1,
    'successful Member creation includes its initial Membership'
);

SELECT is(
    (SELECT count(*)::integer
       FROM public.payments payment
       JOIN public.memberships membership ON membership.id = payment.membership_id
      WHERE membership.member_account_id = (
          SELECT id
          FROM public.accounts
          WHERE auth_user_id = '00000000-0000-0000-0000-000000000003'
      )),
    1,
    'successful Member creation includes its initial Payment'
);

SELECT * FROM finish();

ROLLBACK;
