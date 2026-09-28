begin;

create extension if not exists pgtap with schema extensions;

select plan(21);

select is(
  has_function_privilege(
    'authenticated',
    'public.update_member_records(bigint,text,text,boolean,text,date)',
    'EXECUTE'
  ),
  false,
  'desktop users cannot call the privileged Member update function directly'
);
select is(
  has_function_privilege(
    'service_role',
    'public.update_member_records(bigint,text,text,boolean,text,date)',
    'EXECUTE'
  ),
  true,
  'the protected Edge Function can atomically update Member records'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);

select lives_ok(
  $$select public.owner_add_membership(
      2, current_date, current_date + 30, 8000, 'CARD', now(), 'PHASE5'
    )$$,
  'Owner can atomically add a Membership and Payment'
);
select is((select count(*)::integer from public.memberships), 1,
  'Owner Membership operation creates one Membership');
select is((select count(*)::integer from public.payments), 1,
  'Owner Membership operation creates its Payment in the same transaction');
select is(
  has_function_privilege(
    'authenticated',
    'public.owner_set_membership_active(bigint,boolean)',
    'EXECUTE'
  ),
  true,
  'authenticated Owners can call the protected Membership state operation'
);
select is(
  has_table_privilege('authenticated', 'public.memberships', 'UPDATE'),
  false,
  'desktop users cannot update Membership state directly'
);
select lives_ok(
  $$select public.owner_set_membership_active(
      (select membership_id from public.payments where reference = 'PHASE5'), false
    )$$,
  'Owner can deactivate a Membership'
);
select lives_ok(
  $$select public.owner_set_membership_active(
      (select membership_id from public.payments where reference = 'PHASE5'), true
    )$$,
  'Owner can reactivate a current Membership'
);
reset role;
insert into public.memberships (
  member_account_id, start_date, expiry_date, is_active
) values (
  2, current_date - 60, current_date - 30, false
);
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
select throws_ok(
  $$select public.owner_set_membership_active(
      (select id from public.memberships where expiry_date = current_date - 30), true
    )$$,
  '23514',
  null,
  'Owner cannot reactivate an expired Membership'
);
select lives_ok(
  $$select public.owner_set_membership_active(
      (select membership_id from public.payments where reference = 'PHASE5'), false
    )$$,
  'Owner can deactivate the current Membership before an overlap test'
);
reset role;
insert into public.memberships (
  member_account_id, start_date, expiry_date, is_active
) values (
  2, current_date, current_date + 30, true
);
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
select throws_ok(
  $$select public.owner_set_membership_active(
      (select membership_id from public.payments where reference = 'PHASE5'), true
    )$$,
  '23P01',
  null,
  'Owner cannot reactivate a Membership that overlaps an active Membership'
);
select throws_ok(
  $$select public.save_member_workout(
      null, now() - interval '1 hour', now(), null, '[]'::jsonb
    )$$,
  '42501',
  null,
  'Owner cannot use the Member Workout operation'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);

select throws_ok(
  $$select public.owner_add_membership(
      2, current_date + 31, current_date + 60, 8000, 'CARD', now(), null
    )$$,
  '42501',
  null,
  'Member cannot use the Owner Membership operation'
);
select lives_ok(
  $$select public.save_member_workout(
      null,
      now() - interval '1 hour',
      now(),
      'Initial',
      '[{"exercise_name":"Squat","repetitions":8,"resistance_grams":50000}]'::jsonb
    )$$,
  'Member can atomically save a Workout and its sets'
);
select is((select count(*)::integer from public.workouts), 1,
  'Saved Workout is visible to its Member');
select is((select count(*)::integer from public.workout_sets), 1,
  'Initial Workout set is saved');
select lives_ok(
  $$select public.save_member_workout(
      (select id from public.workouts limit 1),
      now() - interval '2 hours',
      now() - interval '1 hour',
      'Updated',
      '[{"exercise_name":"Row","repetitions":10},'
        '{"exercise_name":"Plank","duration_seconds":60}]'::jsonb
    )$$,
  'Member can atomically replace a Workout and its sets'
);
select is((select count(*)::integer from public.workout_sets), 2,
  'Replacing a Workout replaces rather than appends sets');
select lives_ok(
  $$select public.delete_member_workout((select id from public.workouts limit 1))$$,
  'Member can delete their Workout'
);
select is((select count(*)::integer from public.workouts), 0,
  'Deleting a Workout also removes it from history');

reset role;
select * from finish();
rollback;
