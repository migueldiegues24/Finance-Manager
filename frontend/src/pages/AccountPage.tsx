import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import {
  changePassword,
  exportAccountData,
  listSessions,
  revokeOtherSessions,
  revokeSession,
} from "../api/account";
import { toNotice, type NoticeContent } from "../api/errors";
import AppLayout from "../components/AppLayout";
import ConfirmDialog from "../components/ConfirmDialog";
import DeleteAccountDialog from "../components/DeleteAccountDialog";
import Notice from "../components/Notice";
import PasswordField from "../components/PasswordField";
import { useAuth } from "../context/AuthContext";
import { saveBlob } from "../utils/download";
import { countLabel } from "../utils/plural";
import { formatDateTime, sessionModeLabel, sortSessions, type AccountSession } from "../utils/sessions";
import "./AccountPage.css";

interface Status {
  tone: "error" | "success";
  content: NoticeContent;
}

type PendingConfirm = { kind: "current"; session: AccountSession } | { kind: "others" };

function success(title: string, detail?: string): Status {
  return { tone: "success", content: { title, detail } };
}

function failure(err: unknown): Status {
  return { tone: "error", content: toNotice(err) };
}

function StatusNotice({ status }: { status: Status | null }) {
  if (!status) return null;
  return <Notice tone={status.tone} title={status.content.title} detail={status.content.detail} />;
}

