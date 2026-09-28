REVOKE ALL ON SEQUENCE public.member_number_sequence FROM PUBLIC, anon, authenticated;
GRANT USAGE, SELECT ON SEQUENCE public.member_number_sequence TO service_role;
