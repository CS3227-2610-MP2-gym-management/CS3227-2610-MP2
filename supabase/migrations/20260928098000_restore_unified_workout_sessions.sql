-- Keep the deployment schema compatible while restoring Workout as the
-- authoritative Member session aggregate used by the desktop application.

INSERT INTO public.workouts (member_account_id, started_at, ended_at, created_at, updated_at)
SELECT visit.member_account_id, visit.entered_at, NULL, visit.created_at, statement_timestamp()
FROM public.visits visit
WHERE visit.exited_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM public.workouts workout
      WHERE workout.member_account_id = visit.member_account_id
        AND workout.ended_at IS NULL
  );

CREATE OR REPLACE FUNCTION public.member_check_in()
RETURNS public.visits
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    account_id bigint := public.current_account_id();
    actual_entered_at timestamp with time zone := statement_timestamp();
    visit_date date := (actual_entered_at AT TIME ZONE 'Asia/Singapore')::date;
    created_visit public.visits;
BEGIN
    IF account_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM public.accounts
        WHERE id = account_id AND role = 'MEMBER' AND is_active
    ) THEN
        RAISE EXCEPTION 'An active Member account is required'
            USING ERRCODE = '42501';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.memberships
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

    INSERT INTO public.workouts (member_account_id, started_at)
    VALUES (account_id, actual_entered_at);

    RETURN created_visit;
END;
$$;

CREATE FUNCTION public.check_out_member_workout(
    p_workout_id bigint,
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
    actual_exited_at timestamp with time zone := statement_timestamp();
    closed_workout public.workouts;
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
    IF p_sets IS NULL OR jsonb_typeof(p_sets) <> 'array' THEN
        RAISE EXCEPTION 'Workout sets must be an array' USING ERRCODE = '23514';
    END IF;

    UPDATE public.workouts
    SET ended_at = actual_exited_at,
        notes = nullif(trim(p_notes), ''),
        updated_at = actual_exited_at
    WHERE id = p_workout_id
      AND member_account_id = account_id
      AND ended_at IS NULL
      AND started_at < actual_exited_at
    RETURNING * INTO closed_workout;

    IF closed_workout.id IS NULL THEN
        RAISE EXCEPTION 'No open Workout can be checked out at that time'
            USING ERRCODE = '23514';
    END IF;

    DELETE FROM public.workout_sets WHERE workout_id = closed_workout.id;
    FOR set_item IN SELECT value FROM jsonb_array_elements(p_sets)
    LOOP
        INSERT INTO public.workout_sets (
            workout_id, position, exercise_name, repetitions,
            duration_seconds, resistance_grams
        ) VALUES (
            closed_workout.id,
            set_position,
            trim(set_item ->> 'exercise_name'),
            (set_item ->> 'repetitions')::integer,
            (set_item ->> 'duration_seconds')::integer,
            (set_item ->> 'resistance_grams')::bigint
        );
        set_position := set_position + 1;
    END LOOP;

    UPDATE public.visits
    SET exited_at = actual_exited_at
    WHERE member_account_id = account_id AND exited_at IS NULL;

    RETURN closed_workout;
END;
$$;

REVOKE ALL ON FUNCTION public.check_out_member_workout(bigint, text, jsonb)
    FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.check_out_member_workout(bigint, text, jsonb)
    TO authenticated;
