CREATE FUNCTION public.current_account_id()
RETURNS bigint
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT id
    FROM public.accounts
    WHERE auth_user_id = (SELECT auth.uid())
      AND is_active
$$;

CREATE FUNCTION public.is_active_owner()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.accounts
        WHERE auth_user_id = (SELECT auth.uid())
          AND role = 'OWNER'
          AND is_active
    )
$$;

REVOKE ALL ON FUNCTION public.current_account_id() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_active_owner() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.current_account_id() TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_active_owner() TO authenticated;

GRANT SELECT ON TABLE public.accounts TO authenticated;
GRANT SELECT ON TABLE public.member_profiles TO authenticated;
GRANT UPDATE (full_name, phone_number, date_of_birth)
    ON TABLE public.member_profiles TO authenticated;
GRANT SELECT ON TABLE public.memberships TO authenticated;
GRANT UPDATE (is_active, updated_at) ON TABLE public.memberships TO authenticated;
GRANT SELECT ON TABLE public.payments TO authenticated;
GRANT SELECT, INSERT ON TABLE public.expenses TO authenticated;
GRANT SELECT, INSERT ON TABLE public.announcements TO authenticated;
GRANT UPDATE (withdrawn_at, updated_at) ON TABLE public.announcements TO authenticated;
GRANT SELECT, INSERT ON TABLE public.workouts TO authenticated;
GRANT UPDATE (ended_at, notes, updated_at) ON TABLE public.workouts TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.workout_sets TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.body_metrics TO authenticated;

GRANT USAGE, SELECT ON SEQUENCE public.expenses_id_seq TO authenticated;
GRANT USAGE, SELECT ON SEQUENCE public.announcements_id_seq TO authenticated;
GRANT USAGE, SELECT ON SEQUENCE public.workouts_id_seq TO authenticated;
GRANT USAGE, SELECT ON SEQUENCE public.workout_sets_id_seq TO authenticated;
GRANT USAGE, SELECT ON SEQUENCE public.body_metrics_id_seq TO authenticated;

DROP POLICY accounts_select_own ON public.accounts;

CREATE POLICY accounts_select_own
ON public.accounts
FOR SELECT
TO authenticated
USING (
    auth_user_id = (SELECT auth.uid())
    AND is_active
);

CREATE POLICY accounts_select_owner
ON public.accounts
FOR SELECT
TO authenticated
USING ((SELECT public.is_active_owner()));

CREATE POLICY member_profiles_select_self
ON public.member_profiles
FOR SELECT
TO authenticated
USING (account_id = (SELECT public.current_account_id()));

CREATE POLICY member_profiles_select_owner
ON public.member_profiles
FOR SELECT
TO authenticated
USING ((SELECT public.is_active_owner()));

CREATE POLICY member_profiles_update_self
ON public.member_profiles
FOR UPDATE
TO authenticated
USING (account_id = (SELECT public.current_account_id()))
WITH CHECK (account_id = (SELECT public.current_account_id()));

CREATE POLICY member_profiles_update_owner
ON public.member_profiles
FOR UPDATE
TO authenticated
USING ((SELECT public.is_active_owner()))
WITH CHECK ((SELECT public.is_active_owner()));

CREATE POLICY memberships_select_self
ON public.memberships
FOR SELECT
TO authenticated
USING (member_account_id = (SELECT public.current_account_id()));

CREATE POLICY memberships_select_owner
ON public.memberships
FOR SELECT
TO authenticated
USING ((SELECT public.is_active_owner()));

CREATE POLICY memberships_update_owner
ON public.memberships
FOR UPDATE
TO authenticated
USING ((SELECT public.is_active_owner()))
WITH CHECK ((SELECT public.is_active_owner()));

CREATE POLICY payments_select_self
ON public.payments
FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.memberships
        WHERE memberships.id = payments.membership_id
          AND memberships.member_account_id = (SELECT public.current_account_id())
    )
);

CREATE POLICY payments_select_owner
ON public.payments
FOR SELECT
TO authenticated
USING ((SELECT public.is_active_owner()));

CREATE POLICY expenses_select_owner
ON public.expenses
FOR SELECT
TO authenticated
USING ((SELECT public.is_active_owner()));

