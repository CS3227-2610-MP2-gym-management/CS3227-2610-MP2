begin;

create extension if not exists pgtap with schema extensions;

select plan(21);

select has_table('public', 'owner_account_audit', 'Owner audit table exists');
select has_function('public', 'create_owner_record', array['uuid', 'bigint'],
  'protected Owner creation function exists');
select has_function('public', 'set_owner_active', array['bigint', 'boolean', 'bigint'],
  'protected Owner activation function exists');
select has_trigger('public', 'accounts', 'accounts_protect_final_active_owner',
  'accounts protect the final active Owner');
select is(has_function_privilege('authenticated',
  'public.create_owner_record(uuid,bigint)', 'EXECUTE'), false,
  'desktop users cannot call Owner creation directly');
select is(has_function_privilege('authenticated',
  'public.set_owner_active(bigint,boolean,bigint)', 'EXECUTE'), false,
  'desktop users cannot call Owner activation directly');
select is(has_function_privilege('service_role',
  'public.create_owner_record(uuid,bigint)', 'EXECUTE'), true,
  'protected server role can create Owners');
select is(has_function_privilege('service_role',
  'public.set_owner_active(bigint,boolean,bigint)', 'EXECUTE'), true,
  'protected server role can change Owner activation');

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  confirmation_token, recovery_token, email_change_token_new, email_change,
  raw_app_meta_data, raw_user_meta_data, is_super_admin, created_at, updated_at
) values (
  '00000000-0000-0000-0000-000000000000',
  '00000000-0000-0000-0000-000000000004',
  'authenticated', 'authenticated', 'second.owner.local@example.test',
  extensions.crypt('SecondOwner!2026', extensions.gen_salt('bf')), now(),
  '', '', '', '', '{"provider":"email","providers":["email"]}', '{}', false,
  now(), now()
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
select throws_ok(
  $$select public.create_owner_record(
      '00000000-0000-0000-0000-000000000004', 1
    )$$,
  '42501', null,
  'an Owner desktop session cannot bypass the protected operation'
);

reset role;
set local role service_role;
select lives_ok(
  $$select public.create_owner_record(
      '00000000-0000-0000-0000-000000000004', 1
    )$$,
  'protected operation creates a second Owner'
);

reset role;
select is((select count(*)::integer from public.accounts where role = 'OWNER'), 2,
  'multiple Owner accounts are allowed');
select is((select count(*)::integer from public.owner_account_audit
  where action = 'CREATED'), 1, 'Owner creation is audited');

set local role service_role;
select throws_ok(
  $$select public.set_owner_active(
      1,
      false,
      (select id from public.accounts where email = 'second.owner.local@example.test')
    )$$,
  '22023', 'The Gym Administrator account cannot be activated or deactivated',
  'another Owner cannot deactivate the Gym Administrator through the RPC'
);
select lives_ok(
  $$select public.set_owner_active(
      (select id from public.accounts where email = 'second.owner.local@example.test'),
      false,
      1
    )$$,
  'an Owner can deactivate another Owner while one remains active'
);

reset role;
set local role service_role;
select throws_ok(
  $$select public.set_owner_active(1, false, 1)$$,
  '22023', 'Owners cannot change their own active state',
  'an Owner cannot deactivate their own account'
);

reset role;
select is((select count(*)::integer from public.owner_account_audit
  where action = 'DEACTIVATED'), 1, 'Owner deactivation is audited');
select throws_ok(
  $$update public.accounts set is_active = false where id = 1$$,
  '22023', 'The Gym Administrator account cannot be changed or deactivated',
  'the final active Owner cannot be deactivated'
);
select throws_ok(
  $$delete from public.accounts where id = 1$$,
  '22023', 'The Gym Administrator account cannot be changed or deactivated',
  'the final active Owner cannot be deleted'
);
select is((select count(*)::integer from public.accounts
  where role = 'OWNER' and is_active), 1, 'one active Owner always remains');

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);
select is((select count(*)::integer from public.owner_account_audit), 0,
  'Members cannot read Owner audit records');

reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);
select is((select count(*)::integer from public.owner_account_audit), 2,
  'active Owners can read Owner audit records');

select * from finish();

rollback;
