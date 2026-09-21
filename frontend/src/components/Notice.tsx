import type { ReactNode } from "react";

type Tone = "neutral" | "error" | "attention" | "success";

interface NoticeProps {
  tone?: Tone;
  // Uma frase curta (até ~10 palavras).
  title: ReactNode;
  // Pormenor opcional, em texto secundário mais discreto.
  detail?: ReactNode;
  // Ação à direita (ex.: botão "Mostrar todos").
  action?: ReactNode;
  className?: string;
}

// Mensagem de estado partilhada. Erros usam role="alert" (anunciados de
// imediato); avisos, sucessos e estados neutros usam role="status".
export default function Notice({ tone = "neutral", title, detail, action, className }: NoticeProps) {
  const classes = ["status", "notice"];
  if (tone !== "neutral") classes.push(`status--${tone}`);
  if (className) classes.push(className);

  return (
    <div className={classes.join(" ")} role={tone === "error" ? "alert" : "status"}>
      <div className="notice__text">
        <p className="notice__title">{title}</p>
        {detail && <p className="status__detail">{detail}</p>}
      </div>
      {action && <div className="notice__action">{action}</div>}
    </div>
  );
}
