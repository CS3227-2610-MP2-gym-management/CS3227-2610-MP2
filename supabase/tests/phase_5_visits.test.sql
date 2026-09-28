begin;

create extension if not exists pgtap with schema extensions;

select plan(17);

insert into public.memberships (
  id, member_account_id, start_date, expiry_date
) values (
  801, 2, current_date - 1, current_date + 1
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);

select has_function('public', 'member_check_in', array[]::text[],
  'Member check-in accepts no client timestamp');
select has_function('public', 'member_check_out', array[]::text[],
  'Member check-out accepts no client timestamp');
select hasnt_function('public', 'member_check_in', array['timestamp with time zone'],
  'Timestamped Member check-in is unavailable');
select hasnt_function('public', 'member_check_out', array['timestamp with time zone'],
  'Timestamped Member check-out is unavailable');

select lives_ok(
  $$select public.member_check_in()$$,
  'Member A can check in with an active Membership'
);
select is((select count(*)::integer from public.visits), 1,
  'Member A can see their open Visit');
select throws_ok(
  $$select public.member_check_in()$$,
  '23505',
  null,
  'Member A cannot have two open Visits'
);
select lives_ok(
  $$select public.member_check_out()$$,
  'Member A can check out their open Visit'
);
select is((select count(*)::integer from public.visits where exited_at is null), 0,
  'Member A has no open Visit after check-out');

reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000003', true);

select is((select count(*)::integer from public.visits), 0,
  'Member B cannot see Member A Visits');
select throws_ok(
  $$select public.member_check_in()$$,
  '23514',
  null,
  'Member B cannot check in without an active Membership'
);
select throws_ok(
  $$select public.owner_correct_visit(1, now() - interval '2 hours', now(), 'Blocked')$$,
  '42501',
  null,
  'Member B cannot use the Owner correction operation'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);

select is((select count(*)::integer from public.visits), 1,
  'Owner can see Member Visits');
select lives_ok(
  $$select public.owner_correct_visit(
      (select id from public.visits limit 1),
      now() - interval '2 hours',
      now() - interval '1 hour',
      'Corrected in Phase 5 test'
    )$$,
  'Owner can correct a Visit'
);
select is(
  (select correction_reason from public.visits limit 1),
  'Corrected in Phase 5 test',
  'Visit correction keeps its audit reason'
);
select throws_ok(
  $$select public.owner_correct_visit(
      (select id from public.visits limit 1),
      (select entered_at from public.visits limit 1),
      (select exited_at from public.visits limit 1),
      'No timestamps changed'
    )$$,
  '23514',
  null,
  'Owner cannot record a correction without changing a timestamp'
);

reset role;
set local role anon;
select set_config('request.jwt.claim.sub', '', true);
select throws_ok(
  $$select public.member_check_in()$$,
  '42501',
  null,
  'Unauthenticated users cannot call Visit operations'
);

reset role;
select * from finish();
rollback;
