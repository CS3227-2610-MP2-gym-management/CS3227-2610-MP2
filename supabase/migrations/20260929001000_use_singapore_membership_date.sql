CREATE OR REPLACE FUNCTION public.require_current_membership_for_tracking()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
DECLARE
    today date := (now() AT TIME ZONE 'Asia/Singapore')::date;
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM public.memberships
        WHERE member_account_id = NEW.member_account_id
          AND is_active
          AND start_date <= today
          AND expiry_date >= today
    ) THEN
        RAISE EXCEPTION 'A current Membership is required to record Member activity'
            USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END;
$$;
