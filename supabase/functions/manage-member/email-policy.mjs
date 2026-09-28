export const DUPLICATE_EMAIL_CODE = "duplicate_email";
export const DUPLICATE_EMAIL_MESSAGE = "An account with this email already exists";

export const normalizeAccountEmail = (email) => email.trim().toLowerCase();

export const shouldUpdateLoginEmail = (currentEmail, requestedEmail) =>
  normalizeAccountEmail(currentEmail) !== normalizeAccountEmail(requestedEmail);

export const isDuplicateEmailError = (error) => {
  const code = String(error?.code ?? "").toLowerCase();
  const message = String(error?.message ?? "").toLowerCase();
  return code === "email_exists" ||
    (message.includes("already") && message.includes("registered")) ||
    (code === "23505" && message.includes("email"));
};
