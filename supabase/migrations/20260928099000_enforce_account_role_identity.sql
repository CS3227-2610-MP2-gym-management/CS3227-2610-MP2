CREATE FUNCTION public.prevent_account_role_change()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
    IF NEW.role IS DISTINCT FROM OLD.role THEN
        RAISE EXCEPTION 'Account roles cannot be changed'
            USING ERRCODE = '22023';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER accounts_prevent_role_change
BEFORE UPDATE OF role ON public.accounts
FOR EACH ROW EXECUTE FUNCTION public.prevent_account_role_change();
