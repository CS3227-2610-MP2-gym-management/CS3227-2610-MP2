begin;

create extension if not exists pgtap with schema extensions;
select plan(12);

select is((select count(*)::integer from public.accounts where is_gym_administrator), 1,
  'exactly one Gym Administrator exists after seed');
select is((select id from public.accounts where is_gym_administrator), 1::bigint,
  'the seeded root Owner is the Gym Administrator');
select throws_ok(
  $$update public.accounts set is_active = false where id = 1$$,
  '22023', 'The Gym Administrator account cannot be changed or deactivated',
  'the root Owner cannot be deactivated directly');
select throws_ok(
  $$delete from public.accounts where id = 1$$,
  '22023', 'The Gym Administrator account cannot be changed or deactivated',
  'the root Owner cannot be deleted');
select throws_ok(
  $$update public.accounts set role = 'MEMBER' where id = 1$$,
  '22023', 'Account roles cannot be changed',
  'the root Owner role cannot be changed');
select throws_ok(
  $$update public.accounts set is_gym_administrator = false where id = 1$$,
  '22023', 'The Gym Administrator account cannot be changed or deactivated',
  'the root Owner administrator flag cannot be cleared');
select throws_ok(
  $$update public.accounts set is_gym_administrator = true where id = 2$$,
  '23514', null,
  'a Member cannot be made the Gym Administrator');
select throws_ok(
  $$select public.set_owner_active(1, false, 999)$$,
  '42501', 'An active Owner account is required',
  'an unknown actor cannot change the root Owner');
set local role service_role;
select throws_ok(
  $$select public.set_owner_active(1, false, 1)$$,
  '22023', 'Owners cannot change their own active state',
  'the root Owner cannot deactivate themselves through the RPC');
reset role;
select is((select is_active from public.accounts where id = 1), true,
  'the root Owner remains active');
select is((select role from public.accounts where id = 1), 'OWNER',
  'the root Owner retains the Owner role');
select is((select count(*)::integer from public.owner_account_audit), 0,
  'rejected changes create no audit rows');

select * from finish();
rollback;
