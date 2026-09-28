CREATE FUNCTION public.owner_set_membership_active(
    p_membership_id bigint,
    p_active boolean
)
RETURNS public.memberships
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    membership public.memberships;
    today date := (now() AT TIME ZONE 'Asia/Singapore')::date;
BEGIN
    IF NOT public.is_active_owner() THEN
        RAISE EXCEPTION 'An active Owner account is required'
            USING ERRCODE = '42501';
    END IF;

    SELECT * INTO membership
    FROM public.memberships
    WHERE id = p_membership_id
    FOR UPDATE;

    IF membership.id IS NULL THEN
        RAISE EXCEPTION 'Membership not found' USING ERRCODE = 'P0002';
    END IF;

    IF p_active AND membership.expiry_date < today THEN
        RAISE EXCEPTION 'Expired Memberships cannot be reactivated'
            USING ERRCODE = '23514';
    END IF;

    IF p_active AND EXISTS (
        SELECT 1
        FROM public.memberships AS other
        WHERE other.member_account_id = membership.member_account_id
          AND other.id <> membership.id
          AND other.is_active
          AND daterange(other.start_date, other.expiry_date, '[]')
              && daterange(membership.start_date, membership.expiry_date, '[]')
    ) THEN
        RAISE EXCEPTION 'Membership dates overlap an active Membership'
            USING ERRCODE = '23P01';
    END IF;

    UPDATE public.memberships
    SET is_active = p_active,
        updated_at = now()
    WHERE id = membership.id
    RETURNING * INTO membership;

    RETURN membership;
END;
$$;

REVOKE UPDATE (is_active, updated_at) ON TABLE public.memberships FROM authenticated;
REVOKE ALL ON FUNCTION public.owner_set_membership_active(bigint, boolean)
    FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.owner_set_membership_active(bigint, boolean)
    TO authenticated;
