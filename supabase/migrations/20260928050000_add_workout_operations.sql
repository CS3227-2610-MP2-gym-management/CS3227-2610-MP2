CREATE FUNCTION public.save_member_workout(
    p_workout_id bigint,
    p_started_at timestamp with time zone,
    p_ended_at timestamp with time zone,
    p_notes text,
    p_sets jsonb
)
RETURNS public.workouts
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    account_id bigint := public.current_account_id();
    saved_workout public.workouts;
    set_item jsonb;
    set_position integer := 0;
BEGIN
    IF account_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.accounts
        WHERE id = account_id AND role = 'MEMBER' AND is_active
    ) THEN
        RAISE EXCEPTION 'An active Member account is required'
            USING ERRCODE = '42501';
    END IF;
    IF p_ended_at IS NOT NULL AND p_ended_at <= p_started_at THEN
        RAISE EXCEPTION 'Workout end time must be after its start time'
            USING ERRCODE = '23514';
    END IF;
    IF p_sets IS NULL OR jsonb_typeof(p_sets) <> 'array' THEN
        RAISE EXCEPTION 'Workout sets must be an array' USING ERRCODE = '23514';
    END IF;

    IF p_workout_id IS NULL THEN
        INSERT INTO public.workouts (
            member_account_id, started_at, ended_at, notes
        ) VALUES (
            account_id, p_started_at, p_ended_at, nullif(trim(p_notes), '')
        ) RETURNING * INTO saved_workout;
    ELSE
        UPDATE public.workouts
        SET started_at = p_started_at,
            ended_at = p_ended_at,
            notes = nullif(trim(p_notes), ''),
            updated_at = now()
        WHERE id = p_workout_id
          AND member_account_id = account_id
        RETURNING * INTO saved_workout;

        IF saved_workout.id IS NULL THEN
            RAISE EXCEPTION 'Workout not found' USING ERRCODE = 'P0002';
        END IF;
        DELETE FROM public.workout_sets WHERE workout_id = saved_workout.id;
    END IF;

    FOR set_item IN SELECT value FROM jsonb_array_elements(p_sets)
    LOOP
        INSERT INTO public.workout_sets (
            workout_id,
            position,
            exercise_name,
            repetitions,
            duration_seconds,
            resistance_grams
        ) VALUES (
            saved_workout.id,
            set_position,
            trim(set_item ->> 'exercise_name'),
            (set_item ->> 'repetitions')::integer,
            (set_item ->> 'duration_seconds')::integer,
            (set_item ->> 'resistance_grams')::bigint
        );
        set_position := set_position + 1;
    END LOOP;

    RETURN saved_workout;
END;
$$;

CREATE FUNCTION public.delete_member_workout(p_workout_id bigint)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    account_id bigint := public.current_account_id();
BEGIN
    DELETE FROM public.workouts
    WHERE id = p_workout_id
      AND member_account_id = account_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Workout not found' USING ERRCODE = 'P0002';
    END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.save_member_workout(
    bigint,
    timestamp with time zone,
    timestamp with time zone,
    text,
    jsonb
) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.delete_member_workout(bigint) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.save_member_workout(
    bigint,
    timestamp with time zone,
    timestamp with time zone,
    text,
    jsonb
) TO authenticated;
GRANT EXECUTE ON FUNCTION public.delete_member_workout(bigint) TO authenticated;
