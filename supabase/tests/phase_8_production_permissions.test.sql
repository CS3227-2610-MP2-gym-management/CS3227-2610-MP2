begin;

create extension if not exists pgtap with schema extensions;

select plan(6);

select is(
  has_sequence_privilege('service_role', 'public.member_number_sequence', 'USAGE'),
  true,
  'protected account creation can allocate Member numbers'
);
select is(
  has_sequence_privilege('authenticated', 'public.member_number_sequence', 'USAGE'),
  false,
  'desktop users cannot allocate Member numbers directly'
);
select is(
  has_sequence_privilege('anon', 'public.member_number_sequence', 'USAGE'),
  false,
  'signed-out callers cannot allocate Member numbers'
);

select is(
  (
    select prosecdef
      from pg_catalog.pg_proc
     where oid = 'public.create_member_records(uuid,text,text,text,date,date,date,bigint,text,timestamp with time zone,text,bigint)'::regprocedure
  ),
  true,
  'protected Member creation runs with its function owner privileges'
);

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  confirmation_token, recovery_token, email_change_token_new, email_change,
  raw_app_meta_data, raw_user_meta_data, is_super_admin, created_at, updated_at
) values (
  '00000000-0000-0000-0000-000000000000',
  '00000000-0000-0000-0000-000000000099',
  'authenticated', 'authenticated', 'phase8.permission@example.test',
  extensions.crypt('PermissionTest!2026', extensions.gen_salt('bf')), now(),
  '', '', '', '', '{"provider":"email","providers":["email"]}', '{}', false,
  now(), now()
);

set local role service_role;
select lives_ok(
  $$select public.create_member_records(
      '00000000-0000-0000-0000-000000000099',
      'phase8.permission@example.test',
      'Phase 8 Permission Test',
      '+65 8000 0099',
      '1995-01-01',
      '2030-01-01',
      '2030-01-31',
      8000,
      'CARD',
      '2030-01-01T00:00:00Z',
      'PHASE8-PERMISSION',
      1
    )$$,
  'the protected server role can create all Member records atomically'
);

reset role;
select is(
  (
    select count(*)::integer
      from public.member_profiles
     where account_id = (
       select id from public.accounts
        where auth_user_id = '00000000-0000-0000-0000-000000000099'
     )
  ),
  1,
  'protected Member creation includes the Member profile'
);

select * from finish();

rollback;
