#!/usr/bin/env python3
"""
fb.py — Firebase Auth + Firestore REST helper for the Lock-In two-party test harness.

Why this exists: a two-party flow (friend requests, kudos, group mute-approval) can't be
exercised by a single signed-in emulator. The second party is driven over the Firestore REST
API instead. This script signs in a test account and does surgical Firestore reads/writes so
that harness lives in the repo and survives session resets (it used to be rebuilt each session).

No secrets are committed: the web API key + project id are read at runtime from
`app/google-services.json` (gitignored). Test-account passwords come from `tools/creds.json`
(gitignored), `--password`, or the `FB_PASSWORD` env var.

Usage:
  python tools/fb.py whoami --as mutebreaker@lockin.test
  python tools/fb.py get   users/<uid> --as mutebreaker@lockin.test
  python tools/fb.py patch users/<uid> --as mutebreaker@lockin.test --set sparkles=88 streakMinMinutes=30
  python tools/fb.py delete groups/<gid>/lobbies/<lid> --as feedtester@lockin.test
  python tools/fb.py query users/<uid>/activity --as ... --order-by endedAt --desc --limit 5

Typed --set values: bare ints/floats/true/false/null are inferred; wrap in quotes to force a
string (e.g. --set 'name="42"'). Patch defaults to a SURGICAL updateMask (only the fields you
name), so it can never clobber the rest of the document — a hard-won lesson (a maskless PATCH
once wiped a users/ doc down to a single field).
"""
import argparse
import json
import os
import sys
import urllib.request
import urllib.error

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GOOGLE_SERVICES = os.path.join(REPO_ROOT, "app", "google-services.json")
CREDS_FILE = os.path.join(REPO_ROOT, "tools", "creds.json")


def _fail(msg):
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


def load_project():
    """Return (project_id, api_key) from app/google-services.json."""
    if not os.path.exists(GOOGLE_SERVICES):
        _fail(f"{GOOGLE_SERVICES} not found (gitignored — re-download from the Firebase console).")
    with open(GOOGLE_SERVICES, encoding="utf-8") as f:
        gs = json.load(f)
    try:
        project_id = gs["project_info"]["project_id"]
        api_key = gs["client"][0]["api_key"][0]["current_key"]
    except (KeyError, IndexError) as e:
        _fail(f"unexpected google-services.json shape: {e}")
    return project_id, api_key


def _http(url, payload=None, method=None, token=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method or ("POST" if data else "GET"))
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req) as resp:
            body = resp.read().decode()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode()
        _fail(f"HTTP {e.code} on {method or 'GET'} {url}\n{detail}")


def resolve_password(email, cli_password):
    if cli_password:
        return cli_password
    if os.environ.get("FB_PASSWORD"):
        return os.environ["FB_PASSWORD"]
    if os.path.exists(CREDS_FILE):
        with open(CREDS_FILE, encoding="utf-8") as f:
            creds = json.load(f)
        if email in creds:
            return creds[email]
    _fail(f"no password for {email}: add it to tools/creds.json, pass --password, or set FB_PASSWORD.")


def sign_in(email, password):
    """Return (id_token, local_id) via Firebase Auth REST."""
    _, api_key = load_project()
    url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={api_key}"
    res = _http(url, {"email": email, "password": password, "returnSecureToken": True})
    return res["idToken"], res["localId"]


# ---- Firestore typed-value encode/decode ---------------------------------
def to_value(v):
    if v is None:
        return {"nullValue": None}
    if isinstance(v, bool):
        return {"booleanValue": v}
    if isinstance(v, int):
        return {"integerValue": str(v)}
    if isinstance(v, float):
        return {"doubleValue": v}
    if isinstance(v, list):
        return {"arrayValue": {"values": [to_value(x) for x in v]}}
    if isinstance(v, dict):
        return {"mapValue": {"fields": {k: to_value(x) for k, x in v.items()}}}
    return {"stringValue": str(v)}


def from_value(val):
    if "nullValue" in val:
        return None
    if "booleanValue" in val:
        return val["booleanValue"]
    if "integerValue" in val:
        return int(val["integerValue"])
    if "doubleValue" in val:
        return val["doubleValue"]
    if "stringValue" in val:
        return val["stringValue"]
    if "timestampValue" in val:
        return val["timestampValue"]
    if "arrayValue" in val:
        return [from_value(x) for x in val["arrayValue"].get("values", [])]
    if "mapValue" in val:
        return {k: from_value(x) for k, x in val["mapValue"].get("fields", {}).items()}
    return val


