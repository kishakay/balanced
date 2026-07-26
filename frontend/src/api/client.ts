export type AccountType = "ASSET" | "LIABILITY" | "EQUITY" | "REVENUE" | "EXPENSE";
export type AccountStatus = "ACTIVE" | "CLOSED";
export type EntryStatus = "POSTED" | "VOIDED";
export type Direction = "DEBIT" | "CREDIT";

export interface Account {
  id: string;
  code: string;
  name: string;
  accountType: AccountType;
  currency: string;
  status: AccountStatus;
  createdAt: string;
}

export interface Balance {
  accountId: string;
  accountCode: string;
  currency: string;
  balanceCents: number;
  balance: string;
  debitTotalCents: number;
  creditTotalCents: number;
}

export interface JournalLine {
  id: string;
  accountId: string;
  accountCode: string;
  accountName: string;
  direction: Direction;
  amountCents: number;
  amount: string;
  currency: string;
}

export interface JournalEntry {
  id: string;
  idempotencyKey: string;
  description: string;
  status: EntryStatus;
  createdAt: string;
  voidedAt: string | null;
  voidsEntryId: string | null;
  lines: JournalLine[];
}

export interface ApiErrorBody {
  code: string;
  message: string;
  errors?: { field: string; message: string }[];
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    let body: ApiErrorBody | undefined;
    try {
      body = (await response.json()) as ApiErrorBody;
    } catch {
      body = undefined;
    }
    throw new Error(body?.message ?? `Request failed (${response.status})`);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const api = {
  listAccounts: () => request<Account[]>("/api/v1/accounts"),
  createAccount: (payload: {
    code: string;
    name: string;
    accountType: AccountType;
    currency: string;
  }) =>
    request<Account>("/api/v1/accounts", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  getBalance: (id: string) => request<Balance>(`/api/v1/accounts/${id}/balance`),
  listTransactions: (id: string) =>
    request<JournalEntry[]>(`/api/v1/accounts/${id}/transactions`),
  listJournalEntries: () => request<JournalEntry[]>("/api/v1/journal-entries"),
  transfer: (
    idempotencyKey: string,
    payload: {
      fromAccountId: string;
      toAccountId: string;
      amountCents: number;
      currency: string;
      description: string;
    }
  ) =>
    request<JournalEntry>("/api/v1/transfers", {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(payload),
    }),
  voidEntry: (id: string, idempotencyKey: string) =>
    request<JournalEntry>(`/api/v1/journal-entries/${id}/void`, {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
    }),
};

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

export function dollarsToCents(value: string): number {
  const normalized = value.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) {
    throw new Error("Amount must look like 12.34");
  }
  const [whole, fraction = ""] = normalized.split(".");
  return Number(whole) * 100 + Number((fraction + "00").slice(0, 2));
}
