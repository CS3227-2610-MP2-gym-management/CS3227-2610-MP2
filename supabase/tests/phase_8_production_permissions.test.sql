begin;

create extension if not exists pgtap with schema extensions;

select plan(3);

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

select * from finish();

rollback;
