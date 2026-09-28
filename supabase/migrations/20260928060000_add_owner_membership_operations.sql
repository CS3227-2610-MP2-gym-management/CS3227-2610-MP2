CREATE FUNCTION public.owner_add_membership(
    p_member_account_id bigint,
    p_start_date date,
    p_expiry_date date,
    p_amount_cents bigint,
    p_method text,
    p_paid_at timestamp with time zone,
    p_reference text
)
RETURNS public.memberships
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    owner_id bigint := public.current_account_id();
    membership_id bigint;
    created_membership public.memberships;
BEGIN
    IF NOT public.is_active_owner() THEN
        RAISE EXCEPTION 'An active Owner account is required'
            USING ERRCODE = '42501';
    END IF;
    membership_id := public.add_membership_with_payment(
        p_member_account_id,
        p_start_date,
        p_expiry_date,
        p_amount_cents,
        p_method,
        p_paid_at,
        p_reference,
        owner_id
    );
    SELECT * INTO created_membership
    FROM public.memberships
    WHERE id = membership_id;
    RETURN created_membership;
END;
$$;

REVOKE ALL ON FUNCTION public.owner_add_membership(
    bigint,
    date,
    date,
    bigint,
    text,
    timestamp with time zone,
    text
) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.owner_add_membership(
    bigint,
    date,
    date,
    bigint,
    text,
    timestamp with time zone,
    text
) TO authenticated;
