/**
 * Port of the server's MappedText: text derived from an original string by a series of edits, keeping for every
 * UTF-16 unit the range of the original it came from, so any span of the derived text can be quoted verbatim.
 */
export class MappedText {
  constructor(original, value, starts, ends) {
    this.original = original;
    this.value = value;
    this.starts = starts;
    this.ends = ends;
  }

  static of(original) {
    const starts = new Array(original.length);
    const ends = new Array(original.length);
    for (let i = 0; i < original.length; i++) {
      starts[i] = i;
      ends[i] = i + 1;
    }
    return new MappedText(original, original, starts, ends);
  }

  get length() {
    return this.value.length;
  }

  originalSlice(start, end) {
    if (start >= end) return '';
    return this.original.substring(this.starts[start], this.ends[end - 1]);
  }

  /** Edits are {start, end, replacement}, sorted and non-overlapping, in coordinates of `value`. */
  apply(edits) {
    if (edits.length === 0) return this;
    const { value, starts, ends, original } = this;
    let out = '';
    const newStarts = [];
    const newEnds = [];
    let cursor = 0;
    const copy = (from, to) => {
      out += value.slice(from, to);
      for (let i = from; i < to; i++) {
        newStarts.push(starts[i]);
        newEnds.push(ends[i]);
      }
    };
    for (const edit of edits) {
      if (edit.start < cursor || edit.end < edit.start || edit.end > value.length) {
        throw new RangeError('edits must be sorted, non-overlapping, and in range');
      }
      copy(cursor, edit.start);
      let originalStart;
      let originalEnd;
      if (edit.start < edit.end) {
        originalStart = starts[edit.start];
        originalEnd = ends[edit.end - 1];
      } else {
        originalStart = edit.start < value.length ? starts[edit.start] : original.length;
        originalEnd = originalStart;
      }
      out += edit.replacement;
      for (let i = 0; i < edit.replacement.length; i++) {
        newStarts.push(originalStart);
        newEnds.push(originalEnd);
      }
      cursor = edit.end;
    }
    copy(cursor, value.length);
    return new MappedText(original, out, newStarts, newEnds);
  }

  /** Replaces every match of a global regex whose replacement differs from the matched text. */
  replaceAll(regex, replacer) {
    const edits = [];
    for (const match of this.value.matchAll(regex)) {
      const replacement = replacer(match);
      if (replacement !== match[0]) {
        edits.push({ start: match.index, end: match.index + match[0].length, replacement });
      }
    }
    return this.apply(edits);
  }
}