export default function AccountPage() {
  const { email, replaceTokens, endSession } = useAuth();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [savingPassword, setSavingPassword] = useState(false);
  const [passwordStatus, setPasswordStatus] = useState<Status | null>(null);

  const [sessions, setSessions] = useState<AccountSession[] | null>(null);
  const [sessionsStatus, setSessionsStatus] = useState<Status | null>(null);
  const [revokingId, setRevokingId] = useState<number | null>(null);
  const [pendingConfirm, setPendingConfirm] = useState<PendingConfirm | null>(null);

  const [exporting, setExporting] = useState(false);
  const [dangerStatus, setDangerStatus] = useState<Status | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);

  const othersButtonRef = useRef<HTMLButtonElement>(null);
  const deleteButtonRef = useRef<HTMLButtonElement>(null);

  const reloadSessions = useCallback(async () => {
    setSessions(sortSessions(await listSessions()));
  }, []);

  useEffect(() => {
    let cancelled = false;
    listSessions()
      .then((data) => {
        if (!cancelled) setSessions(sortSessions(data));
      })
      .catch((err) => {
        if (!cancelled) setSessionsStatus(failure(err));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const others = sessions?.filter((s) => !s.current) ?? [];

  async function handlePasswordSubmit(event: FormEvent) {
    event.preventDefault();
    if (!currentPassword || !newPassword || savingPassword) return;

    setSavingPassword(true);
    setPasswordStatus(null);
    try {
      replaceTokens(await changePassword(currentPassword, newPassword));
      setCurrentPassword("");
      setNewPassword("");
      setPasswordStatus(success("Password mudada.", "As sessões noutros dispositivos foram terminadas."));
      await reloadSessions().catch(() => {});
    } catch (err) {
      setPasswordStatus(failure(err));
    } finally {
      setSavingPassword(false);
    }
  }

  async function handleRevoke(session: AccountSession) {
    if (session.current) {
      setPendingConfirm({ kind: "current", session });
      return;
    }
    setRevokingId(session.id);
    setSessionsStatus(null);
    try {
      await revokeSession(session.id);
      await reloadSessions();
      setSessionsStatus(success("Sessão terminada."));
      othersButtonRef.current?.focus();
    } catch (err) {
      setSessionsStatus(failure(err));
    } finally {
      setRevokingId(null);
    }
  }

  async function confirmPending() {
    if (!pendingConfirm) return;
    if (pendingConfirm.kind === "current") {
      await revokeSession(pendingConfirm.session.id);
      endSession();
      return;
    }
    const revoked = await revokeOtherSessions();
    await reloadSessions().catch(() => {});
    setSessionsStatus(
      success(revoked === 0 ? "Não havia outras sessões." : `${countLabel(revoked, "sessão terminada", "sessões terminadas")}.`),
    );
  }

  async function handleExport() {
    setExporting(true);
    setDangerStatus(null);
    try {
      const { blob, filename } = await exportAccountData();
      saveBlob(blob, filename);
      setDangerStatus(success("Exportação pronta.", `O ficheiro ${filename} foi descarregado.`));
    } catch (err) {
      setDangerStatus(failure(err));
    } finally {
      setExporting(false);
    }
  }

  return (
    <AppLayout>
      <header className="page-header">
        <p className="page-header__eyebrow">Conta</p>
        <h1 className="page-header__title">A tua conta</h1>
        {email && <p className="page-header__subtitle">{email}</p>}
      </header>

      <section className="section" aria-labelledby="account-password-title">
        <h2 className="section__title" id="account-password-title">
          Password
        </h2>
        <p className="section__subtitle">
          Ao mudar a password, as sessões noutros dispositivos terminam e têm de voltar a entrar.
        </p>
        <StatusNotice status={passwordStatus} />
        <form className="account-page__form" onSubmit={handlePasswordSubmit} noValidate>
          <PasswordField
            label="Password atual"
            value={currentPassword}
            onChange={setCurrentPassword}
            autoComplete="current-password"
          />
          <PasswordField
            label="Nova password"
            value={newPassword}
            onChange={setNewPassword}
            autoComplete="new-password"
            minLength={12}
            maxLength={64}
            hint="Entre 12 e 64 caracteres. Evita passwords comuns ou com o teu email."
          />
          <div className="account-page__form-actions">
            <button
              type="submit"
              className="btn btn--primary"
              disabled={savingPassword || !currentPassword || !newPassword}
            >
              {savingPassword ? "A gravar…" : "Mudar password"}
            </button>
          </div>
        </form>
      </section>

      <section className="section" aria-labelledby="account-sessions-title">
        <h2 className="section__title" id="account-sessions-title">
          Sessões ativas
        </h2>
        <p className="section__subtitle">
          Onde a tua conta tem sessão iniciada. Uma sessão terminada deixa de renovar e perde o acesso em até 15
          minutos.
        </p>
        <StatusNotice status={sessionsStatus} />
        {sessions === null && !sessionsStatus && <Notice title="A carregar…" />}

        {sessions && (
          <>
            <table className="ledger account-page__sessions">
              <thead>
                <tr>
                  <th>Sessão</th>
                  <th>Última atividade</th>
                  <th>Expira</th>
                  <th>
                    <span className="visually-hidden">Ações</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {sessions.map((session) => (
                  <tr key={session.id}>
                    <td>
                      <span className="account-page__session-name">
                        {sessionModeLabel(session.mode)}
                        {session.current && <span className="tag">Esta sessão</span>}
                      </span>
                    </td>
                    <td data-label="Última atividade">{formatDateTime(session.createdAt)}</td>
                    <td data-label="Expira">{formatDateTime(session.expiresAt)}</td>
                    <td className="account-page__actions">
                      <button
                        type="button"
                        className="btn btn--link btn--danger"
                        onClick={() => handleRevoke(session)}
                        disabled={revokingId !== null}
                        aria-label={
                          session.current
                            ? "Terminar esta sessão"
                            : `Terminar a sessão com última atividade a ${formatDateTime(session.createdAt)}`
                        }
                      >
                        {revokingId === session.id ? "A terminar…" : "Terminar"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="account-page__sessions-actions">
              <button
                ref={othersButtonRef}
                type="button"
                className="btn btn--ghost"
                onClick={() => setPendingConfirm({ kind: "others" })}
                disabled={others.length === 0}
              >
                Terminar todas as outras
              </button>
            </div>
          </>
        )}
      </section>

      <section className="section" aria-labelledby="account-danger-title">
        <h2 className="section__title" id="account-danger-title">
          Zona de perigo
        </h2>
        <StatusNotice status={dangerStatus} />
        <div className="account-page__danger">
          <div className="account-page__danger-item">
            <div className="account-page__danger-text">
              <p className="account-page__danger-title">Exportar os dados</p>
              <p className="status__detail">Um ficheiro JSON com a conta, categorias, regras e transações.</p>
            </div>
            <button type="button" className="btn btn--ghost" onClick={handleExport} disabled={exporting}>
              {exporting ? "A exportar…" : "Exportar JSON"}
            </button>
          </div>
          <div className="account-page__danger-item">
            <div className="account-page__danger-text">
              <p className="account-page__danger-title">Apagar a conta</p>
              <p className="status__detail">
                Apaga a conta e todos os dados, sem volta. Exporta primeiro se quiseres guardar uma cópia.
              </p>
            </div>
            <button
              ref={deleteButtonRef}
              type="button"
              className="btn btn--destructive"
              onClick={() => setDeleteOpen(true)}
            >
              Apagar conta…
            </button>
          </div>
        </div>
      </section>

      {pendingConfirm && (
        <ConfirmDialog
          open
          title={pendingConfirm.kind === "current" ? "Terminar esta sessão?" : "Terminar todas as outras sessões?"}
          description={
            pendingConfirm.kind === "current"
              ? "Sais da conta neste dispositivo e tens de voltar a entrar."
              : `${countLabel(others.length, "outra sessão termina", "outras sessões terminam")}. Esta sessão continua.`
          }
          confirmLabel={pendingConfirm.kind === "current" ? "Terminar e sair" : "Terminar as outras"}
          destructive
          onConfirm={confirmPending}
          onClose={() => setPendingConfirm(null)}
          returnFocusRef={othersButtonRef}
        />
      )}

      {deleteOpen && (
        <DeleteAccountDialog
          open
          onClose={() => setDeleteOpen(false)}
          onDeleted={endSession}
          returnFocusRef={deleteButtonRef}
        />
      )}
    </AppLayout>
  );
}
