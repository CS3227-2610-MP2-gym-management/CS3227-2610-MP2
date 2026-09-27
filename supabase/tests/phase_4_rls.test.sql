begin;

create extension if not exists pgtap with schema extensions;

select plan(34);

insert into public.memberships (
  id, member_account_id, start_date, expiry_date
) values
  (101, 2, current_date - 10, current_date + 20),
  (102, 3, current_date - 5, current_date + 25);

insert into public.payments (
  id, membership_id, amount_cents, method, paid_at, recorded_by_account_id
) values
  (201, 101, 8000, 'CARD', now() - interval '10 days', 1),
  (202, 102, 8000, 'CASH', now() - interval '5 days', 1);

insert into public.expenses (
  id, expense_date, amount_cents, method, category, description, recorded_by_account_id
) values
  (301, current_date, 2500, 'CARD', 'EQUIPMENT', 'RLS test expense', 1);

insert into public.announcements (
  id, title, content, published_at, created_by_account_id, withdrawn_at
) values
  (401, 'Published notice', 'Visible to signed-in members.', now(), 1, null),
  (402, 'Owner draft', 'Not visible to members.', now() - interval '1 hour', 1, now());

insert into public.workouts (
  id, member_account_id, started_at, ended_at, notes
) values
  (501, 2, now() - interval '1 hour', now(), 'Member A workout'),
  (502, 3, now() - interval '2 hours', now() - interval '1 hour',
   'Member B workout');

insert into public.workout_sets (
  id, workout_id, position, exercise_name, repetitions, resistance_grams
) values
  (601, 501, 0, 'Squat', 8, 50000),
  (602, 502, 0, 'Bench press', 8, 40000);

insert into public.body_metrics (
  id, member_account_id, measurement_date, weight_grams
) values
  (701, 2, current_date, 65000),
  (702, 3, current_date, 70000);

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);

select is((select count(*)::integer from public.accounts), 1,
  'Member A can see exactly one account');
select is((select id from public.accounts), 2::bigint,
  'Member A can see only their own account');
select is((select count(*)::integer from public.member_profiles), 1,
  'Member A can see only their own profile');
select is((select count(*)::integer from public.memberships), 1,
  'Member A can see only their own membership');
select is((select count(*)::integer from public.payments), 1,
  'Member A can see only their own payment');
select is((select count(*)::integer from public.expenses), 0,
  'Member A cannot see business expenses');
select is((select count(*)::integer from public.announcements), 1,
  'Member A can see only published announcements');
select is((select count(*)::integer from public.workouts), 1,
  'Member A can see only their own workout');
select is((select count(*)::integer from public.workout_sets), 1,
  'Member A can see only sets from their own workout');
select is((select count(*)::integer from public.body_metrics), 1,
  'Member A can see only their own body metrics');
select throws_ok(
  $$insert into public.workouts (member_account_id, started_at) values (3, now())$$,
  '42501',
  null,
  'Member A cannot create a workout for Member B'
);
select throws_ok(
  $$insert into public.expenses (
      expense_date, amount_cents, method, category, recorded_by_account_id
    ) values (current_date, 100, 'CASH', 'OTHER', 2)$$,
  '42501',
  null,
  'Member A cannot create an expense'
);
select throws_ok(
  $$select public.create_member_records(
      '00000000-0000-0000-0000-000000000099'::uuid,
      'blocked@example.test',
      'Blocked User',
      '00000000',
      null::date,
      current_date,
      current_date + 30,
      1000,
      'CASH',
      now(),
      null,
      1
    )$$,
  '42501',
  null,
  'Member A cannot call the privileged member provisioning function'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);

select is((select count(*)::integer from public.accounts), 1,
  'Member B can see exactly one account');
select is((select count(*)::integer from public.member_profiles), 1,
  'Member B can see only their own profile');
select is((select count(*)::integer from public.workouts), 1,
  'Member B cannot see Member A workouts');
select is((select count(*)::integer from public.payments), 1,
  'Member B cannot see Member A payments');

reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);

select is((select count(*)::integer from public.accounts), 3,
  'Owner can see all accounts');
select is((select count(*)::integer from public.member_profiles), 2,
  'Owner can see all member profiles');
select is((select count(*)::integer from public.memberships), 2,
  'Owner can see all memberships');
select is((select count(*)::integer from public.payments), 2,
  'Owner can see all payments');
select is((select count(*)::integer from public.expenses), 1,
  'Owner can see business expenses');
select is((select count(*)::integer from public.announcements), 2,
  'Owner can see published and withdrawn announcements');
select is((select count(*)::integer from public.workouts), 2,
  'Owner can see all workouts');
select is((select count(*)::integer from public.workout_sets), 2,
  'Owner can see all workout sets');
select is((select count(*)::integer from public.body_metrics), 2,
  'Owner can see all body metrics');
select lives_ok(
  $$insert into public.expenses (
      expense_date, amount_cents, method, category, recorded_by_account_id
    ) values (current_date, 500, 'CASH', 'OTHER', 1)$$,
  'Owner can create an expense attributed to themselves'
);
select throws_ok(
  $$insert into public.expenses (
      expense_date, amount_cents, method, category, recorded_by_account_id
    ) values (current_date, 500, 'CASH', 'OTHER', 2)$$,
  '42501',
  null,
  'Owner cannot attribute an expense to another account'
);

reset role;
set local role anon;
select set_config('request.jwt.claim.sub', '', true);

select throws_ok(
  $$select * from public.accounts$$,
  '42501',
  null,
  'Unauthenticated users cannot read accounts'
);
select throws_ok(
  $$select * from public.announcements$$,
  '42501',
  null,
  'Unauthenticated users cannot read announcements'
);
select throws_ok(
  $$select public.current_account_id()$$,
  '42501',
  null,
  'Unauthenticated users cannot execute authorization helpers'
);

reset role;
update public.accounts set is_active = false where id = 3;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);

select is((select count(*)::integer from public.accounts), 0,
  'An inactive Member B cannot read their account');
select is((select count(*)::integer from public.member_profiles), 0,
  'An inactive Member B cannot read their profile');
select is((select count(*)::integer from public.announcements), 0,
  'An inactive Member B cannot read published announcements');

reset role;
select * from finish();
rollback;
