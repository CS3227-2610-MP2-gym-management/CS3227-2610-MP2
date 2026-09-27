-- Local-only Auth users. These fixed credentials contain no production data.
INSERT INTO auth.users (
    instance_id,
    id,
    aud,
    role,
    email,
    encrypted_password,
    email_confirmed_at,
    confirmation_token,
    recovery_token,
    email_change_token_new,
    email_change,
    raw_app_meta_data,
    raw_user_meta_data,
    is_super_admin,
    created_at,
    updated_at
)
VALUES
    (
        '00000000-0000-0000-0000-000000000000',
        '00000000-0000-0000-0000-000000000001',
        'authenticated',
        'authenticated',
        'owner.local@example.test',
        extensions.crypt('LocalOwner!2026', extensions.gen_salt('bf')),
        now(),
        '',
        '',
        '',
        '',
        '{"provider":"email","providers":["email"]}',
        '{}',
        false,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000',
        '00000000-0000-0000-0000-000000000002',
        'authenticated',
        'authenticated',
        'member.a.local@example.test',
        extensions.crypt('LocalMemberA!2026', extensions.gen_salt('bf')),
        now(),
        '',
        '',
        '',
        '',
        '{"provider":"email","providers":["email"]}',
        '{}',
        false,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000',
        '00000000-0000-0000-0000-000000000003',
        'authenticated',
        'authenticated',
        'member.b.local@example.test',
        extensions.crypt('LocalMemberB!2026', extensions.gen_salt('bf')),
        now(),
        '',
        '',
        '',
        '',
        '{"provider":"email","providers":["email"]}',
        '{}',
        false,
        now(),
        now()
    );

INSERT INTO auth.identities (
    provider_id,
    user_id,
    identity_data,
    provider,
    last_sign_in_at,
    created_at,
    updated_at
)
SELECT
    id::text,
    id,
    jsonb_build_object(
        'sub', id::text,
        'email', email,
        'email_verified', true,
        'phone_verified', false
    ),
    'email',
    now(),
    now(),
    now()
FROM auth.users
WHERE id IN (
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000003'
);
