GRANT SELECT ON TABLE public.accounts TO authenticated;

CREATE POLICY accounts_select_own
ON public.accounts
FOR SELECT
TO authenticated
USING (auth_user_id = (SELECT auth.uid()));
