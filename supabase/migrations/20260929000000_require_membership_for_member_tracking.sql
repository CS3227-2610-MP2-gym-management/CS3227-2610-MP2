CREATE FUNCTION public.require_current_membership_for_tracking()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM public.memberships
        WHERE member_account_id = NEW.member_account_id
          AND is_active
          AND start_date <= current_date
          AND expiry_date >= current_date
    ) THEN
        RAISE EXCEPTION 'A current Membership is required to record Member activity'
            USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER workouts_require_current_membership
BEFORE INSERT ON public.workouts
FOR EACH ROW EXECUTE FUNCTION public.require_current_membership_for_tracking();

CREATE TRIGGER body_metrics_require_current_membership
BEFORE INSERT ON public.body_metrics
FOR EACH ROW EXECUTE FUNCTION public.require_current_membership_for_tracking();