CREATE POLICY expenses_insert_owner
ON public.expenses
FOR INSERT
TO authenticated
WITH CHECK (
    (SELECT public.is_active_owner())
    AND recorded_by_account_id = (SELECT public.current_account_id())
);

CREATE POLICY announcements_select_published
ON public.announcements
FOR SELECT
TO authenticated
USING (
    withdrawn_at IS NULL
    AND (SELECT public.current_account_id()) IS NOT NULL
);

CREATE POLICY announcements_select_owner
ON public.announcements
FOR SELECT
TO authenticated
USING ((SELECT public.is_active_owner()));

CREATE POLICY announcements_insert_owner
ON public.announcements
FOR INSERT
TO authenticated
WITH CHECK (
    (SELECT public.is_active_owner())
    AND created_by_account_id = (SELECT public.current_account_id())
);

CREATE POLICY announcements_update_owner
ON public.announcements
FOR UPDATE
TO authenticated
USING ((SELECT public.is_active_owner()))
WITH CHECK ((SELECT public.is_active_owner()));

CREATE POLICY workouts_select_self
ON public.workouts
FOR SELECT
TO authenticated
USING (member_account_id = (SELECT public.current_account_id()));

CREATE POLICY workouts_select_owner
ON public.workouts
FOR SELECT
TO authenticated
USING ((SELECT public.is_active_owner()));

CREATE POLICY workouts_insert_self
ON public.workouts
FOR INSERT
TO authenticated
WITH CHECK (member_account_id = (SELECT public.current_account_id()));

CREATE POLICY workouts_update_self
ON public.workouts
FOR UPDATE
TO authenticated
USING (member_account_id = (SELECT public.current_account_id()))
WITH CHECK (member_account_id = (SELECT public.current_account_id()));

CREATE POLICY workout_sets_select_self
ON public.workout_sets
FOR SELECT
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.workouts
        WHERE workouts.id = workout_sets.workout_id
          AND workouts.member_account_id = (SELECT public.current_account_id())
    )
);

CREATE POLICY workout_sets_select_owner
ON public.workout_sets
FOR SELECT
TO authenticated
USING ((SELECT public.is_active_owner()));

CREATE POLICY workout_sets_insert_self
ON public.workout_sets
FOR INSERT
TO authenticated
WITH CHECK (
    EXISTS (
        SELECT 1
        FROM public.workouts
        WHERE workouts.id = workout_sets.workout_id
          AND workouts.member_account_id = (SELECT public.current_account_id())
    )
);

CREATE POLICY workout_sets_update_self
ON public.workout_sets
FOR UPDATE
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.workouts
        WHERE workouts.id = workout_sets.workout_id
          AND workouts.member_account_id = (SELECT public.current_account_id())
    )
)
WITH CHECK (
    EXISTS (
        SELECT 1
        FROM public.workouts
        WHERE workouts.id = workout_sets.workout_id
          AND workouts.member_account_id = (SELECT public.current_account_id())
    )
);

CREATE POLICY workout_sets_delete_self
ON public.workout_sets
FOR DELETE
TO authenticated
USING (
    EXISTS (
        SELECT 1
        FROM public.workouts
        WHERE workouts.id = workout_sets.workout_id
          AND workouts.member_account_id = (SELECT public.current_account_id())
    )
);

CREATE POLICY body_metrics_select_self
ON public.body_metrics
FOR SELECT
TO authenticated
USING (member_account_id = (SELECT public.current_account_id()));

CREATE POLICY body_metrics_select_owner
ON public.body_metrics
FOR SELECT
TO authenticated
USING ((SELECT public.is_active_owner()));

CREATE POLICY body_metrics_insert_self
ON public.body_metrics
FOR INSERT
TO authenticated
WITH CHECK (member_account_id = (SELECT public.current_account_id()));

CREATE POLICY body_metrics_update_self
ON public.body_metrics
FOR UPDATE
TO authenticated
USING (member_account_id = (SELECT public.current_account_id()))
WITH CHECK (member_account_id = (SELECT public.current_account_id()));

CREATE POLICY body_metrics_delete_self
ON public.body_metrics
FOR DELETE
TO authenticated
USING (member_account_id = (SELECT public.current_account_id()));
