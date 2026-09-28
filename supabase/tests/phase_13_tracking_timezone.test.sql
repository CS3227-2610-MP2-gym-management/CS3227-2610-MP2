begin;

create extension if not exists pgtap with schema extensions;
select plan(4);

insert into public.memberships (member_account_id, start_date, expiry_date)
values (3, (now() at time zone 'Asia/Singapore')::date,
           (now() at time zone 'Asia/Singapore')::date);

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);
set local time zone 'Pacific/Kiritimati';
select lives_ok(
  $$insert into public.workouts (member_account_id, started_at, ended_at)
    values (3, now() - interval '1 hour', now())$$,
  'Workout creation uses the Singapore membership date in an advanced session zone');
select is((select count(*)::integer from public.workouts where member_account_id = 3), 1,
  'the workout is saved once');

set local time zone 'Etc/GMT+12';
select lives_ok(
  $$insert into public.body_metrics (member_account_id, measurement_date, weight_grams)
    values (3, (now() at time zone 'Asia/Singapore')::date, 70000)$$,
  'body-mass creation uses the Singapore membership date in a delayed session zone');
select is((select count(*)::integer from public.body_metrics where member_account_id = 3), 1,
  'the body-mass reading is saved once');

select * from finish();
rollback;
