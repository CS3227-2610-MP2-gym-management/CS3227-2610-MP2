import { createClient } from "npm:@supabase/supabase-js@2";

type Payload = {
  action: "create" | "reset-password" | "update" | "create-owner" | "set-owner-active";
  member_account_id?: number;
  owner_account_id?: number;
  active?: boolean;
  email?: string;
  password?: string;
  current_password?: string;
  full_name?: string;
  phone_number?: string;
  date_of_birth?: string | null;
  membership_start?: string;
  membership_expiry?: string;
  payment_amount_cents?: number;
  payment_method?: string;
  paid_at?: string;
  payment_reference?: string | null;
};

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), {
  status,
  headers: { "Content-Type": "application/json" },
});

Deno.serve(async (request) => {
  if (request.method !== "POST") return json(405, { message: "Method not allowed" });

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const authorization = request.headers.get("Authorization");
  if (!url || !serviceKey || !authorization?.startsWith("Bearer ")) {
    return json(401, { message: "Authentication is required" });
  }

  const admin = createClient(url, serviceKey, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const token = authorization.slice("Bearer ".length);
  const { data: userData, error: userError } = await admin.auth.getUser(token);
  if (userError || !userData.user) return json(401, { message: "Invalid session" });

  const { data: actor, error: actorError } = await admin.from("accounts")
    .select("id,role,is_active")
    .eq("auth_user_id", userData.user.id)
    .single();
  if (actorError || !actor?.is_active) return json(403, { message: "Active account required" });

  let payload: Payload;
  try {
    payload = await request.json();
  } catch {
    return json(400, { message: "Invalid request" });
  }

  const recentlyReauthenticated = async () => {
    if (!payload.current_password || !userData.user.email) return false;
    const verifier = createClient(url, Deno.env.get("SUPABASE_ANON_KEY") ?? serviceKey, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
    const { data, error } = await verifier.auth.signInWithPassword({
      email: userData.user.email,
      password: payload.current_password,
    });
    return !error && data.user?.id === userData.user.id;
  };

  if (payload.action === "create-owner") {
    if (actor.role !== "OWNER") return json(403, { message: "Owner access required" });
    if (!payload.email || !payload.password) {
      return json(400, { message: "Owner email and password are required" });
    }
    if (!(await recentlyReauthenticated())) {
      return json(403, { message: "Current password is incorrect" });
    }
    const { data: created, error: createError } = await admin.auth.admin.createUser({
      email: payload.email,
      password: payload.password,
      email_confirm: true,
    });
    if (createError || !created.user) {
      return json(400, { message: createError?.message ?? "Unable to create Owner login" });
    }
    const { data: ownerId, error: recordsError } = await admin.rpc("create_owner_record", {
      p_auth_user_id: created.user.id,
      p_actor_account_id: actor.id,
    });
    if (recordsError) {
      await admin.auth.admin.deleteUser(created.user.id);
      return json(400, { message: recordsError.message });
    }
    return json(200, { owner_account_id: ownerId });
  }

  if (payload.action === "set-owner-active") {
    if (actor.role !== "OWNER") return json(403, { message: "Owner access required" });
    if (!payload.owner_account_id || typeof payload.active !== "boolean") {
      return json(400, { message: "Owner account and active state are required" });
    }
    if (!(await recentlyReauthenticated())) {
      return json(403, { message: "Current password is incorrect" });
    }
    const { data: changed, error } = await admin.rpc("set_owner_active", {
      p_target_account_id: payload.owner_account_id,
      p_active: payload.active,
      p_actor_account_id: actor.id,
    });
    return error
      ? json(400, { message: error.message })
      : json(200, { updated: changed });
  }

  if (payload.action === "create") {
    if (actor.role !== "OWNER") return json(403, { message: "Owner access required" });
    const { data: created, error: createError } = await admin.auth.admin.createUser({
      email: payload.email,
      password: payload.password,
      email_confirm: true,
    });
    if (createError || !created.user) {
      return json(400, { message: createError?.message ?? "Unable to create login" });
    }
    const { data: memberId, error: recordsError } = await admin.rpc("create_member_records", {
      p_member_auth_user_id: created.user.id,
      p_email: payload.email,
      p_full_name: payload.full_name,
      p_phone_number: payload.phone_number,
      p_date_of_birth: payload.date_of_birth,
      p_membership_start: payload.membership_start,
      p_membership_expiry: payload.membership_expiry,
      p_payment_amount_cents: payload.payment_amount_cents,
      p_payment_method: payload.payment_method,
      p_paid_at: payload.paid_at,
      p_payment_reference: payload.payment_reference,
      p_owner_account_id: actor.id,
    });
    if (recordsError) {
      await admin.auth.admin.deleteUser(created.user.id);
      return json(400, { message: recordsError.message });
    }
    return json(200, { member_account_id: memberId });
  }

  const memberId = payload.member_account_id;
  if (!memberId) return json(400, { message: "Member account is required" });
  const ownUpdate = actor.role === "MEMBER" && actor.id === memberId;
  if (actor.role !== "OWNER" && !ownUpdate) {
    return json(403, { message: "Member access denied" });
  }
  const { data: member, error: memberError } = await admin.from("accounts")
    .select("auth_user_id,email,role")
    .eq("id", memberId)
    .eq("role", "MEMBER")
    .single();
  if (memberError || !member) return json(404, { message: "Member not found" });

  if (payload.action === "reset-password") {
    if (actor.role !== "OWNER") return json(403, { message: "Owner access required" });
    const { error } = await admin.auth.admin.updateUserById(member.auth_user_id, {
      password: payload.password,
    });
    return error ? json(400, { message: error.message }) : json(200, { updated: true });
  }

  if (payload.action !== "update" || !payload.email || !payload.phone_number) {
    return json(400, { message: "Invalid Member update" });
  }
  const oldEmail = member.email;
  const { error: authUpdateError } = await admin.auth.admin.updateUserById(member.auth_user_id, {
    email: payload.email,
    email_confirm: true,
  });
  if (authUpdateError) return json(400, { message: authUpdateError.message });

  const { error: recordsUpdateError } = await admin.rpc("update_member_records", {
    p_member_account_id: memberId,
    p_email: payload.email,
    p_phone_number: payload.phone_number,
    p_update_identity: actor.role === "OWNER",
    p_full_name: payload.full_name ?? "",
    p_date_of_birth: payload.date_of_birth,
  });
  if (recordsUpdateError) {
    await admin.auth.admin.updateUserById(member.auth_user_id, {
      email: oldEmail,
      email_confirm: true,
    });
    return json(400, { message: recordsUpdateError.message });
  }
  return json(200, { updated: true });
});
