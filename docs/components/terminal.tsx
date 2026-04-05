'use client';

export function Terminal({ title, children }: { title?: string; children: string }) {
  return (
    <div className="terminal">
      <div className="terminal-header">
        <span className="terminal-title">{title ?? 'terminal'}</span>
      </div>
      <pre
        className="terminal-body"
        dangerouslySetInnerHTML={{ __html: children }}
      />
    </div>
  );
}
