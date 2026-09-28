begin;

create extension if not exists pgtap with schema extensions;

select plan(8);

delete from public.workouts where member_account_id = 2;
delete from public.visits where member_account_id = 2;
delete from public.memberships where member_account_id = 2;

insert into public.memberships (
  id, member_account_id, start_date, expiry_date
) values (
  810, 2, current_date - 1, current_date + 1
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);

select lives_ok(
  $$select public.member_check_in()$$,
  'Check-in creates a unified session'
);
select is((select count(*)::integer from public.visits where exited_at is null), 1,
  'Check-in retains the compatibility Visit');
select is((select count(*)::integer from public.workouts where ended_at is null), 1,
  'Check-in creates the open Workout restored by Home');

select lives_ok(
  $$select public.save_member_workout(
      (select id from public.workouts where ended_at is null),
      (select started_at from public.workouts where ended_at is null),
      null,
      'Navigation draft',
      '[{"exercise_name":"Squat","repetitions":8,"duration_seconds":null,"resistance_grams":60000}]'
    )$$,
  'Navigating away can save the active Workout draft'
);
select is((select count(*)::integer from public.workout_sets), 1,
  'The saved exercise remains attached to the open Workout');

select lives_ok(
  $$select public.check_out_member_workout(
      (select id from public.workouts where ended_at is null),
      'Navigation draft',
      '[{"exercise_name":"Squat","repetitions":8,"duration_seconds":null,"resistance_grams":60000}]'
    )$$,
  'Home can atomically save and close the restored Workout'
);
select is((select count(*)::integer from public.workouts where ended_at is null), 0,
  'Check-out closes the Workout');
select is((select count(*)::integer from public.visits where exited_at is null), 0,
  'Check-out also closes the compatibility Visit');

reset role;
select * from finish();
rollback;
