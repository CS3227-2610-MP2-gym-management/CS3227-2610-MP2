ALTER TABLE public.accounts
ADD COLUMN is_gym_administrator boolean NOT NULL DEFAULT false;

UPDATE public.accounts
   SET is_gym_administrator = true
 WHERE id = (
    SELECT id
      FROM public.accounts
     WHERE role = 'OWNER'
     ORDER BY created_at, id
     LIMIT 1
 );

CREATE UNIQUE INDEX accounts_one_gym_administrator
ON public.accounts (is_gym_administrator)
WHERE is_gym_administrator;

ALTER TABLE public.accounts
ADD CONSTRAINT accounts_gym_administrator_is_owner
CHECK (NOT is_gym_administrator OR role = 'OWNER');

CREATE OR REPLACE FUNCTION public.bootstrap_initial_owner(p_auth_user_id uuid)
RETURNS bigint
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    owner_email text;
    owner_account_id bigint;
BEGIN
    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('gymflow.bootstrap_initial_owner', 0)
    );

    IF EXISTS (SELECT 1 FROM public.accounts) THEN
        RAISE EXCEPTION 'Initial Owner bootstrap requires an empty account table';
    END IF;

    SELECT lower(trim(email))
      INTO owner_email
      FROM auth.users
     WHERE id = p_auth_user_id
       AND email IS NOT NULL;

    IF owner_email IS NULL THEN
        RAISE EXCEPTION 'A matching Auth user with an email address is required';
    END IF;

    INSERT INTO public.accounts (
        auth_user_id,
        email,
        role,
        is_active,
        is_gym_administrator
    ) VALUES (
        p_auth_user_id,
        owner_email,
        'OWNER',
        true,
        true
    )
    RETURNING id INTO owner_account_id;

    RETURN owner_account_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.protect_final_active_owner()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
    IF OLD.is_gym_administrator
       AND (
           TG_OP = 'DELETE'
           OR NEW.role <> 'OWNER'
           OR NOT NEW.is_active
           OR NOT NEW.is_gym_administrator
       ) THEN
        RAISE EXCEPTION 'The Gym Administrator account cannot be changed or deactivated'
            USING ERRCODE = '22023';
    END IF;
    IF OLD.role = 'OWNER'
       AND OLD.is_active
       AND (
           TG_OP = 'DELETE'
           OR NEW.role <> 'OWNER'
           OR NOT NEW.is_active
       ) THEN
        PERFORM pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended('gymflow.active_owners', 0)
        );
        IF NOT EXISTS (
            SELECT 1
              FROM public.accounts
             WHERE role = 'OWNER'
               AND is_active
               AND id <> OLD.id
        ) THEN
            RAISE EXCEPTION 'At least one active Owner is required';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.set_owner_active(
    p_target_account_id bigint,
    p_active boolean,
    p_actor_account_id bigint
)
RETURNS boolean
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
    previous_active boolean;
BEGIN
    PERFORM pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('gymflow.active_owners', 0)
    );
    IF NOT EXISTS (
        SELECT 1
          FROM public.accounts
         WHERE id = p_actor_account_id
           AND role = 'OWNER'
           AND is_active
    ) THEN
        RAISE EXCEPTION 'An active Owner account is required' USING ERRCODE = '42501';
    END IF;
    IF p_target_account_id = p_actor_account_id THEN
        RAISE EXCEPTION 'Owners cannot change their own active state'
            USING ERRCODE = '22023';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM public.accounts
         WHERE id = p_target_account_id
           AND is_gym_administrator
    ) THEN
        RAISE EXCEPTION 'The Gym Administrator account cannot be activated or deactivated'
            USING ERRCODE = '22023';
    END IF;

    SELECT is_active
      INTO previous_active
      FROM public.accounts
     WHERE id = p_target_account_id
       AND role = 'OWNER'
     FOR UPDATE;
    IF previous_active IS NULL THEN
        RAISE EXCEPTION 'Owner account not found';
    END IF;
    IF previous_active = p_active THEN
        RETURN false;
    END IF;

    UPDATE public.accounts
       SET is_active = p_active,
           updated_at = now()
     WHERE id = p_target_account_id;

    INSERT INTO public.owner_account_audit (
        actor_account_id,
        target_account_id,
        action
    ) VALUES (
        p_actor_account_id,
        p_target_account_id,
        CASE WHEN p_active THEN 'ACTIVATED' ELSE 'DEACTIVATED' END
    );
    RETURN true;
END;
$$;

DROP POLICY accounts_select_owner ON public.accounts;

CREATE POLICY accounts_select_owner
ON public.accounts
FOR SELECT
TO authenticated
USING (
    (SELECT public.is_active_owner())
    AND NOT is_gym_administrator
);
