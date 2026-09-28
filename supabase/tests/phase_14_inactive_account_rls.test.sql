begin;

create extension if not exists pgtap with schema extensions;
select plan(9);

insert into public.memberships (member_account_id, start_date, expiry_date)
values (2, (now() at time zone 'Asia/Singapore')::date,
           (now() at time zone 'Asia/Singapore')::date);
insert into public.workouts (member_account_id, started_at, ended_at)
values (2, now() - interval '1 hour', now());
insert into public.body_metrics (member_account_id, measurement_date, weight_grams)
values (2, (now() at time zone 'Asia/Singapore')::date, 70000);
insert into public.announcements (title, content, published_at, created_by_account_id)
values ('Inactive session test', 'Published announcement', now(), 1);

update public.accounts set is_active = false where id = 2;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);

select is(public.current_account_id(), null::bigint,
  'an inactive Member has no current account');
select is((select count(*)::integer from public.accounts), 0,
  'an inactive Member cannot read accounts');
select is((select count(*)::integer from public.member_profiles), 0,
  'an inactive Member cannot read their profile');
select is((select count(*)::integer from public.memberships), 0,
  'an inactive Member cannot read Memberships');
select is((select count(*)::integer from public.workouts), 0,
  'an inactive Member cannot read Workouts');
select is((select count(*)::integer from public.body_metrics), 0,
  'an inactive Member cannot read body-mass data');
select is((select count(*)::integer from public.announcements), 0,
  'an inactive Member cannot read published announcements');
select throws_ok(
  $$select public.member_check_in()$$,
  '42501', null,
  'an inactive Member cannot check in');
select throws_ok(
  $$insert into public.body_metrics (member_account_id, measurement_date, weight_grams)
    values (2, current_date - 1, 71000)$$,
  '42501', null,
  'an inactive Member cannot create body-mass data');

select * from finish();
rollback;
