begin;

create extension if not exists pgtap with schema extensions;

select plan(7);

select has_function(
  'public',
  'bootstrap_initial_owner',
  array['uuid'],
  'initial Owner bootstrap function exists'
);
select is(
  has_function_privilege(
    'authenticated',
    'public.bootstrap_initial_owner(uuid)',
    'EXECUTE'
  ),
  false,
  'desktop users cannot execute the initial Owner bootstrap'
);
select is(
  has_function_privilege(
    'service_role',
    'public.bootstrap_initial_owner(uuid)',
    'EXECUTE'
  ),
  true,
  'the protected server role can execute the initial Owner bootstrap'
);

truncate table public.accounts cascade;

set local role authenticated;
select throws_ok(
  $$select public.bootstrap_initial_owner(
      '00000000-0000-0000-0000-000000000001'
    )$$,
  '42501',
  null,
  'an authenticated desktop user cannot bootstrap an Owner'
);

reset role;
set local role service_role;
select lives_ok(
  $$select public.bootstrap_initial_owner(
      '00000000-0000-0000-0000-000000000001'
    )$$,
  'the protected server role can bootstrap the initial Owner'
);

reset role;
select is(
  (
    select email || ':' || role || ':' || is_active::text
      from public.accounts
     where auth_user_id = '00000000-0000-0000-0000-000000000001'
  ),
  'owner.local@example.test:OWNER:true',
  'bootstrap derives the email and creates an active Owner profile'
);
select throws_ok(
  $$select public.bootstrap_initial_owner(
      '00000000-0000-0000-0000-000000000002'
    )$$,
  'P0001',
  'Initial Owner bootstrap requires an empty account table',
  'bootstrap cannot create another account after initial provisioning'
);

select * from finish();

rollback;
