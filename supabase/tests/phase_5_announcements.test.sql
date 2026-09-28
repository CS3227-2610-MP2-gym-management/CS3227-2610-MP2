begin;

create extension if not exists pgtap with schema extensions;

select plan(5);

select is(
  has_function_privilege(
    'authenticated',
    'public.owner_withdraw_announcement(bigint)',
    'EXECUTE'
  ),
  true,
  'authenticated Owners can call the protected withdrawal operation'
);
select is(
  has_table_privilege('authenticated', 'public.announcements', 'UPDATE'),
  false,
  'desktop users cannot update announcement withdrawal state directly'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', true);

insert into public.announcements (
  title, content, published_at, created_by_account_id
) values (
  'Withdrawal contract', 'May only be withdrawn once', now(), 1
);

select lives_ok(
  $$select public.owner_withdraw_announcement(
      (select id from public.announcements where title = 'Withdrawal contract')
    )$$,
  'Owner can withdraw a published announcement'
);
select throws_ok(
  $$select public.owner_withdraw_announcement(
      (select id from public.announcements where title = 'Withdrawal contract')
    )$$,
  '23514',
  null,
  'Owner cannot withdraw an announcement twice'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', true);

select throws_ok(
  $$select public.owner_withdraw_announcement(
      (select id from public.announcements where title = 'Withdrawal contract')
    )$$,
  '42501',
  null,
  'Member cannot use the Owner withdrawal operation'
);

reset role;
select * from finish();
rollback;
