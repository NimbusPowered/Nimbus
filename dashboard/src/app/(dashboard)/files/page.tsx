"use client";

import { PageShell } from "@/components/page-shell";
import { FileBrowser } from "@/components/file-browser";

export default function FilesPage() {
  return (
    <PageShell
      title="Files"
      description="Browse and edit templates, service directories, and group configs."
    >
      <FileBrowser />
    </PageShell>
  );
}
