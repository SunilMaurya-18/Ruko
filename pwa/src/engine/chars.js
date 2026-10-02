// java.lang.Character semantics the server pipeline relies on, so both engines split and count text alike.

const JAVA_WHITESPACE = /^(?:[\t\n\u000B\f\r\u001C-\u001F]|(?![\u00A0\u2007\u202F])[\p{Zs}\p{Zl}\p{Zp}])$/u;
const LETTER = /^\p{L}$/u;
const MARK = /^\p{M}$/u;
const LETTER_OR_MARK = /^[\p{L}\p{Mn}\p{Mc}]$/u;
const CONTROL = /^\p{Cc}$/u;
const UPPER_OR_TITLE = /^[\p{Uppercase}\p{Lt}]$/u;

export const isWhitespace = (ch) => JAVA_WHITESPACE.test(ch);
export const isLetter = (ch) => LETTER.test(ch);
export const isMark = (ch) => MARK.test(ch);
export const isLetterOrMark = (ch) => LETTER_OR_MARK.test(ch);
export const isControl = (ch) => CONTROL.test(ch);
export const isUpperOrTitle = (ch) => UPPER_OR_TITLE.test(ch);

/** String.strip(): trims Java whitespace code points at both ends. */
export function strip(text) {
  const points = [...text];
  let start = 0;
  let end = points.length;
  while (start < end && isWhitespace(points[start])) start++;
  while (end > start && isWhitespace(points[end - 1])) end--;
  return points.slice(start, end).join('');
}

export const isBlank = (text) => strip(text).length === 0;

export const codePointCount = (text) => [...text].length;
