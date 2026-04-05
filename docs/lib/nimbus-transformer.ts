/**
 * Shiki transformer that colorizes Nimbus CLI output in code blocks
 * with title containing "Nimbus". Runs after Shiki's highlighting.
 */
import type { ShikiTransformer } from 'shiki';

const C = {
  green: '#4ade80',
  yellow: '#facc15',
  red: '#f87171',
  cyan: '#22d3ee',
  brightCyan: '#67e8f9',
  blue: '#60a5fa',
  magenta: '#c084fc',
  dim: '#6b7280',
  bold: '#f9fafb',
};

type Segment = { text: string; color?: string };

function colorizeLine(line: string): Segment[] {
  const segments: Segment[] = [];
  let rest = line;

  function push(text: string, color?: string) {
    if (text) segments.push({ text, color });
  }

  // Timestamp at start: [HH:MM:SS]
  const tsMatch = rest.match(/^(\[[\d:]+\])/);
  if (tsMatch) {
    push(tsMatch[1], C.dim);
    rest = rest.slice(tsMatch[1].length);
  }

  // After timestamp, check for status patterns
  const trimmed = rest.trimStart();
  const indent = rest.length - trimmed.length;
  if (indent > 0) push(rest.slice(0, indent));
  rest = trimmed;

  // Status indicators
  const statusPatterns: [RegExp, string][] = [
    [/^(● READY)/, C.green],
    [/^(▲ STARTING)/, C.yellow],
    [/^(▼ STOPPING)/, C.yellow],
    [/^(● STARTING)/, C.yellow],
    [/^(○ STOPPED)/, C.dim],
    [/^(✖ CRASHED)/, C.red],
    [/^(◉ DRAINING)/, C.magenta],
    [/^(↑ SCALE UP)/, C.green],
    [/^(↓ SCALE DOWN)/, C.yellow],
  ];

  let matched = false;
  for (const [pattern, color] of statusPatterns) {
    const m = rest.match(pattern);
    if (m) {
      push(m[1], color);
      rest = rest.slice(m[1].length);
      matched = true;
      break;
    }
  }

  if (!matched) {
    // Single-char symbols at start
    const symbolPatterns: [RegExp, string][] = [
      [/^(✓)/, C.green],
      [/^(✗)/, C.red],
      [/^(✖)/, C.red],
      [/^(ℹ)/, C.cyan],
      [/^(⚠)/, C.yellow],
      [/^(↑)/, C.green],
      [/^(↓)/, C.cyan],
      [/^(⚡)/, C.magenta],
      [/^(↻)/, C.cyan],
      [/^(◆)/, C.brightCyan],
      [/^(◇)/, C.cyan],
      [/^(◈)/, C.cyan],
      [/^(\+)/, C.green],
      [/^(▸)/, C.cyan],
    ];

    for (const [pattern, color] of symbolPatterns) {
      const m = rest.match(pattern);
      if (m) {
        push(m[1], color);
        rest = rest.slice(m[1].length);
        matched = true;
        break;
      }
    }
  }

  if (!matched) {
    // Prompt: nimbus »
    const promptMatch = rest.match(/^(nimbus)( »)/);
    if (promptMatch) {
      push(promptMatch[1], C.brightCyan);
      push(promptMatch[2], C.cyan);
      rest = rest.slice(promptMatch[0].length);
      matched = true;
    }

    // Section header: ── Title ──
    const headerMatch = rest.match(/^(──[─ ].+)$/);
    if (!matched && headerMatch) {
      push(headerMatch[1], C.cyan);
      rest = '';
      matched = true;
    }

    // Separator line: ────────
    const sepMatch = rest.match(/^(─{4,})$/);
    if (!matched && sepMatch) {
      push(sepMatch[1], C.dim);
      rest = '';
      matched = true;
    }
  }

  // Remaining text: colorize inline parenthesized parts as dim
  if (rest) {
    let pos = 0;
    const parenRe = /\([^)]+\)/g;
    let pm;
    while ((pm = parenRe.exec(rest)) !== null) {
      if (pm.index > pos) {
        push(rest.slice(pos, pm.index));
      }
      push(pm[0], C.dim);
      pos = pm.index + pm[0].length;
    }
    if (pos < rest.length) {
      push(rest.slice(pos));
    }
  }

  return segments.length > 0 ? segments : [{ text: line }];
}

export function transformerNimbus(): ShikiTransformer {
  return {
    name: 'nimbus-colorizer',
    pre(node) {
      // Check if this code block has a Nimbus title
      const meta = (this.options.meta as any)?.__raw ?? '';
      if (!meta.includes('title="Nimbus')) return;

      // Walk the tree and replace text nodes in .line spans
      for (const child of node.children) {
        if (child.type !== 'element' || child.tagName !== 'code') continue;

        const newChildren: any[] = [];
        for (const lineEl of child.children) {
          if (lineEl.type === 'text' && lineEl.value === '\n') {
            newChildren.push(lineEl);
            continue;
          }
          if (lineEl.type !== 'element' || !lineEl.properties?.class?.toString().includes('line')) {
            newChildren.push(lineEl);
            continue;
          }

          // Get text content of this line
          const text = getTextContent(lineEl);
          const segments = colorizeLine(text);

          // Replace children with colored spans
          lineEl.children = segments.map((seg) => {
            if (seg.color) {
              return {
                type: 'element',
                tagName: 'span',
                properties: { style: `color:${seg.color}` },
                children: [{ type: 'text', value: seg.text }],
              };
            }
            return { type: 'text', value: seg.text };
          });

          newChildren.push(lineEl);
        }
        child.children = newChildren;
      }
    },
  };
}

function getTextContent(node: any): string {
  if (node.type === 'text') return node.value ?? '';
  if (node.children) return node.children.map(getTextContent).join('');
  return '';
}
