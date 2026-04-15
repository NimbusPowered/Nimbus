"use client";

import * as React from "react";
import { useTheme } from "next-themes";
import CodeMirror, { EditorView, keymap } from "@uiw/react-codemirror";
import { oneDark } from "@codemirror/theme-one-dark";
import { javascript } from "@codemirror/lang-javascript";
import { json } from "@codemirror/lang-json";
import { xml } from "@codemirror/lang-xml";
import { html } from "@codemirror/lang-html";
import { css } from "@codemirror/lang-css";
import { markdown } from "@codemirror/lang-markdown";
import { yaml } from "@codemirror/lang-yaml";
import type { Extension } from "@codemirror/state";

import {
  Sheet,
  SheetBody,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Loader2, Save } from "@/lib/icons";

/**
 * Extensions treated as human-readable text in the file browser. Files
 * matching one of these get an "Edit" action and open the in-sheet editor
 * on click; everything else is treated as binary and offered as download.
 */
const EDITABLE_EXTENSIONS = new Set([
  "toml",
  "yml",
  "yaml",
  "properties",
  "txt",
  "json",
  "conf",
  "md",
  "log",
  "cfg",
  "ini",
  "env",
  "xml",
  "html",
  "css",
  "js",
  "jsx",
  "ts",
  "tsx",
  "sh",
]);

export const IMAGE_EXTENSIONS = new Set([
  "png",
  "jpg",
  "jpeg",
  "gif",
  "webp",
  "svg",
  "bmp",
  "ico",
  "avif",
]);

function extOf(name: string): string {
  const idx = name.lastIndexOf(".");
  return idx === -1 ? "" : name.slice(idx + 1).toLowerCase();
}

export function isEditableExtension(name: string): boolean {
  const ext = extOf(name);
  return ext !== "" && EDITABLE_EXTENSIONS.has(ext);
}

export function isImageExtension(name: string): boolean {
  const ext = extOf(name);
  return ext !== "" && IMAGE_EXTENSIONS.has(ext);
}

// ── Language selection ─────────────────────────────────────────────

/**
 * Pick a CodeMirror language extension for a given filename.
 * Returns an empty array (no language highlighting) for plain-text
 * formats CM6 doesn't ship (toml, properties, conf, ini, env, sh, log);
 * the editor still gets line numbers, search, bracket matching, etc.
 */
function languageExtensionFor(filename: string): Extension[] {
  switch (extOf(filename)) {
    case "js":
    case "jsx":
      return [javascript({ jsx: true })];
    case "ts":
    case "tsx":
      return [javascript({ jsx: true, typescript: true })];
    case "json":
      return [json()];
    case "xml":
      return [xml()];
    case "html":
      return [html()];
    case "css":
      return [css()];
    case "md":
      return [markdown()];
    case "yml":
    case "yaml":
      return [yaml()];
    default:
      return [];
  }
}

// ── Props ──────────────────────────────────────────────────────────

export interface FileEditorProps {
  path: string;
  initialContent: string;
  readOnly: boolean;
  onClose: () => void;
  onSave: (content: string) => Promise<void> | void;
}

// ── Component ──────────────────────────────────────────────────────

export function FileEditor({
  path,
  initialContent,
  readOnly,
  onClose,
  onSave,
}: FileEditorProps) {
  const [content, setContent] = React.useState(initialContent);
  const [saving, setSaving] = React.useState(false);
  const { resolvedTheme } = useTheme();

  const dirty = content !== initialContent;

  const filename = path.includes("/")
    ? path.slice(path.lastIndexOf("/") + 1)
    : path;

  // ── Save ─────────────────────────────────────────────────────────
  const saveRef = React.useRef<() => void>(() => {});
  saveRef.current = () => {
    if (readOnly || !dirty || saving) return;
    setSaving(true);
    Promise.resolve(onSave(content)).finally(() => setSaving(false));
  };

  const handleSave = () => saveRef.current();

  // ── beforeunload warning ─────────────────────────────────────────
  React.useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (dirty) e.preventDefault();
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);

  // ── CodeMirror extensions ────────────────────────────────────────
  const extensions = React.useMemo<Extension[]>(
    () => [
      ...languageExtensionFor(filename),
      EditorView.lineWrapping,
      keymap.of([
        {
          key: "Mod-s",
          run: () => {
            saveRef.current();
            return true;
          },
          preventDefault: true,
        },
      ]),
    ],
    [filename],
  );

  const isDark = resolvedTheme === "dark";

  return (
    <Sheet
      open
      onOpenChange={(open) => {
        if (!open) onClose();
      }}
    >
      <SheetContent side="right" size="xl">
        <SheetHeader>
          <SheetTitle className="font-mono text-sm flex items-center gap-2">
            <span className="truncate">{path}</span>
            {dirty && !readOnly && (
              <span
                className="inline-block size-2 shrink-0 rounded-full bg-severity-warn"
                title="Unsaved changes"
              />
            )}
          </SheetTitle>
          <SheetDescription>
            {readOnly
              ? "This scope is read-only."
              : "Edit the file content, then click Save or press Ctrl+S."}
          </SheetDescription>
        </SheetHeader>

        <SheetBody className="pb-6">
          <div className="h-full min-h-[60vh] rounded-md border bg-muted/30 overflow-hidden focus-within:ring-1 focus-within:ring-ring">
            <CodeMirror
              value={content}
              onChange={(v: string) => setContent(v)}
              readOnly={readOnly}
              editable={!readOnly}
              theme={isDark ? oneDark : "light"}
              extensions={extensions}
              basicSetup={{
                lineNumbers: true,
                foldGutter: true,
                highlightActiveLine: true,
                highlightSelectionMatches: true,
                bracketMatching: true,
                closeBrackets: true,
                autocompletion: true,
                indentOnInput: true,
                tabSize: 2,
              }}
              height="100%"
              className="h-full text-xs"
            />
          </div>
        </SheetBody>

        <SheetFooter>
          <Button variant="outline" onClick={onClose} disabled={saving}>
            {dirty && !readOnly ? "Discard" : "Close"}
          </Button>
          {!readOnly && (
            <Button onClick={handleSave} disabled={saving || !dirty}>
              {saving ? (
                <Loader2 className="size-4 animate-spin" />
              ) : (
                <Save className="size-4" />
              )}
              Save
            </Button>
          )}
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
