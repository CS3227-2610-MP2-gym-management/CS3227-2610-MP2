CREATE FUNCTION public.bootstrap_initial_owner(p_auth_user_id uuid)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    owner_email text;
    owner_account_id bigint;
BEGIN
    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('gymflow.bootstrap_initial_owner', 0)
    );

    IF EXISTS (SELECT 1 FROM public.accounts) THEN
        RAISE EXCEPTION 'Initial Owner bootstrap requires an empty account table';
    END IF;

    SELECT lower(trim(email))
      INTO owner_email
      FROM auth.users
     WHERE id = p_auth_user_id
       AND email IS NOT NULL;

    IF owner_email IS NULL THEN
        RAISE EXCEPTION 'A matching Auth user with an email address is required';
    END IF;

    INSERT INTO public.accounts (auth_user_id, email, role, is_active)
    VALUES (p_auth_user_id, owner_email, 'OWNER', true)
    RETURNING id INTO owner_account_id;

    RETURN owner_account_id;
END;
$$;

REVOKE ALL ON FUNCTION public.bootstrap_initial_owner(uuid)
FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.bootstrap_initial_owner(uuid)
TO service_role;
