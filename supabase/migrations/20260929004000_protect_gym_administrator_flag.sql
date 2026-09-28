DROP TRIGGER accounts_protect_final_active_owner ON public.accounts;

CREATE TRIGGER accounts_protect_final_active_owner
BEFORE UPDATE OF role, is_active, is_gym_administrator OR DELETE ON public.accounts
FOR EACH ROW EXECUTE FUNCTION public.protect_final_active_owner();
