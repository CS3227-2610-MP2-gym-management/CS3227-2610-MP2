DROP FUNCTION public.member_check_in(timestamp with time zone);

CREATE FUNCTION public.member_check_in()
RETURNS public.visits
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    account_id bigint := public.current_account_id();
    actual_entered_at timestamp with time zone := now();
    visit_date date := (actual_entered_at AT TIME ZONE 'Asia/Singapore')::date;
    created_visit public.visits;
BEGIN
    IF account_id IS NULL OR NOT EXISTS (
        SELECT 1
        FROM public.accounts
        WHERE id = account_id
          AND role = 'MEMBER'
          AND is_active
    ) THEN
        RAISE EXCEPTION 'An active Member account is required'
            USING ERRCODE = '42501';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM public.memberships
        WHERE member_account_id = account_id
          AND is_active
          AND start_date <= visit_date
          AND expiry_date >= visit_date
    ) THEN
        RAISE EXCEPTION 'An active Membership is required to check in'
            USING ERRCODE = '23514';
    END IF;

    INSERT INTO public.visits (member_account_id, entered_at)
    VALUES (account_id, actual_entered_at)
    RETURNING * INTO created_visit;

    RETURN created_visit;
END;
$$;

DROP FUNCTION public.member_check_out(timestamp with time zone);

CREATE FUNCTION public.member_check_out()
RETURNS public.visits
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    account_id bigint := public.current_account_id();
    actual_exited_at timestamp with time zone := now();
    closed_visit public.visits;
BEGIN
    IF account_id IS NULL OR NOT EXISTS (
        SELECT 1
        FROM public.accounts
        WHERE id = account_id
          AND role = 'MEMBER'
          AND is_active
    ) THEN
        RAISE EXCEPTION 'An active Member account is required'
            USING ERRCODE = '42501';
    END IF;

    UPDATE public.visits
    SET exited_at = actual_exited_at
    WHERE member_account_id = account_id
      AND visits.exited_at IS NULL
      AND entered_at < actual_exited_at
    RETURNING * INTO closed_visit;

    IF closed_visit.id IS NULL THEN
        RAISE EXCEPTION 'No open Visit can be checked out at that time'
            USING ERRCODE = '23514';
    END IF;

    RETURN closed_visit;
END;
$$;

REVOKE ALL ON FUNCTION public.member_check_in() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.member_check_out() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.member_check_in() TO authenticated;
GRANT EXECUTE ON FUNCTION public.member_check_out() TO authenticated;
