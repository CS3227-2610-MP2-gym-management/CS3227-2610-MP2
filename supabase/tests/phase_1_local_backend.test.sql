BEGIN;

SELECT plan(5);

SELECT is(
    (SELECT count(*)::integer FROM auth.users),
    3,
    'the local seed creates exactly three Auth users'
);

SELECT is(
    (SELECT count(*)::integer FROM auth.identities WHERE provider = 'email'),
    3,
    'every local user has an email identity'
);

SELECT is(
    (SELECT count(*)::integer FROM auth.users WHERE email_confirmed_at IS NOT NULL),
    3,
    'every local user is confirmed for password login'
);

SELECT is(
    (SELECT count(*)::integer FROM auth.users WHERE email LIKE '%.test'),
    3,
    'all seeded addresses use the reserved test domain'
);

SELECT is(
    (SELECT count(*)::integer
       FROM auth.users
      WHERE id IN (
          '00000000-0000-0000-0000-000000000001',
          '00000000-0000-0000-0000-000000000002',
          '00000000-0000-0000-0000-000000000003'
      )),
    3,
    'local users keep stable identifiers across database resets'
);

SELECT * FROM finish();

ROLLBACK;
