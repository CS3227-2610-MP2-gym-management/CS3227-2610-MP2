CREATE FUNCTION public.owner_withdraw_announcement(p_announcement_id bigint)
RETURNS public.announcements
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    withdrawn_announcement public.announcements;
BEGIN
    IF NOT public.is_active_owner() THEN
        RAISE EXCEPTION 'An active Owner account is required'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.announcements
    SET withdrawn_at = now(),
        updated_at = now()
    WHERE id = p_announcement_id
      AND withdrawn_at IS NULL
    RETURNING * INTO withdrawn_announcement;

    IF withdrawn_announcement.id IS NULL THEN
        RAISE EXCEPTION 'Announcement not found or already withdrawn'
            USING ERRCODE = '23514';
    END IF;

    RETURN withdrawn_announcement;
END;
$$;

REVOKE UPDATE (withdrawn_at, updated_at) ON TABLE public.announcements FROM authenticated;
REVOKE ALL ON FUNCTION public.owner_withdraw_announcement(bigint) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.owner_withdraw_announcement(bigint) TO authenticated;