def doc_url(project_id, path):
    return (f"https://firestore.googleapis.com/v1/projects/{project_id}"
            f"/databases/(default)/documents/{path}")


def get_doc(path, token):
    project_id, _ = load_project()
    res = _http(doc_url(project_id, path), token=token)
    fields = res.get("fields", {})
    return {k: from_value(v) for k, v in fields.items()}


def patch_doc(path, fields, token, update_mask=None):
    """Surgical patch: only the named fields, nothing else in the doc is touched."""
    project_id, _ = load_project()
    mask = update_mask if update_mask is not None else list(fields.keys())
    qs = "&".join(f"updateMask.fieldPaths={m}" for m in mask)
    url = doc_url(project_id, path) + ("?" + qs if qs else "")
    payload = {"fields": {k: to_value(v) for k, v in fields.items()}}
    res = _http(url, payload, method="PATCH", token=token)
    return {k: from_value(v) for k, v in res.get("fields", {}).items()}


def delete_doc(path, token):
    project_id, _ = load_project()
    _http(doc_url(project_id, path), method="DELETE", token=token)
    return {"deleted": path}


def run_query(parent_path, token, order_by=None, desc=False, limit=None):
    """List/query a collection under parent_path's last segment (collectionId)."""
    project_id, _ = load_project()
    segments = parent_path.strip("/").split("/")
    collection_id = segments[-1]
    parent_doc = "/".join(segments[:-1])
    base = (f"https://firestore.googleapis.com/v1/projects/{project_id}"
            f"/databases/(default)/documents")
    parent = base + ("/" + parent_doc if parent_doc else "")
    query = {"from": [{"collectionId": collection_id}]}
    if order_by:
        query["orderBy"] = [{"field": {"fieldPath": order_by},
                             "direction": "DESCENDING" if desc else "ASCENDING"}]
    if limit:
        query["limit"] = int(limit)
    res = _http(parent + ":runQuery", {"structuredQuery": query}, token=token)
    out = []
    for row in res:
        doc = row.get("document")
        if not doc:
            continue
        out.append({"_name": doc["name"].split("/documents/")[-1],
                    **{k: from_value(v) for k, v in doc.get("fields", {}).items()}})
    return out


def parse_set(pairs):
    """Parse key=value pairs with light type inference."""
    out = {}
    for p in pairs:
        if "=" not in p:
            _fail(f"--set expects key=value, got: {p}")
        k, raw = p.split("=", 1)
        if raw.startswith('"') and raw.endswith('"'):
            out[k] = raw[1:-1]
            continue
        low = raw.lower()
        if low == "true":
            out[k] = True
        elif low == "false":
            out[k] = False
        elif low == "null":
            out[k] = None
        else:
            try:
                out[k] = int(raw)
            except ValueError:
                try:
                    out[k] = float(raw)
                except ValueError:
                    out[k] = raw
    return out


def main():
    ap = argparse.ArgumentParser(description="Firebase Auth + Firestore REST helper")
    ap.add_argument("cmd", choices=["whoami", "get", "patch", "delete", "query"])
    ap.add_argument("path", nargs="?", help="Firestore doc/collection path")
    ap.add_argument("--as", dest="email", required=True, help="test account email to act as")
    ap.add_argument("--password", help="override password (else creds.json / FB_PASSWORD)")
    ap.add_argument("--set", nargs="+", default=[], help="key=value pairs for patch")
    ap.add_argument("--mask", nargs="+", help="explicit updateMask fields (default: --set keys)")
    ap.add_argument("--order-by", dest="order_by")
    ap.add_argument("--desc", action="store_true")
    ap.add_argument("--limit")
    args = ap.parse_args()

    password = resolve_password(args.email, args.password)
    token, uid = sign_in(args.email, password)

    if args.cmd == "whoami":
        print(json.dumps({"email": args.email, "uid": uid}, indent=2))
        return
    if not args.path:
        _fail(f"{args.cmd} needs a path")
    if args.cmd == "get":
        print(json.dumps(get_doc(args.path, token), indent=2))
    elif args.cmd == "patch":
        fields = parse_set(args.set)
        print(json.dumps(patch_doc(args.path, fields, token, args.mask), indent=2))
    elif args.cmd == "delete":
        print(json.dumps(delete_doc(args.path, token), indent=2))
    elif args.cmd == "query":
        print(json.dumps(run_query(args.path, token, args.order_by, args.desc, args.limit), indent=2))


if __name__ == "__main__":
    main()
