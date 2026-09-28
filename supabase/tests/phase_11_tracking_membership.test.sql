begin;

create extension if not exists pgtap with schema extensions;

select plan(7);

delete from public.workouts where member_account_id = 2;
delete from public.body_metrics where member_account_id = 2;
delete from public.memberships where member_account_id = 2;

insert into public.memberships (
  id, member_account_id, start_date, expiry_date
) values (
  820, 2, current_date - 1, current_date + 1
);

insert into public.workouts (
  id, member_account_id, started_at, ended_at, notes
) values (
  820, 2, now() - interval '1 hour', now(), 'Existing workout'
);

insert into public.body_metrics (
  id, member_account_id, measurement_date, weight_grams
) values (
  820, 2, current_date - 1, 70000
);

update public.memberships set is_active = false where id = 820;

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);

select is((select count(*)::integer from public.workouts), 1,
  'Member without current Membership can view an existing Workout');
select lives_ok(
  $$select public.save_member_workout(
      820,
      now() - interval '1 hour',
      now(),
      'Edited workout',
      '[]'
    )$$,
  'Member without current Membership can edit an existing Workout'
);
select throws_ok(
  $$select public.save_member_workout(
      null,
      now() - interval '1 hour',
      now(),
      'New workout',
      '[]'
    )$$,
  '42501',
  null,
  'Member without current Membership cannot create a Workout'
);
select is((select count(*)::integer from public.body_metrics), 1,
  'Member without current Membership can view an existing body-mass reading');
select lives_ok(
  $$update public.body_metrics set weight_grams = 69000 where id = 820$$,
  'Member without current Membership can edit an existing body-mass reading'
);
select throws_ok(
  $$insert into public.body_metrics (
      member_account_id, measurement_date, weight_grams
    ) values (2, current_date, 69000)$$,
  '42501',
  null,
  'Member without current Membership cannot create a body-mass reading'
);

reset role;
select matches(
  pg_get_functiondef('public.require_current_membership_for_tracking()'::regprocedure),
  'Asia/Singapore',
  'Membership tracking uses the Singapore business date instead of the database session date'
);
select * from finish();
rollback;
