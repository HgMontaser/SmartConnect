// Pretty-printed JSON with quiet syntax colours; anything that isn't JSON is shown as is.
const TOKEN = /("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g;

function highlight(text) {
  const parts = [];
  let last = 0;
  let match;
  TOKEN.lastIndex = 0;
  while ((match = TOKEN.exec(text)) !== null) {
    if (match.index > last) parts.push(text.slice(last, match.index));
    const [whole, str, colon, literal, number] = match;
    if (str && colon) {
      parts.push(<span key={match.index} className="json-key">{str}</span>, colon);
    } else if (str) {
      parts.push(<span key={match.index} className="json-string">{str}</span>);
    } else if (literal) {
      parts.push(<span key={match.index} className="json-literal">{literal}</span>);
    } else if (number) {
      parts.push(<span key={match.index} className="json-number">{number}</span>);
    } else {
      parts.push(whole);
    }
    last = match.index + whole.length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts;
}

export function prettyJson(value) {
  if (value == null) return { text: '', json: false };
  try {
    return { text: JSON.stringify(JSON.parse(value), null, 2), json: true };
  } catch {
    return { text: value, json: false };
  }
}

export default function JsonView({ value }) {
  const { text, json } = prettyJson(value);
  return <pre className="json-view">{json ? highlight(text) : text}</pre>;
}
