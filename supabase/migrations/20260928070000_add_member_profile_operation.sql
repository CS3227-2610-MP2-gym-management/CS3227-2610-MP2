CREATE FUNCTION public.update_member_records(
    p_member_account_id bigint,
    p_email text,
    p_phone_number text,
    p_update_identity boolean,
    p_full_name text,
    p_date_of_birth date
)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.accounts
        WHERE id = p_member_account_id AND role = 'MEMBER'
    ) THEN
        RAISE EXCEPTION 'Member not found' USING ERRCODE = 'P0002';
    END IF;

    UPDATE public.accounts
    SET email = lower(trim(p_email)),
        updated_at = now()
    WHERE id = p_member_account_id;

    UPDATE public.member_profiles
    SET phone_number = trim(p_phone_number),
        full_name = CASE WHEN p_update_identity THEN trim(p_full_name) ELSE full_name END,
        date_of_birth = CASE WHEN p_update_identity THEN p_date_of_birth ELSE date_of_birth END
    WHERE account_id = p_member_account_id;
END;
$$;

REVOKE ALL ON FUNCTION public.update_member_records(
    bigint,
    text,
    text,
    boolean,
    text,
    date
) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.update_member_records(
    bigint,
    text,
    text,
    boolean,
    text,
    date
) TO service_role;
