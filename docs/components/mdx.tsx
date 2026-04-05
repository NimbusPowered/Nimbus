import defaultComponents from 'fumadocs-ui/mdx';
import type { MDXComponents } from 'mdx/types';
import { Terminal } from './terminal';

export function getMDXComponents(components?: MDXComponents): MDXComponents {
  return {
    ...defaultComponents,
    Terminal,
    ...components,
  };
}
