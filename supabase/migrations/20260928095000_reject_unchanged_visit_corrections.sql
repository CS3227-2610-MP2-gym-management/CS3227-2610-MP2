CREATE OR REPLACE FUNCTION public.owner_correct_visit(
    p_visit_id bigint,
    p_entered_at timestamp with time zone,
    p_exited_at timestamp with time zone,
    p_reason text
)
RETURNS public.visits
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    owner_id bigint := public.current_account_id();
    existing_visit public.visits;
    corrected_visit public.visits;
BEGIN
    IF NOT public.is_active_owner() THEN
        RAISE EXCEPTION 'An active Owner account is required'
            USING ERRCODE = '42501';
    END IF;
    IF p_exited_at IS NOT NULL AND p_exited_at <= p_entered_at THEN
        RAISE EXCEPTION 'Exit time must be after entry time'
            USING ERRCODE = '23514';
    END IF;
    IF length(trim(coalesce(p_reason, ''))) = 0 THEN
        RAISE EXCEPTION 'Correction reason is required'
            USING ERRCODE = '23514';
    END IF;

    SELECT * INTO existing_visit
    FROM public.visits
    WHERE id = p_visit_id
    FOR UPDATE;

    IF existing_visit.id IS NULL THEN
        RAISE EXCEPTION 'Visit not found' USING ERRCODE = 'P0002';
    END IF;
    IF existing_visit.entered_at IS NOT DISTINCT FROM p_entered_at
       AND existing_visit.exited_at IS NOT DISTINCT FROM p_exited_at THEN
        RAISE EXCEPTION 'At least one Visit timestamp must change'
            USING ERRCODE = '23514';
    END IF;

    UPDATE public.visits
    SET entered_at = p_entered_at,
        exited_at = p_exited_at,
        corrected_at = now(),
        corrected_by_account_id = owner_id,
        correction_reason = trim(p_reason)
    WHERE id = p_visit_id
    RETURNING * INTO corrected_visit;

    RETURN corrected_visit;
END;
$$;
