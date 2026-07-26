import { FormEvent, useEffect, useState, useTransition } from "react";
import {
  Account,
  AccountType,
  Balance,
  JournalEntry,
  api,
  dollarsToCents,
  newIdempotencyKey,
} from "./api/client";

type AccountWithBalance = Account & { balance?: Balance };

export default function App() {
  const [accounts, setAccounts] = useState<AccountWithBalance[]>([]);
  const [entries, setEntries] = useState<JournalEntry[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [message, setMessage] = useState<{ tone: "ok" | "error"; text: string } | null>(null);
  const [pending, startTransition] = useTransition();
  const [lastTransferKey, setLastTransferKey] = useState<string>("");

  const [accountForm, setAccountForm] = useState({
    code: "",
    name: "",
    accountType: "ASSET" as AccountType,
    currency: "USD",
  });

  const [transferForm, setTransferForm] = useState({
    fromAccountId: "",
    toAccountId: "",
    amount: "",
    description: "",
    reuseKey: false,
  });

  async function refresh() {
    const [accountList, journal] = await Promise.all([
      api.listAccounts(),
      api.listJournalEntries(),
    ]);
    const withBalances = await Promise.all(
      accountList.map(async (account) => {
        const balance = await api.getBalance(account.id);
        return { ...account, balance };
      })
    );
    setAccounts(withBalances);
    setEntries(journal);
    if (!selectedId && withBalances.length > 0) {
      setSelectedId(withBalances[0].id);
    }
    if (!transferForm.fromAccountId && withBalances.length > 0) {
      const assets = withBalances.filter((a) => a.accountType === "ASSET" && a.status === "ACTIVE");
      setTransferForm((prev) => ({
        ...prev,
        fromAccountId: assets[0]?.id ?? "",
        toAccountId: assets[1]?.id ?? assets[0]?.id ?? "",
      }));
    }
  }

  useEffect(() => {
    startTransition(() => {
      void refresh().catch((err: Error) =>
        setMessage({ tone: "error", text: err.message || "Failed to load ledger" })
      );
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onCreateAccount(event: FormEvent) {
    event.preventDefault();
    setMessage(null);
    try {
      await api.createAccount({
        code: accountForm.code.trim().toUpperCase(),
        name: accountForm.name.trim(),
        accountType: accountForm.accountType,
        currency: accountForm.currency.trim().toUpperCase(),
      });
      setAccountForm({ code: "", name: "", accountType: "ASSET", currency: "USD" });
      await refresh();
      setMessage({ tone: "ok", text: "Account created." });
    } catch (err) {
      setMessage({ tone: "error", text: err instanceof Error ? err.message : "Create failed" });
    }
  }

  async function onTransfer(event: FormEvent) {
    event.preventDefault();
    setMessage(null);
    try {
      const amountCents = dollarsToCents(transferForm.amount);
      const key = transferForm.reuseKey && lastTransferKey ? lastTransferKey : newIdempotencyKey();
      const from = accounts.find((a) => a.id === transferForm.fromAccountId);
      const entry = await api.transfer(key, {
        fromAccountId: transferForm.fromAccountId,
        toAccountId: transferForm.toAccountId,
        amountCents,
        currency: from?.currency ?? "USD",
        description: transferForm.description.trim() || "Transfer",
      });
      setLastTransferKey(key);
      setTransferForm((prev) => ({ ...prev, amount: "", description: "" }));
      await refresh();
      setMessage({
        tone: "ok",
        text: `Posted ${entry.id.slice(0, 8)}… with key ${key.slice(0, 8)}…`,
      });
    } catch (err) {
      setMessage({ tone: "error", text: err instanceof Error ? err.message : "Transfer failed" });
    }
  }

  async function onVoid(entryId: string) {
    setMessage(null);
    try {
      await api.voidEntry(entryId, newIdempotencyKey());
      await refresh();
      setMessage({ tone: "ok", text: "Entry voided with reversing journal lines." });
    } catch (err) {
      setMessage({ tone: "error", text: err instanceof Error ? err.message : "Void failed" });
    }
  }

  const visibleEntries = selectedId
    ? entries.filter((entry) => entry.lines.some((line) => line.accountId === selectedId))
    : entries;

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand-mark">Balanced</div>
        <nav>
          <a href="#demo">Demo</a>
          <a href="#ledger">Ledger</a>
          <a href="https://github.com/kishakay/balanced" target="_blank" rel="noreferrer">
            GitHub
          </a>
        </nav>
      </header>

      <section className="hero">
        <p className="hero-brand">Balanced</p>
        <h2>A ledger that stays honest under retries.</h2>
        <p>
          Double-entry API for idempotent transfers, live balances, and audit-ready debit/credit
          history.
        </p>
        <div className="cta-row">
          <a className="btn" href="#demo">
            Open the demo
          </a>
          <a className="btn secondary" href="#ledger">
            Inspect entries
          </a>
        </div>
      </section>

      <section className="section" id="demo">
        <div className="section-head">
          <h3>Working demo</h3>
          <p>
            Seeded accounts ship with the API. Create another account, move funds, retry with the
            same idempotency key, then void if you need a clean reverse.
          </p>
        </div>

        <div className="panel">
          <div className="account-grid">
            {accounts.map((account, index) => (
              <div
                className="account-row"
                key={account.id}
                style={{ animationDelay: `${index * 40}ms` }}
              >
                <button
                  type="button"
                  className={`linkish ${selectedId === account.id ? "selected" : ""}`}
                  onClick={() => setSelectedId(account.id)}
                >
                  <div className="code">{account.code}</div>
                  <div className="meta">
                    {account.name} · {account.accountType} · {account.status}
                  </div>
                </button>
                <div className="meta">{account.currency}</div>
                <div className="balance">
                  {account.balance ? `${account.balance.balance}` : "—"}
                </div>
              </div>
            ))}
            {accounts.length === 0 && (
              <div className="account-row">
                <div className="meta">{pending ? "Loading accounts…" : "No accounts yet."}</div>
              </div>
            )}
          </div>
        </div>

        <div className="forms" style={{ marginTop: "2rem" }}>
          <form className="form" onSubmit={onCreateAccount}>
            <h4 style={{ margin: 0, fontFamily: "var(--font-display)" }}>Create account</h4>
            <label>
              Code
              <input
                value={accountForm.code}
                onChange={(e) => setAccountForm({ ...accountForm, code: e.target.value })}
                placeholder="ASSET-PETTY"
                required
              />
            </label>
            <label>
              Name
              <input
                value={accountForm.name}
                onChange={(e) => setAccountForm({ ...accountForm, name: e.target.value })}
                placeholder="Petty cash"
                required
              />
            </label>
            <label>
              Type
              <select
                value={accountForm.accountType}
                onChange={(e) =>
                  setAccountForm({ ...accountForm, accountType: e.target.value as AccountType })
                }
              >
                <option value="ASSET">ASSET</option>
                <option value="LIABILITY">LIABILITY</option>
                <option value="EQUITY">EQUITY</option>
                <option value="REVENUE">REVENUE</option>
                <option value="EXPENSE">EXPENSE</option>
              </select>
            </label>
            <label>
              Currency
              <input
                value={accountForm.currency}
                onChange={(e) => setAccountForm({ ...accountForm, currency: e.target.value })}
                maxLength={3}
                required
              />
            </label>
            <div className="actions">
              <button className="btn" type="submit">
                Create
              </button>
            </div>
          </form>

          <form className="form" onSubmit={onTransfer}>
            <h4 style={{ margin: 0, fontFamily: "var(--font-display)" }}>Transfer</h4>
            <label>
              From
              <select
                value={transferForm.fromAccountId}
                onChange={(e) =>
                  setTransferForm({ ...transferForm, fromAccountId: e.target.value })
                }
                required
              >
                {accounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.code}
                  </option>
                ))}
              </select>
            </label>
            <label>
              To
              <select
                value={transferForm.toAccountId}
                onChange={(e) => setTransferForm({ ...transferForm, toAccountId: e.target.value })}
                required
              >
                {accounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.code}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Amount
              <input
                value={transferForm.amount}
                onChange={(e) => setTransferForm({ ...transferForm, amount: e.target.value })}
                placeholder="25.00"
                required
              />
            </label>
            <label>
              Description
              <input
                value={transferForm.description}
                onChange={(e) =>
                  setTransferForm({ ...transferForm, description: e.target.value })
                }
                placeholder="Operating → reserves"
              />
            </label>
            <label style={{ gridTemplateColumns: "auto 1fr", alignItems: "center" }}>
              <input
                type="checkbox"
                checked={transferForm.reuseKey}
                onChange={(e) =>
                  setTransferForm({ ...transferForm, reuseKey: e.target.checked })
                }
              />
              Reuse last idempotency key (safe retry)
            </label>
            <div className="actions">
              <button className="btn" type="submit">
                Post transfer
              </button>
            </div>
          </form>
        </div>

        <p className={`status ${message?.tone ?? ""}`}>{message?.text ?? ""}</p>
      </section>

      <section className="section" id="ledger">
        <div className="section-head">
          <h3>Audit trail</h3>
          <p>
            Every movement is a balanced journal entry. Voiding writes a reversing pair instead of
            deleting history.
            {selectedId
              ? ` Showing entries touching ${accounts.find((a) => a.id === selectedId)?.code ?? "account"}.`
              : ""}
          </p>
        </div>

        <div className="ledger-list panel">
          {visibleEntries.map((entry, index) => (
            <article className="entry" key={entry.id} style={{ animationDelay: `${index * 35}ms` }}>
              <div className="entry-top">
                <h4>
                  {entry.description}
                  <span className={`badge ${entry.status === "VOIDED" ? "voided" : ""}`}>
                    {entry.status}
                  </span>
                </h4>
                <div className="stamp">
                  {new Date(entry.createdAt).toLocaleString()}
                  {entry.status === "POSTED" && !entry.voidsEntryId && (
                    <>
                      {" · "}
                      <button className="btn danger" type="button" onClick={() => void onVoid(entry.id)}>
                        Void
                      </button>
                    </>
                  )}
                </div>
              </div>
              <div className="lines">
                {entry.lines.map((line) => (
                  <div className="line" key={line.id}>
                    <span>{line.direction}</span>
                    <span>
                      {line.accountCode} — {line.accountName}
                    </span>
                    <strong>
                      {line.amount} {line.currency}
                    </strong>
                  </div>
                ))}
              </div>
            </article>
          ))}
          {visibleEntries.length === 0 && (
            <div className="entry">
              <div className="meta">No journal entries yet.</div>
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
